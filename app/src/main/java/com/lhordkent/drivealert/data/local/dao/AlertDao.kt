package com.lhordkent.drivealert.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lhordkent.drivealert.data.local.entity.AlertEntity
import com.lhordkent.drivealert.data.local.entity.AlertSignEntity
import com.lhordkent.drivealert.data.local.entity.AlertWithSigns
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AlertDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertEntity(alert: AlertEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertSigns(signs: List<AlertSignEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertSignsIgnoringDuplicates(signs: List<AlertSignEntity>)

    @Transaction
    open suspend fun insert(alert: AlertEntity, signs: Set<AlertSignEntity>) {
        require(signs.isNotEmpty()) { "A confirmed Alert must contain at least one selected visible sign." }
        require(signs.all { it.alertId == alert.alertId }) { "Every sign must belong to the inserted Alert." }
        insertEntity(alert)
        insertSigns(signs.toList())
    }

    @Transaction
    open suspend fun addSigns(alertId: String, signs: Set<AlertSignEntity>) {
        require(signs.isNotEmpty())
        require(signs.all { it.alertId == alertId })
        insertSignsIgnoringDuplicates(signs.toList())
    }

    @Transaction
    @Query("SELECT * FROM alerts WHERE alertId = :alertId AND driverUserId = :driverUserId")
    abstract suspend fun getById(driverUserId: String, alertId: String): AlertWithSigns?

    @Transaction
    @Query("SELECT * FROM alerts WHERE driverUserId = :driverUserId ORDER BY detectedAtEpochMillis DESC")
    abstract fun observeForDriver(driverUserId: String): Flow<List<AlertWithSigns>>

    @Transaction
    @Query("SELECT * FROM alerts WHERE driverUserId = :driverUserId AND sessionId = :sessionId ORDER BY detectedAtEpochMillis DESC")
    abstract fun observeForSession(driverUserId: String, sessionId: String): Flow<List<AlertWithSigns>>

    @Transaction
    @Query("SELECT * FROM alerts WHERE driverUserId = :driverUserId ORDER BY detectedAtEpochMillis DESC LIMIT :limit")
    abstract fun observeLatest(driverUserId: String, limit: Int): Flow<List<AlertWithSigns>>
}
