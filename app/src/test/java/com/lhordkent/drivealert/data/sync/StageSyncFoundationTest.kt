package com.lhordkent.drivealert.data.sync

import com.lhordkent.drivealert.data.local.dao.StageSyncRecordDao
import com.lhordkent.drivealert.data.local.entity.StageSyncEligibility
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordSignEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordType
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordWithSigns
import com.lhordkent.drivealert.data.local.entity.StageSyncStatus
import com.lhordkent.drivealert.data.local.entity.StoredVisibleSign
import com.lhordkent.drivealert.postauth.VisibleSign
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageSyncFoundationTest {
    @Test
    fun explicitStage3RecordStartsPendingEvaluationAndSchedulesConnectedWork() = runTest {
        val dao = FakeStageSyncRecordDao()
        val scheduler = FakeScheduler()
        val repository = RoomStageSyncRepository(
            dao = dao,
            scheduler = scheduler,
            clock = { 5_000L },
            idFactory = { "sync-1" },
        )

        repository.createSynchronizationRecord(
            StageSyncRecordInput(
                sessionId = "session-1",
                driverUserId = "driver-a",
                recordType = StageSyncRecordType.STAGE3_TRANSITION,
                periodStartedAtEpochMillis = 4_000L,
                periodEndedAtEpochMillis = null,
                eventCount = 1,
                signs = setOf(VisibleSign.YAWNING),
            ),
        )

        val stored = dao.getById("driver-a", "sync-1")?.record
        assertEquals(StageSyncEligibility.PENDING_EVALUATION, stored?.eligibility)
        assertEquals(StageSyncStatus.NOT_QUEUED, stored?.syncStatus)
        assertEquals(listOf("driver-a"), scheduler.scheduled)
    }

    @Test
    fun eligibleRecordIsMarkedSyncedOnlyAfterAcknowledgedUpload() = runTest {
        val dao = FakeStageSyncRecordDao(record())
        val remote = FakeRemote(eligible = true)
        val processor = StageSyncProcessor(dao, remote, clock = sequenceOf(10_000L, 10_100L).iterator()::next)

        assertTrue(processor.synchronize("driver-a"))

        assertEquals(listOf("sync-1"), remote.uploaded)
        val stored = dao.getById("driver-a", "sync-1")?.record
        assertEquals(StageSyncStatus.SYNCED, stored?.syncStatus)
        assertEquals(10_100L, stored?.syncedAtEpochMillis)
        assertNull(stored?.syncErrorMessage)
    }

    @Test
    fun failedUploadStaysInRoomWithSafeRetryMessage() = runTest {
        val dao = FakeStageSyncRecordDao(record())
        val processor = StageSyncProcessor(dao, FakeRemote(eligible = true, failUpload = true), clock = { 10_000L })

        assertFalse(processor.synchronize("driver-a"))

        val stored = dao.getById("driver-a", "sync-1")?.record
        assertEquals(StageSyncStatus.FAILED, stored?.syncStatus)
        assertEquals(1, stored?.attemptCount)
        assertEquals("Synchronization could not be completed and will be retried.", stored?.syncErrorMessage)
        assertNull(stored?.syncedAtEpochMillis)
    }

    @Test
    fun noApprovedContactRetainsLocalRecordWithoutUploading() = runTest {
        val dao = FakeStageSyncRecordDao(record())
        val remote = FakeRemote(eligible = false)

        assertTrue(StageSyncProcessor(dao, remote).synchronize("driver-a"))

        val stored = dao.getById("driver-a", "sync-1")?.record
        assertEquals(StageSyncEligibility.NO_APPROVED_CONTACT, stored?.eligibility)
        assertEquals(StageSyncStatus.NOT_QUEUED, stored?.syncStatus)
        assertTrue(remote.uploaded.isEmpty())
    }

    @Test
    fun previouslyEligibleRecordIsRevalidatedBeforeUpload() = runTest {
        val dao = FakeStageSyncRecordDao(
            record().let { value ->
                value.copy(record = value.record.copy(
                    eligibility = StageSyncEligibility.ELIGIBLE,
                    syncStatus = StageSyncStatus.PENDING,
                ))
            },
        )
        val remote = FakeRemote(eligible = false)

        assertTrue(StageSyncProcessor(dao, remote).synchronize("driver-a"))

        val stored = dao.getById("driver-a", "sync-1")?.record
        assertEquals(StageSyncEligibility.NO_APPROVED_CONTACT, stored?.eligibility)
        assertEquals(StageSyncStatus.NOT_QUEUED, stored?.syncStatus)
        assertTrue(remote.uploaded.isEmpty())
    }
}

private class FakeScheduler : StageSyncScheduler {
    val scheduled = mutableListOf<String>()
    override fun schedule(driverUserId: String) { scheduled += driverUserId }
    override fun cancel(driverUserId: String) = Unit
}

private class FakeRemote(
    private val eligible: Boolean,
    private val failUpload: Boolean = false,
) : StageSyncRemoteDataSource {
    val uploaded = mutableListOf<String>()
    override suspend fun hasEligibleApprovedContact(driverUserId: String, periodStartedAtEpochMillis: Long) = eligible
    override suspend fun upload(record: StageSyncRecordWithSigns) {
        if (failUpload) throw IOException("raw network detail")
        uploaded += record.record.stageSyncRecordId
    }
}

private class FakeStageSyncRecordDao(initial: StageSyncRecordWithSigns? = null) : StageSyncRecordDao() {
    private val records = linkedMapOf<String, StageSyncRecordEntity>()
    private val signs = linkedMapOf<String, MutableList<StageSyncRecordSignEntity>>()
    private val state = MutableStateFlow<List<StageSyncRecordWithSigns>>(emptyList())

    init {
        initial?.let {
            records[it.record.stageSyncRecordId] = it.record
            signs[it.record.stageSyncRecordId] = it.signs.toMutableList()
            publish()
        }
    }

    override suspend fun insertEntity(record: StageSyncRecordEntity) {
        records[record.stageSyncRecordId] = record
        publish()
    }

    override suspend fun insertSigns(signs: List<StageSyncRecordSignEntity>) {
        signs.forEach { sign -> this.signs.getOrPut(sign.stageSyncRecordId) { mutableListOf() }.add(sign) }
        publish()
    }

    override suspend fun getById(driverUserId: String, recordId: String): StageSyncRecordWithSigns? =
        records[recordId]?.takeIf { it.driverUserId == driverUserId }?.let(::withSigns)

    override fun observeForDriver(driverUserId: String): Flow<List<StageSyncRecordWithSigns>> = state

    override suspend fun getRecordsReadyForSync(driverUserId: String, limit: Int) = records.values
        .filter { it.driverUserId == driverUserId && it.syncStatus in setOf(StageSyncStatus.PENDING, StageSyncStatus.FAILED) }
        .take(limit)
        .map(::withSigns)

    override suspend fun getRecordsAwaitingEvaluation(driverUserId: String, limit: Int) = records.values
        .filter { it.driverUserId == driverUserId && it.eligibility == StageSyncEligibility.PENDING_EVALUATION }
        .take(limit)
        .map(::withSigns)

    override suspend fun updateEligibility(
        driverUserId: String,
        recordId: String,
        eligibility: StageSyncEligibility,
        syncStatus: StageSyncStatus,
    ): Int = update(driverUserId, recordId) { it.copy(eligibility = eligibility, syncStatus = syncStatus) }

    override suspend fun updateSyncState(
        driverUserId: String,
        recordId: String,
        status: StageSyncStatus,
        attemptCount: Int,
        lastAttemptAtEpochMillis: Long?,
        syncedAtEpochMillis: Long?,
        errorCode: String?,
        errorMessage: String?,
    ): Int = update(driverUserId, recordId) {
        it.copy(
            syncStatus = status,
            attemptCount = attemptCount,
            lastAttemptAtEpochMillis = lastAttemptAtEpochMillis,
            syncedAtEpochMillis = syncedAtEpochMillis,
            syncErrorCode = errorCode,
            syncErrorMessage = errorMessage,
        )
    }

    override suspend fun recoverInterruptedRecords(driverUserId: String): Int {
        var count = 0
        records.replaceAll { _, value ->
            if (value.driverUserId == driverUserId && value.syncStatus == StageSyncStatus.SYNCING) {
                count++
                value.copy(syncStatus = StageSyncStatus.FAILED)
            } else value
        }
        publish()
        return count
    }

    private fun update(driverUserId: String, recordId: String, transform: (StageSyncRecordEntity) -> StageSyncRecordEntity): Int {
        val current = records[recordId]?.takeIf { it.driverUserId == driverUserId } ?: return 0
        records[recordId] = transform(current)
        publish()
        return 1
    }

    private fun withSigns(record: StageSyncRecordEntity) = StageSyncRecordWithSigns(record, signs[record.stageSyncRecordId].orEmpty())
    private fun publish() { state.value = records.values.map(::withSigns) }
}

private fun record() = StageSyncRecordWithSigns(
    record = StageSyncRecordEntity(
        stageSyncRecordId = "sync-1",
        sessionId = "session-1",
        driverUserId = "driver-a",
        recordType = StageSyncRecordType.STAGE3_TRANSITION,
        periodStartedAtEpochMillis = 4_000L,
        periodEndedAtEpochMillis = null,
        eventCount = 1,
        eligibility = StageSyncEligibility.PENDING_EVALUATION,
        syncStatus = StageSyncStatus.NOT_QUEUED,
        attemptCount = 0,
        lastAttemptAtEpochMillis = null,
        syncedAtEpochMillis = null,
        syncErrorCode = null,
        syncErrorMessage = null,
        firestoreDocumentId = "sync-1",
        createdAtEpochMillis = 5_000L,
    ),
    signs = listOf(StageSyncRecordSignEntity("sync-1", StoredVisibleSign.YAWNING)),
)
