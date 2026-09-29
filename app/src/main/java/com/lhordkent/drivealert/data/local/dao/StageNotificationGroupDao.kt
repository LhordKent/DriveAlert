package com.lhordkent.drivealert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lhordkent.drivealert.data.local.entity.NotificationDispatchStatus
import com.lhordkent.drivealert.data.local.entity.StageNotificationGroupEntity
import com.lhordkent.drivealert.data.local.entity.StageNotificationGroupRecordEntity

data class PendingNotificationGroup(
    val dispatchGroupId: String,
    val driverUserId: String,
    val attemptCount: Int,
    val recordIds: List<String>,
)

@Dao
abstract class StageNotificationGroupDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertGroup(entity: StageNotificationGroupEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertRecord(entity: StageNotificationGroupRecordEntity): Long

    @Query("SELECT * FROM stage_notification_groups WHERE driverUserId = :driverUserId AND status = 'OPEN' ORDER BY createdAtEpochMillis ASC LIMIT 1")
    abstract suspend fun openGroup(driverUserId: String): StageNotificationGroupEntity?

    @Query("SELECT * FROM stage_notification_groups WHERE driverUserId = :driverUserId AND status = 'PENDING' ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    protected abstract suspend fun pendingEntities(driverUserId: String, limit: Int): List<StageNotificationGroupEntity>

    @Query("SELECT stageSyncRecordId FROM stage_notification_group_records WHERE dispatchGroupId = :groupId ORDER BY ordinal ASC")
    abstract suspend fun recordIds(groupId: String): List<String>

    @Query("SELECT COUNT(*) FROM stage_notification_group_records WHERE dispatchGroupId = :groupId")
    abstract suspend fun recordCount(groupId: String): Int

    @Transaction
    open suspend fun createOpenGroup(entity: StageNotificationGroupEntity) {
        check(entity.status == NotificationDispatchStatus.OPEN)
        insertGroup(entity)
    }

    @Transaction
    open suspend fun addRecord(groupId: String, recordId: String) {
        insertRecord(StageNotificationGroupRecordEntity(groupId, recordId, recordCount(groupId)))
    }

    @Query("""UPDATE stage_notification_groups SET
        status = 'PENDING',
        expectedRecordCount = (SELECT COUNT(*) FROM stage_notification_group_records WHERE dispatchGroupId = :groupId),
        expectedChunkCount = ((SELECT COUNT(*) FROM stage_notification_group_records WHERE dispatchGroupId = :groupId) + 19) / 20
        WHERE dispatchGroupId = :groupId AND status = 'OPEN'""")
    abstract suspend fun closeGroup(groupId: String): Int

    @Query("UPDATE stage_notification_groups SET attemptCount = :attemptCount, lastAttemptAtEpochMillis = :attemptedAt, lastErrorCode = :errorCode WHERE dispatchGroupId = :groupId")
    abstract suspend fun recordAttempt(groupId: String, attemptCount: Int, attemptedAt: Long, errorCode: String?): Int

    @Query("UPDATE stage_notification_groups SET status = :status, completedAtEpochMillis = :completedAt, lastErrorCode = :errorCode WHERE dispatchGroupId = :groupId")
    abstract suspend fun complete(groupId: String, status: NotificationDispatchStatus, completedAt: Long, errorCode: String?): Int

    @Transaction
    open suspend fun pendingGroups(driverUserId: String, limit: Int): List<PendingNotificationGroup> =
        pendingEntities(driverUserId, limit).map { entity ->
            PendingNotificationGroup(entity.dispatchGroupId, entity.driverUserId, entity.attemptCount, recordIds(entity.dispatchGroupId))
        }
}
