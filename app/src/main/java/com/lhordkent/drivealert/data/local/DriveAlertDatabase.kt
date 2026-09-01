package com.lhordkent.drivealert.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lhordkent.drivealert.data.local.dao.AlertDao
import com.lhordkent.drivealert.data.local.dao.CalibrationDao
import com.lhordkent.drivealert.data.local.dao.DriverPreferenceDao
import com.lhordkent.drivealert.data.local.dao.MonitoringSessionDao
import com.lhordkent.drivealert.data.local.dao.StageSyncRecordDao
import com.lhordkent.drivealert.data.local.dao.TrustedContactConnectionProjectionDao
import com.lhordkent.drivealert.data.local.entity.AlertEntity
import com.lhordkent.drivealert.data.local.entity.AlertSignEntity
import com.lhordkent.drivealert.data.local.entity.CalibrationEntity
import com.lhordkent.drivealert.data.local.entity.DriverPreferenceEntity
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordSignEntity
import com.lhordkent.drivealert.data.local.entity.TrustedContactConnectionProjectionEntity

@Database(
    entities = [
        CalibrationEntity::class,
        MonitoringSessionEntity::class,
        AlertEntity::class,
        AlertSignEntity::class,
        DriverPreferenceEntity::class,
        StageSyncRecordEntity::class,
        StageSyncRecordSignEntity::class,
        TrustedContactConnectionProjectionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DriveAlertTypeConverters::class)
abstract class DriveAlertDatabase : RoomDatabase() {
    abstract fun monitoringSessionDao(): MonitoringSessionDao
    abstract fun alertDao(): AlertDao
    abstract fun calibrationDao(): CalibrationDao
    abstract fun driverPreferenceDao(): DriverPreferenceDao
    abstract fun stageSyncRecordDao(): StageSyncRecordDao
    abstract fun trustedContactConnectionProjectionDao(): TrustedContactConnectionProjectionDao

    companion object {
        const val DATABASE_NAME = "drivealert.db"

        @Volatile private var instance: DriveAlertDatabase? = null

        fun getInstance(context: Context): DriveAlertDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DriveAlertDatabase::class.java,
                DATABASE_NAME,
            ).build().also { instance = it }
        }
    }
}
