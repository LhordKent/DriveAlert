package com.lhordkent.drivealert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordSignEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordWithSigns
import com.lhordkent.drivealert.data.local.entity.StageSyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StageSyncRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertEntity(record: StageSyncRecordEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertSigns(signs: List<StageSyncRecordSignEntity>)

    @Transaction
    open suspend fun insert(record: StageSyncRecordEntity, signs: Set<StageSyncRecordSignEntity>) {
        require(signs.isNotEmpty()) { "A Stage 3 synchronization record must include at least one selected sign." }
        require(signs.all { it.stageSyncRecordId == record.stageSyncRecordId }) {
            "Every sign must belong to the inserted Stage 3 synchronization record."
        }
        insertEntity(record)
        insertSigns(signs.toList())
    }

    @Transaction
    @Query("SELECT * FROM stage_sync_records WHERE stageSyncRecordId = :recordId AND driverUserId = :driverUserId")
    abstract suspend fun getById(driverUserId: String, recordId: String): StageSyncRecordWithSigns?

    @Transaction
    @Query("SELECT * FROM stage_sync_records WHERE driverUserId = :driverUserId ORDER BY periodStartedAtEpochMillis DESC")
    abstract fun observeForDriver(driverUserId: String): Flow<List<StageSyncRecordWithSigns>>

    @Transaction
    @Query("SELECT * FROM stage_sync_records WHERE driverUserId = :driverUserId AND syncStatus IN ('PENDING', 'FAILED') ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    abstract suspend fun getRecordsReadyForSync(driverUserId: String, limit: Int): List<StageSyncRecordWithSigns>

    @Transaction
    @Query("SELECT * FROM stage_sync_records WHERE driverUserId = :driverUserId AND eligibility = 'PENDING_EVALUATION' ORDER BY createdAtEpochMillis ASC LIMIT :limit")
    abstract suspend fun getRecordsAwaitingEvaluation(driverUserId: String, limit: Int): List<StageSyncRecordWithSigns>

    @Query(
        """
        UPDATE stage_sync_records
        SET eligibility = :eligibility, syncStatus = :syncStatus
        WHERE stageSyncRecordId = :recordId AND driverUserId = :driverUserId
        """,
    )
    abstract suspend fun updateEligibility(
        driverUserId: String,
        recordId: String,
        eligibility: com.lhordkent.drivealert.data.local.entity.StageSyncEligibility,
        syncStatus: StageSyncStatus,
    ): Int

    @Query(
        """
        UPDATE stage_sync_records
        SET syncStatus = :status,
            attemptCount = :attemptCount,
            lastAttemptAtEpochMillis = :lastAttemptAtEpochMillis,
            syncedAtEpochMillis = :syncedAtEpochMillis,
            syncErrorCode = :errorCode,
            syncErrorMessage = :errorMessage
        WHERE stageSyncRecordId = :recordId AND driverUserId = :driverUserId
        """,
    )
    abstract suspend fun updateSyncState(
        driverUserId: String,
        recordId: String,
        status: StageSyncStatus,
        attemptCount: Int,
        lastAttemptAtEpochMillis: Long?,
        syncedAtEpochMillis: Long?,
        errorCode: String?,
        errorMessage: String?,
    ): Int

    @Query("UPDATE stage_sync_records SET syncStatus = 'FAILED', syncErrorCode = 'INTERRUPTED', syncErrorMessage = 'Synchronization was interrupted and will be retried.' WHERE driverUserId = :driverUserId AND syncStatus = 'SYNCING'")
    abstract suspend fun recoverInterruptedRecords(driverUserId: String): Int
}
