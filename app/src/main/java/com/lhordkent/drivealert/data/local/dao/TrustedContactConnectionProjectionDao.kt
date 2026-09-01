package com.lhordkent.drivealert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lhordkent.drivealert.data.local.entity.ConnectionStatus
import com.lhordkent.drivealert.data.local.entity.TrustedContactConnectionProjectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedContactConnectionProjectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connection: TrustedContactConnectionProjectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(connections: List<TrustedContactConnectionProjectionEntity>)

    @Query("SELECT * FROM trusted_contact_connection_projection WHERE driverUserId = :driverUserId ORDER BY requestedAtEpochMillis DESC")
    fun observeForDriver(driverUserId: String): Flow<List<TrustedContactConnectionProjectionEntity>>

    @Query(
        """
        SELECT * FROM trusted_contact_connection_projection
        WHERE driverUserId = :driverUserId
          AND status = :status
          AND approvedAtEpochMillis IS NOT NULL
          AND approvedAtEpochMillis <= :periodStartedAtEpochMillis
        """,
    )
    suspend fun getEligibleAt(
        driverUserId: String,
        periodStartedAtEpochMillis: Long,
        status: ConnectionStatus = ConnectionStatus.APPROVED,
    ): List<TrustedContactConnectionProjectionEntity>
}
