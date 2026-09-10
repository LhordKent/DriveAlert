package com.lhordkent.drivealert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lhordkent.drivealert.data.local.entity.ProvisionedDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProvisionedDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertEntity(device: ProvisionedDeviceEntity)

    @Query("UPDATE provisioned_devices SET isActive = 0 WHERE driverUserId = :driverUserId AND isActive = 1")
    protected abstract suspend fun deactivateForDriver(driverUserId: String)

    @Transaction
    open suspend fun insertAndActivate(device: ProvisionedDeviceEntity) {
        require(device.isActive)
        deactivateForDriver(device.driverUserId)
        insertEntity(device)
    }

    @Query("SELECT * FROM provisioned_devices WHERE driverUserId = :driverUserId AND isActive = 1 LIMIT 1")
    abstract fun observeActive(driverUserId: String): Flow<ProvisionedDeviceEntity?>

    @Query("SELECT * FROM provisioned_devices WHERE driverUserId = :driverUserId AND isActive = 1 LIMIT 1")
    abstract suspend fun getActive(driverUserId: String): ProvisionedDeviceEntity?
}
