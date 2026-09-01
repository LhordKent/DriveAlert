package com.lhordkent.drivealert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonitoringSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: MonitoringSessionEntity)

    @Update
    suspend fun update(session: MonitoringSessionEntity)

    @Query("SELECT * FROM monitoring_sessions WHERE sessionId = :sessionId AND driverUserId = :driverUserId")
    suspend fun getById(driverUserId: String, sessionId: String): MonitoringSessionEntity?

    @Query("SELECT * FROM monitoring_sessions WHERE driverUserId = :driverUserId ORDER BY startedAtEpochMillis DESC")
    fun observeForDriver(driverUserId: String): Flow<List<MonitoringSessionEntity>>

    @Query("SELECT * FROM monitoring_sessions WHERE driverUserId = :driverUserId ORDER BY startedAtEpochMillis DESC LIMIT 1")
    fun observeLatestForDriver(driverUserId: String): Flow<MonitoringSessionEntity?>
}
