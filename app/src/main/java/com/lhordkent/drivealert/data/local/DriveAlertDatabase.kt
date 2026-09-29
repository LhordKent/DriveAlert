package com.lhordkent.drivealert.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lhordkent.drivealert.data.local.dao.AlertDao
import com.lhordkent.drivealert.data.local.dao.CalibrationDao
import com.lhordkent.drivealert.data.local.dao.DriverPreferenceDao
import com.lhordkent.drivealert.data.local.dao.MonitoringSessionDao
import com.lhordkent.drivealert.data.local.dao.StageSyncRecordDao
import com.lhordkent.drivealert.data.local.dao.StageNotificationGroupDao
import com.lhordkent.drivealert.data.local.dao.TrustedContactConnectionProjectionDao
import com.lhordkent.drivealert.data.local.dao.ProvisionedDeviceDao
import com.lhordkent.drivealert.data.local.entity.AlertEntity
import com.lhordkent.drivealert.data.local.entity.AlertSignEntity
import com.lhordkent.drivealert.data.local.entity.CalibrationEntity
import com.lhordkent.drivealert.data.local.entity.DriverPreferenceEntity
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordSignEntity
import com.lhordkent.drivealert.data.local.entity.StageNotificationGroupEntity
import com.lhordkent.drivealert.data.local.entity.StageNotificationGroupRecordEntity
import com.lhordkent.drivealert.data.local.entity.TrustedContactConnectionProjectionEntity
import com.lhordkent.drivealert.data.local.entity.ProvisionedDeviceEntity

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
        ProvisionedDeviceEntity::class,
        StageNotificationGroupEntity::class,
        StageNotificationGroupRecordEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(DriveAlertTypeConverters::class)
abstract class DriveAlertDatabase : RoomDatabase() {
    abstract fun monitoringSessionDao(): MonitoringSessionDao
    abstract fun alertDao(): AlertDao
    abstract fun calibrationDao(): CalibrationDao
    abstract fun driverPreferenceDao(): DriverPreferenceDao
    abstract fun stageSyncRecordDao(): StageSyncRecordDao
    abstract fun stageNotificationGroupDao(): StageNotificationGroupDao
    abstract fun trustedContactConnectionProjectionDao(): TrustedContactConnectionProjectionDao
    abstract fun provisionedDeviceDao(): ProvisionedDeviceDao

    companion object {
        const val DATABASE_NAME = "drivealert.db"

        @Volatile private var instance: DriveAlertDatabase? = null

        fun getInstance(context: Context): DriveAlertDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                DriveAlertDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE driver_calibrations ADD COLUMN detectorSchemaVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE driver_calibrations ADD COLUMN neutralEar REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE driver_calibrations ADD COLUMN closedEyeEar REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE driver_calibrations ADD COLUMN neutralMar REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE driver_calibrations ADD COLUMN openMouthMar REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE driver_calibrations ADD COLUMN downwardPitchMultiplier REAL NOT NULL DEFAULT 1")
                db.execSQL("UPDATE driver_calibrations SET isActive = 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS provisioned_devices (
                        deviceId TEXT NOT NULL,
                        driverUserId TEXT NOT NULL,
                        hostname TEXT,
                        lastKnownIp TEXT NOT NULL,
                        streamPort INTEGER NOT NULL,
                        streamPath TEXT NOT NULL,
                        lastConnectedAtEpochMillis INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        PRIMARY KEY(driverUserId, deviceId)
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_provisioned_devices_driverUserId ON provisioned_devices(driverUserId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_provisioned_devices_driverUserId_isActive ON provisioned_devices(driverUserId, isActive)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE driver_preferences ADD COLUMN warningAlertsEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS stage_notification_groups (
                    dispatchGroupId TEXT NOT NULL,
                    driverUserId TEXT NOT NULL,
                    status TEXT NOT NULL,
	                    expectedRecordCount INTEGER NOT NULL,
	                    expectedChunkCount INTEGER NOT NULL,
                    attemptCount INTEGER NOT NULL,
                    lastAttemptAtEpochMillis INTEGER,
                    lastErrorCode TEXT,
                    createdAtEpochMillis INTEGER NOT NULL,
                    completedAtEpochMillis INTEGER,
                    PRIMARY KEY(dispatchGroupId)
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stage_notification_groups_driverUserId ON stage_notification_groups(driverUserId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stage_notification_groups_driverUserId_status ON stage_notification_groups(driverUserId, status)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS stage_notification_group_records (
                    dispatchGroupId TEXT NOT NULL,
                    stageSyncRecordId TEXT NOT NULL,
                    ordinal INTEGER NOT NULL,
                    PRIMARY KEY(dispatchGroupId, stageSyncRecordId),
                    FOREIGN KEY(dispatchGroupId) REFERENCES stage_notification_groups(dispatchGroupId) ON UPDATE NO ACTION ON DELETE CASCADE
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stage_notification_group_records_dispatchGroupId ON stage_notification_group_records(dispatchGroupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stage_notification_group_records_stageSyncRecordId ON stage_notification_group_records(stageSyncRecordId)")
            }
        }
    }
}
