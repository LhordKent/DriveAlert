package com.lhordkent.drivealert.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.data.local.entity.AlertEntity
import com.lhordkent.drivealert.data.local.entity.AlertSignEntity
import com.lhordkent.drivealert.data.local.entity.CalibrationEntity
import com.lhordkent.drivealert.data.local.entity.ProvisionedDeviceEntity
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionEntity
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionStatus
import com.lhordkent.drivealert.data.local.entity.StageSyncEligibility
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordSignEntity
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordType
import com.lhordkent.drivealert.data.local.entity.StageSyncStatus
import com.lhordkent.drivealert.data.local.entity.StoredVisibleSign
import com.lhordkent.drivealert.data.local.entity.StoredWarningStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveAlertDatabaseTest {
    @Test
    fun provisionedDeviceIsIsolatedPerDriverAndOnlyOneIsActive() = runBlocking {
        database.provisionedDeviceDao().insertAndActivate(device("driver-a", "camera-1", "192.168.1.20"))
        database.provisionedDeviceDao().insertAndActivate(device("driver-a", "camera-2", "192.168.1.21"))
        database.provisionedDeviceDao().insertAndActivate(device("driver-b", "camera-1", "192.168.1.22"))

        assertEquals("camera-2", database.provisionedDeviceDao().getActive("driver-a")?.deviceId)
        assertEquals("camera-1", database.provisionedDeviceDao().getActive("driver-b")?.deviceId)
    }

    private lateinit var context: Context
    private lateinit var database: DriveAlertDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, DriveAlertDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun monitoringSessionAndMultiSignAlertRoundTrip() = runBlocking {
        insertOperationalFoundation(driverUserId = "driver-a")
        val alert = alert(driverUserId = "driver-a")
        database.alertDao().insert(
            alert,
            setOf(
                AlertSignEntity(alert.alertId, StoredVisibleSign.PROLONGED_EYE_CLOSURE),
                AlertSignEntity(alert.alertId, StoredVisibleSign.YAWNING),
            ),
        )

        val stored = database.alertDao().getById("driver-a", alert.alertId)

        assertNotNull(stored)
        assertEquals(StoredWarningStage.STAGE_3, stored?.alert?.warningStageAtDetection)
        assertEquals(
            setOf(StoredVisibleSign.PROLONGED_EYE_CLOSURE, StoredVisibleSign.YAWNING),
            stored?.signs?.map { it.signType }?.toSet(),
        )
        assertNull(database.alertDao().getById("driver-b", alert.alertId))
    }

    @Test
    fun activeCalibrationIsReplacedTransactionallyAndHistoryIsRetained() = runBlocking {
        val first = calibration("calibration-1", "driver-a", 1_000L)
        val second = calibration("calibration-2", "driver-a", 2_000L)

        database.calibrationDao().insertAndActivate(first)
        database.calibrationDao().insertAndActivate(second)

        assertEquals("calibration-2", database.calibrationDao().getActive("driver-a")?.calibrationId)
        val history = database.calibrationDao().observeHistory("driver-a").first()
        assertEquals(2, history.size)
        assertEquals(1, history.count { it.isActive })
    }

    @Test
    fun pendingStageSyncRecordCanBeQueriedAndMarkedSyncedWithoutChangingAlert() = runBlocking {
        insertOperationalFoundation(driverUserId = "driver-a")
        val localAlert = alert(driverUserId = "driver-a")
        database.alertDao().insert(
            localAlert,
            setOf(AlertSignEntity(localAlert.alertId, StoredVisibleSign.HEAD_NODDING)),
        )
        val record = stageSyncRecord(driverUserId = "driver-a")
        database.stageSyncRecordDao().insert(
            record,
            setOf(StageSyncRecordSignEntity(record.stageSyncRecordId, StoredVisibleSign.HEAD_NODDING)),
        )

        val pending = database.stageSyncRecordDao().getRecordsReadyForSync("driver-a", 10)
        assertEquals(listOf(record.stageSyncRecordId), pending.map { it.record.stageSyncRecordId })

        val updated = database.stageSyncRecordDao().updateSyncState(
            driverUserId = "driver-a",
            recordId = record.stageSyncRecordId,
            status = StageSyncStatus.SYNCED,
            attemptCount = 1,
            lastAttemptAtEpochMillis = 3_000L,
            syncedAtEpochMillis = 3_100L,
            errorCode = null,
            errorMessage = null,
        )

        assertEquals(1, updated)
        assertEquals(StageSyncStatus.SYNCED, database.stageSyncRecordDao().getById("driver-a", record.stageSyncRecordId)?.record?.syncStatus)
        assertNotNull(database.alertDao().getById("driver-a", localAlert.alertId))
    }

    @Test
    fun localAlertSurvivesDatabaseCloseAndReopen() = runBlocking {
        database.close()
        val databaseName = "drivealert-reopen-test.db"
        context.deleteDatabase(databaseName)
        var fileDatabase = Room.databaseBuilder(context, DriveAlertDatabase::class.java, databaseName).build()
        insertOperationalFoundation(fileDatabase, "driver-a")
        val alert = alert(driverUserId = "driver-a")
        fileDatabase.alertDao().insert(
            alert,
            setOf(AlertSignEntity(alert.alertId, StoredVisibleSign.PROLONGED_EYE_CLOSURE)),
        )
        fileDatabase.close()

        fileDatabase = Room.databaseBuilder(context, DriveAlertDatabase::class.java, databaseName).build()
        assertNotNull(fileDatabase.alertDao().getById("driver-a", alert.alertId))
        fileDatabase.close()
        context.deleteDatabase(databaseName)

        database = Room.inMemoryDatabaseBuilder(context, DriveAlertDatabase::class.java).build()
    }

    @Test
    fun alarmTimestampInvariantRejectsTimestampWhenAlarmWasNotTriggered() {
        val error = runCatching {
            alert(driverUserId = "driver-a").copy(
                alarmTriggered = false,
                alarmTriggeredAtEpochMillis = 2_100L,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private suspend fun insertOperationalFoundation(driverUserId: String) {
        insertOperationalFoundation(database, driverUserId)
    }

    private suspend fun insertOperationalFoundation(target: DriveAlertDatabase, driverUserId: String) {
        target.calibrationDao().insertAndActivate(calibration("calibration-$driverUserId", driverUserId, 1_000L))
        target.monitoringSessionDao().insert(
            MonitoringSessionEntity(
                sessionId = "session-$driverUserId",
                driverUserId = driverUserId,
                calibrationId = "calibration-$driverUserId",
                deviceId = "device-$driverUserId",
                startedAtEpochMillis = 1_500L,
                endedAtEpochMillis = null,
                status = MonitoringSessionStatus.ACTIVE,
                highestWarningStage = StoredWarningStage.STAGE_3,
                createdAtEpochMillis = 1_500L,
                updatedAtEpochMillis = 1_500L,
            ),
        )
    }

    private fun calibration(id: String, driverUserId: String, time: Long) = CalibrationEntity(
        calibrationId = id,
        driverUserId = driverUserId,
        earThreshold = 0.21,
        marThreshold = 0.62,
        headPitchThresholdDegrees = 14.0,
        neutralHeadPitchDegrees = 1.5,
        calibratedAtEpochMillis = time,
        isActive = true,
    )

    private fun device(driverId: String, deviceId: String, ip: String) = ProvisionedDeviceEntity(
        deviceId = deviceId,
        driverUserId = driverId,
        hostname = deviceId,
        lastKnownIp = ip,
        streamPort = 81,
        streamPath = "/stream",
        lastConnectedAtEpochMillis = 1_000,
        isActive = true,
    )

    private fun alert(driverUserId: String) = AlertEntity(
        alertId = "alert-$driverUserId",
        sessionId = "session-$driverUserId",
        driverUserId = driverUserId,
        detectedAtEpochMillis = 2_000L,
        warningStageAtDetection = StoredWarningStage.STAGE_3,
        alarmTriggered = true,
        alarmTriggeredAtEpochMillis = 2_000L,
        createdAtEpochMillis = 2_000L,
    )

    private fun stageSyncRecord(driverUserId: String) = StageSyncRecordEntity(
        stageSyncRecordId = "sync-$driverUserId",
        sessionId = "session-$driverUserId",
        driverUserId = driverUserId,
        recordType = StageSyncRecordType.STAGE3_TRANSITION,
        periodStartedAtEpochMillis = 2_000L,
        periodEndedAtEpochMillis = null,
        eventCount = 1,
        eligibility = StageSyncEligibility.ELIGIBLE,
        syncStatus = StageSyncStatus.PENDING,
        attemptCount = 0,
        lastAttemptAtEpochMillis = null,
        syncedAtEpochMillis = null,
        syncErrorCode = null,
        syncErrorMessage = null,
        firestoreDocumentId = "sync-$driverUserId",
        createdAtEpochMillis = 2_000L,
    )
}
