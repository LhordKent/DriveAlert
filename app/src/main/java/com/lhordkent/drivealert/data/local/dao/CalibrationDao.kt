package com.lhordkent.drivealert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lhordkent.drivealert.data.local.entity.CalibrationEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CalibrationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertEntity(calibration: CalibrationEntity)

    @Query("UPDATE driver_calibrations SET isActive = 0 WHERE driverUserId = :driverUserId AND isActive = 1")
    protected abstract suspend fun deactivateForDriver(driverUserId: String)

    @Transaction
    open suspend fun insertAndActivate(calibration: CalibrationEntity) {
        require(calibration.isActive) { "A calibration activated through insertAndActivate must be active." }
        deactivateForDriver(calibration.driverUserId)
        insertEntity(calibration)
    }

    @Query("SELECT * FROM driver_calibrations WHERE driverUserId = :driverUserId AND isActive = 1 ORDER BY calibratedAtEpochMillis DESC LIMIT 1")
    abstract suspend fun getActive(driverUserId: String): CalibrationEntity?

    @Query("SELECT * FROM driver_calibrations WHERE driverUserId = :driverUserId ORDER BY calibratedAtEpochMillis DESC")
    abstract fun observeHistory(driverUserId: String): Flow<List<CalibrationEntity>>
}
