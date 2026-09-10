package com.lhordkent.drivealert.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DriveAlertMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DriveAlertDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration1To2PreservesHistoryAndDeactivatesIncompleteCalibration() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """INSERT INTO driver_calibrations
                    (calibrationId, driverUserId, earThreshold, marThreshold,
                     headPitchThresholdDegrees, neutralHeadPitchDegrees,
                     calibratedAtEpochMillis, isActive)
                    VALUES ('legacy', 'driver-a', 0.2, 0.6, 15.0, 2.0, 1000, 1)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, DriveAlertDatabase.MIGRATION_1_2)
        migrated.query("SELECT calibrationId, isActive, detectorSchemaVersion FROM driver_calibrations").use { cursor ->
            cursor.moveToFirst()
            assertEquals("legacy", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
        migrated.query("SELECT COUNT(*) FROM provisioned_devices").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migration2To3EnablesDriverWarningNotificationsByDefault() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """INSERT INTO driver_preferences
                    (driverUserId, selectedWarningSound, preferredWarningVolume,
                     setupAndDeviceRemindersEnabled, contactRequestUpdatesEnabled,
                     trustedRequestUpdatesEnabled, sharedStage3RecordsEnabled, updatedAtEpochMillis)
                    VALUES ('driver-a', 'DIGITAL_BEEP', 'MEDIUM', 1, 1, 1, 1, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 3, true, DriveAlertDatabase.MIGRATION_2_3)
        migrated.query("SELECT warningAlertsEnabled FROM driver_preferences WHERE driverUserId = 'driver-a'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.close()
    }

    companion object { private const val TEST_DB = "drivealert-migration-test" }
}
