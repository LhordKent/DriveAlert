package com.lhordkent.drivealert.data.history

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "shared_history_expiry")
data class SharedHistoryExpiry(@PrimaryKey val viewerUserId: String, val expiredBeforeEpochMillis: Long)

@Entity(tableName = "shared_history_dismissals", primaryKeys = ["viewerUserId", "driverUserId", "recordId"])
data class SharedHistoryDismissal(val viewerUserId: String, val driverUserId: String, val recordId: String)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM shared_history_expiry WHERE viewerUserId = :userId")
    fun observeExpiry(userId: String): Flow<SharedHistoryExpiry?>
    @Query("SELECT * FROM shared_history_dismissals WHERE viewerUserId = :userId")
    fun observeDismissals(userId: String): Flow<List<SharedHistoryDismissal>>
    @Query("SELECT * FROM shared_history_expiry WHERE viewerUserId = :userId")
    suspend fun expiry(userId: String): SharedHistoryExpiry?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setExpiry(expiry: SharedHistoryExpiry)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun dismiss(records: List<SharedHistoryDismissal>)
    @Query("SELECT COUNT(*) FROM alerts WHERE driverUserId = :userId AND detectedAtEpochMillis < :before AND sessionId IN (SELECT sessionId FROM monitoring_sessions WHERE driverUserId = :userId AND status IN ('COMPLETED', 'INTERRUPTED'))")
    suspend fun countExpiredAlerts(userId: String, before: Long): Int
    @Query("DELETE FROM alerts WHERE driverUserId = :userId AND detectedAtEpochMillis < :before AND sessionId IN (SELECT sessionId FROM monitoring_sessions WHERE driverUserId = :userId AND status IN ('COMPLETED', 'INTERRUPTED'))")
    suspend fun deleteExpiredAlerts(userId: String, before: Long): Int
    @Query("DELETE FROM alerts WHERE driverUserId = :userId AND alertId = :alertId AND sessionId IN (SELECT sessionId FROM monitoring_sessions WHERE driverUserId = :userId AND status IN ('COMPLETED', 'INTERRUPTED'))")
    suspend fun deleteAlert(userId: String, alertId: String): Int
}
