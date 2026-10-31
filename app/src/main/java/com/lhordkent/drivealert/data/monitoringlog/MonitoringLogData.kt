package com.lhordkent.drivealert.data.monitoringlog

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "monitoring_logs", indices = [Index("driverUserId")])
data class MonitoringLog(
    @PrimaryKey val sessionId: String,
    val driverUserId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String = "ACTIVE",
    val recordingGap: Boolean = false,
)

@Entity(tableName = "monitoring_log_events", foreignKeys = [ForeignKey(
    entity = MonitoringLog::class, parentColumns = ["sessionId"], childColumns = ["sessionId"],
    onDelete = ForeignKey.CASCADE,
)], indices = [Index("sessionId"), Index(value = ["eventId"], unique = true)])
data class MonitoringLogEvent(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val eventId: String,
    val sessionId: String,
    val driverUserId: String,
    val occurredAt: Long,
    val kind: String,
    val sign: String? = null,
    val measuredValue: Double? = null,
    val threshold: Double? = null,
    val durationMs: Long? = null,
    val requiredDurationMs: Long? = null,
    val downwardRatio: Double? = null,
    val ratioThreshold: Double? = null,
    val calibrationId: String? = null,
    val calibrationVersion: Int? = null,
    val stage: Int? = null,
    val repeated: Boolean = false,
    val result: String? = null,
    val responseAt: Long? = null,
    val processId: String? = null,
)

@Entity(tableName = "monitoring_log_preferences")
data class MonitoringLogPreference(@PrimaryKey val driverUserId: String, val retentionDays: Int? = null)

@Dao
interface MonitoringLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(log: MonitoringLog): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(event: MonitoringLogEvent): Long
    @Upsert suspend fun save(preference: MonitoringLogPreference)
    @Query("SELECT * FROM monitoring_log_preferences WHERE driverUserId = :uid")
    fun preference(uid: String): Flow<MonitoringLogPreference?>
    @Query("SELECT * FROM monitoring_log_preferences") suspend fun preferences(): List<MonitoringLogPreference>
    @Query("SELECT * FROM monitoring_logs WHERE driverUserId = :uid ORDER BY (endedAt IS NULL) DESC, startedAt DESC, sessionId DESC LIMIT :limit")
    fun sessions(uid: String, limit: Int): Flow<List<MonitoringLog>>
    @Query("SELECT * FROM monitoring_logs WHERE driverUserId = :uid AND sessionId = :id")
    fun observe(uid: String, id: String): Flow<MonitoringLog?>
    @Query("SELECT * FROM monitoring_logs WHERE driverUserId = :uid AND sessionId = :id")
    suspend fun get(uid: String, id: String): MonitoringLog?
    @Query("SELECT * FROM monitoring_logs WHERE endedAt IS NULL")
    suspend fun openLogs(): List<MonitoringLog>
    @Query("SELECT * FROM monitoring_log_events WHERE driverUserId = :uid AND sessionId = :id ORDER BY sequence DESC LIMIT :limit")
    fun events(uid: String, id: String, limit: Int): Flow<List<MonitoringLogEvent>>
    @Query("UPDATE monitoring_logs SET recordingGap = 1 WHERE driverUserId = :uid AND sessionId = :id")
    suspend fun gap(uid: String, id: String)
    @Query("UPDATE monitoring_logs SET endedAt = :time, status = :status WHERE driverUserId = :uid AND sessionId = :id AND endedAt IS NULL")
    suspend fun end(uid: String, id: String, time: Long, status: String): Int
    @Query("UPDATE monitoring_log_events SET result = :result, responseAt = :time WHERE driverUserId = :uid AND eventId = :requestId AND kind = 'ALARM' AND result = 'PENDING'")
    suspend fun result(uid: String, requestId: String, result: String, time: Long)
    @Query("UPDATE monitoring_log_events SET result = 'UNKNOWN_INTERRUPTED' WHERE kind = 'ALARM' AND result = 'PENDING' AND processId != :processId")
    suspend fun recoverRequests(processId: String)
    @Query("SELECT COUNT(*) FROM monitoring_logs WHERE driverUserId = :uid AND endedAt IS NOT NULL AND (:cutoff IS NULL OR endedAt < :cutoff)")
    suspend fun deletionCount(uid: String, cutoff: Long?): Int
    @Query("DELETE FROM monitoring_logs WHERE driverUserId = :uid AND endedAt IS NOT NULL AND (:cutoff IS NULL OR endedAt < :cutoff)")
    suspend fun deleteCompleted(uid: String, cutoff: Long?): Int
    @Query("DELETE FROM monitoring_logs WHERE driverUserId = :uid AND sessionId = :id AND endedAt IS NOT NULL")
    suspend fun delete(uid: String, id: String): Int
}
