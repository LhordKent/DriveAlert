package com.lhordkent.drivealert.data.sync

import com.lhordkent.drivealert.data.local.dao.StageSyncRecordDao
import com.lhordkent.drivealert.data.local.entity.StageSyncEligibility
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordSignEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordType
import com.lhordkent.drivealert.data.local.entity.StageSyncStatus
import com.lhordkent.drivealert.data.local.entity.StoredVisibleSign
import com.lhordkent.drivealert.postauth.SharingState
import com.lhordkent.drivealert.postauth.Stage3SyncRecord
import com.lhordkent.drivealert.postauth.SyncRecordKind
import com.lhordkent.drivealert.postauth.VisibleSign
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class StageSyncRecordInput(
    val sessionId: String,
    val driverUserId: String,
    val recordType: StageSyncRecordType,
    val periodStartedAtEpochMillis: Long,
    val periodEndedAtEpochMillis: Long?,
    val eventCount: Int,
    val signs: Set<VisibleSign>,
)

interface StageSyncScheduler {
    fun schedule(driverUserId: String)
    fun cancel(driverUserId: String)
}

interface StageSyncRepository {
    fun observe(driverUserId: String): Flow<List<Stage3SyncRecord>>
    suspend fun createSynchronizationRecord(input: StageSyncRecordInput): String
    fun retryPending(driverUserId: String)
    fun cancelPendingWork(driverUserId: String)
}

class RoomStageSyncRepository(
    private val dao: StageSyncRecordDao,
    private val scheduler: StageSyncScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : StageSyncRepository {
    override fun observe(driverUserId: String): Flow<List<Stage3SyncRecord>> =
        dao.observeForDriver(driverUserId).map { records ->
            records.map { value ->
                Stage3SyncRecord(
                    id = value.record.stageSyncRecordId,
                    occurredAt = Instant.ofEpochMilli(value.record.periodStartedAtEpochMillis)
                        .atZone(zoneId)
                        .toLocalDateTime(),
                    kind = when (value.record.recordType) {
                        StageSyncRecordType.STAGE3_TRANSITION -> SyncRecordKind.STAGE_3_TRANSITION
                        StageSyncRecordType.STAGE3_PERSISTENCE -> SyncRecordKind.STAGE_3_PERSISTENCE
                    },
                    signs = value.signs.mapTo(linkedSetOf()) { it.signType.toDomain() },
                    sharingState = when {
                        value.record.eligibility == StageSyncEligibility.NO_APPROVED_CONTACT -> SharingState.NO_CONTACT
                        value.record.syncStatus == StageSyncStatus.SYNCED -> SharingState.SHARED
                        value.record.syncStatus == StageSyncStatus.FAILED -> SharingState.FAILED
                        else -> SharingState.PENDING
                    },
                    sessionId = value.record.sessionId,
                )
            }
        }

    override suspend fun createSynchronizationRecord(input: StageSyncRecordInput): String {
        require(input.driverUserId.isNotBlank())
        require(input.signs.isNotEmpty())
        val id = idFactory()
        dao.insert(
            StageSyncRecordEntity(
                stageSyncRecordId = id,
                sessionId = input.sessionId,
                driverUserId = input.driverUserId,
                recordType = input.recordType,
                periodStartedAtEpochMillis = input.periodStartedAtEpochMillis,
                periodEndedAtEpochMillis = input.periodEndedAtEpochMillis,
                eventCount = input.eventCount,
                eligibility = StageSyncEligibility.PENDING_EVALUATION,
                syncStatus = StageSyncStatus.NOT_QUEUED,
                attemptCount = 0,
                lastAttemptAtEpochMillis = null,
                syncedAtEpochMillis = null,
                syncErrorCode = null,
                syncErrorMessage = null,
                firestoreDocumentId = id,
                createdAtEpochMillis = clock(),
            ),
            input.signs.mapTo(linkedSetOf()) { StageSyncRecordSignEntity(id, it.toStored()) },
        )
        scheduler.schedule(input.driverUserId)
        return id
    }

    override fun retryPending(driverUserId: String) = scheduler.schedule(driverUserId)
    override fun cancelPendingWork(driverUserId: String) = scheduler.cancel(driverUserId)
}

private fun VisibleSign.toStored() = when (this) {
    VisibleSign.PROLONGED_EYE_CLOSURE -> StoredVisibleSign.PROLONGED_EYE_CLOSURE
    VisibleSign.YAWNING -> StoredVisibleSign.YAWNING
    VisibleSign.HEAD_NODDING -> StoredVisibleSign.HEAD_NODDING
}

private fun StoredVisibleSign.toDomain() = when (this) {
    StoredVisibleSign.PROLONGED_EYE_CLOSURE -> VisibleSign.PROLONGED_EYE_CLOSURE
    StoredVisibleSign.YAWNING -> VisibleSign.YAWNING
    StoredVisibleSign.HEAD_NODDING -> VisibleSign.HEAD_NODDING
}
