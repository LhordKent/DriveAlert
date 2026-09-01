package com.lhordkent.drivealert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lhordkent.drivealert.data.local.entity.DriverPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriverPreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: DriverPreferenceEntity)

    @Query("SELECT * FROM driver_preferences WHERE driverUserId = :driverUserId")
    fun observe(driverUserId: String): Flow<DriverPreferenceEntity?>

    @Query("SELECT * FROM driver_preferences WHERE driverUserId = :driverUserId")
    suspend fun get(driverUserId: String): DriverPreferenceEntity?
}
