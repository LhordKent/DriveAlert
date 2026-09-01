package com.lhordkent.drivealert.data.sync

import com.lhordkent.drivealert.data.local.dao.StageSyncRecordDao
import com.lhordkent.drivealert.data.local.entity.StageSyncEligibility
import com.lhordkent.drivealert.data.local.entity.StageSyncStatus

class StageSyncProcessor(
    private val dao: StageSyncRecordDao,
    private val remote: StageSyncRemoteDataSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun synchronize(driverUserId: String, batchSize: Int = 20): Boolean {
        dao.recoverInterruptedRecords(driverUserId)
        val awaitingEvaluation = dao.getRecordsAwaitingEvaluation(driverUserId, batchSize)
        for (record in awaitingEvaluation) {
            val eligible = remote.hasEligibleApprovedContact(
                driverUserId = driverUserId,
                periodStartedAtEpochMillis = record.record.periodStartedAtEpochMillis,
            )
            dao.updateEligibility(
                driverUserId = driverUserId,
                recordId = record.record.stageSyncRecordId,
                eligibility = if (eligible) StageSyncEligibility.ELIGIBLE else StageSyncEligibility.NO_APPROVED_CONTACT,
                syncStatus = if (eligible) StageSyncStatus.PENDING else StageSyncStatus.NOT_QUEUED,
            )
        }

        var allSucceeded = true
        for (record in dao.getRecordsReadyForSync(driverUserId, batchSize)) {
            val stillEligible = remote.hasEligibleApprovedContact(
                driverUserId = driverUserId,
                periodStartedAtEpochMillis = record.record.periodStartedAtEpochMillis,
            )
            if (!stillEligible) {
                dao.updateEligibility(
                    driverUserId = driverUserId,
                    recordId = record.record.stageSyncRecordId,
                    eligibility = StageSyncEligibility.NO_APPROVED_CONTACT,
                    syncStatus = StageSyncStatus.NOT_QUEUED,
                )
                continue
            }
            val attempt = record.record.attemptCount + 1
            val attemptedAt = clock()
            dao.updateSyncState(
                driverUserId,
                record.record.stageSyncRecordId,
                StageSyncStatus.SYNCING,
                attempt,
                attemptedAt,
                null,
                null,
                null,
            )
            try {
                remote.upload(record)
                dao.updateSyncState(
                    driverUserId,
                    record.record.stageSyncRecordId,
                    StageSyncStatus.SYNCED,
                    attempt,
                    attemptedAt,
                    clock(),
                    null,
                    null,
                )
            } catch (error: Exception) {
                allSucceeded = false
                dao.updateSyncState(
                    driverUserId,
                    record.record.stageSyncRecordId,
                    StageSyncStatus.FAILED,
                    attempt,
                    attemptedAt,
                    null,
                    error.javaClass.simpleName.take(80),
                    "Synchronization could not be completed and will be retried.",
                )
            }
        }
        return allSucceeded
    }
}
