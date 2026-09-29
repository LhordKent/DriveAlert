package com.lhordkent.drivealert.data.sync

import com.lhordkent.drivealert.data.local.dao.StageSyncRecordDao
import com.lhordkent.drivealert.data.local.dao.StageNotificationGroupDao
import com.lhordkent.drivealert.data.local.entity.StageSyncEligibility
import com.lhordkent.drivealert.data.local.entity.NotificationDispatchStatus
import com.lhordkent.drivealert.data.local.entity.StageNotificationGroupEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncStatus
import java.util.UUID

class StageSyncProcessor(
    private val dao: StageSyncRecordDao,
    private val remote: StageSyncRemoteDataSource,
    private val notificationGroups: StageNotificationGroupDao? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val monotonicClock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val groupIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun synchronize(
        driverUserId: String,
        batchSize: Int = 20,
        timeBudgetMs: Long = DEFAULT_TIME_BUDGET_MS,
    ): Boolean {
        require(batchSize in 1..20)
        dao.recoverInterruptedRecords(driverUserId)
        val startedAt = monotonicClock()
        var openGroup = notificationGroups?.openGroup(driverUserId)
        var retryRequired = false
        while (monotonicClock() - startedAt < timeBudgetMs) {
            val awaitingEvaluation = dao.getRecordsAwaitingEvaluation(driverUserId, batchSize)
            for (record in awaitingEvaluation) {
                val eligible = try {
                    remote.hasEligibleApprovedContact(driverUserId, record.record.periodStartedAtEpochMillis)
                } catch (_: Exception) {
                    retryRequired = true
                    break
                }
                dao.updateEligibility(
                    driverUserId = driverUserId,
                    recordId = record.record.stageSyncRecordId,
                    eligibility = if (eligible) StageSyncEligibility.ELIGIBLE else StageSyncEligibility.NO_APPROVED_CONTACT,
                    syncStatus = if (eligible) StageSyncStatus.PENDING else StageSyncStatus.NOT_QUEUED,
                )
            }
            if (retryRequired) break

            val ready = dao.getRecordsReadyForSync(driverUserId, batchSize)
            if (ready.isEmpty()) {
                if (awaitingEvaluation.isEmpty()) break
                continue
            }
            for (record in ready) {
                val stillEligible = try {
                    remote.hasEligibleApprovedContact(driverUserId, record.record.periodStartedAtEpochMillis)
                } catch (_: Exception) {
                    retryRequired = true
                    break
                }
                if (!stillEligible) {
                    dao.updateEligibility(
                        driverUserId,
                        record.record.stageSyncRecordId,
                        StageSyncEligibility.NO_APPROVED_CONTACT,
                        StageSyncStatus.NOT_QUEUED,
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
                    if (notificationGroups != null) {
                        if (openGroup == null) {
                            openGroup = StageNotificationGroupEntity(
                                dispatchGroupId = groupIdFactory(),
                                driverUserId = driverUserId,
                                status = NotificationDispatchStatus.OPEN,
                                expectedRecordCount = 0,
                                expectedChunkCount = 0,
                                attemptCount = 0,
                                lastAttemptAtEpochMillis = null,
                                lastErrorCode = null,
                                createdAtEpochMillis = clock(),
                                completedAtEpochMillis = null,
                            ).also { notificationGroups.createOpenGroup(it) }
                        }
                        notificationGroups.addRecord(checkNotNull(openGroup).dispatchGroupId, record.record.stageSyncRecordId)
                    }
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
                    retryRequired = true
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
                    break
                }
            }
            if (retryRequired) break
        }
        val remaining = dao.awaitingEvaluationCount(driverUserId) + dao.readyCount(driverUserId)
        val finished = !retryRequired && remaining == 0
        if (finished && openGroup != null) notificationGroups?.let { groups ->
            if (groups.recordCount(openGroup.dispatchGroupId) > 0) groups.closeGroup(openGroup.dispatchGroupId)
        }
        return finished
    }

    companion object {
        const val DEFAULT_TIME_BUDGET_MS = 8 * 60 * 1_000L
    }
}
