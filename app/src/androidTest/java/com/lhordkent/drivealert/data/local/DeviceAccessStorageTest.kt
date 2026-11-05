package com.lhordkent.drivealert.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.data.device.DeviceAccessGrant
import com.lhordkent.drivealert.device.DeviceCredential
import com.lhordkent.drivealert.device.DeviceCredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceAccessStorageTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DriveAlertDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())

    @Test fun migration6To7KeepsDeviceConnectionsAndCreatesNoAutomaticGrants() {
        val name = "device-access-migration"
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO provisioned_devices (deviceId, driverUserId, hostname, lastKnownIp, streamPort, streamPath, lastConnectedAtEpochMillis, isActive) VALUES ('legacy-camera', 'driver-a', 'drivealert-eeff', '192.168.1.20', 81, '/stream', 123, 1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 7, true, DriveAlertDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT deviceId FROM provisioned_devices WHERE driverUserId='driver-a'").use { assertTrue(it.moveToFirst()); assertEquals("legacy-camera", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM device_access").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun staleGrantsCannotResurrectRemovalOrAuthorizeAnotherAccount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DriveAlertDatabase::class.java).build()
        try {
            val dao = db.deviceAccessDao()
            val active = DeviceAccessGrant("a", "AABBCCDDEEFF", "owner", "Camera", "ACTIVE", 2, 100)
            dao.saveVerified(active)
            dao.saveVerified(active.copy(status = "REMOVED", revision = 3))
            dao.saveVerified(active)
            assertFalse(dao.get("a", active.deviceId)!!.allowed)
            assertNull(dao.get("b", active.deviceId))
            dao.saveVerified(active.copy(revision = 5))
            assertTrue(dao.get("a", active.deviceId)!!.allowed)
        } finally { db.close() }
    }

    @Test fun keystoreCacheSurvivesRecreationAndSeparatesAccountSecrets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val credential = DeviceCredential("AABBCCDDEEFF", "00".repeat(16), "AB".repeat(32), true)
        DeviceCredentialStore(context).save("storage-test-a", credential)
        val restored = DeviceCredentialStore(context)
        assertEquals(credential, restored.load("storage-test-a", credential.deviceId))
        assertNull(restored.load("storage-test-b", credential.deviceId))
        restored.saveSession("storage-test-a", credential.deviceId, "CD".repeat(32))
        assertEquals("CD".repeat(32), DeviceCredentialStore(context).session("storage-test-a", credential.deviceId))
        assertNull(restored.session("storage-test-b", credential.deviceId))
        restored.setRevocation("storage-test-a", credential.deviceId, credential.accountId, 4)
        restored.setRevocation("storage-test-a", credential.deviceId, credential.accountId, 2)
        assertEquals(listOf(credential.accountId to 4L), restored.revocations("storage-test-a", credential.deviceId))
    }
}
