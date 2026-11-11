package com.lhordkent.drivealert.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.data.local.entity.*
import com.lhordkent.drivealert.data.repository.ConfirmedAlertInput
import com.lhordkent.drivealert.data.repository.RoomAlertRepository
import com.lhordkent.drivealert.postauth.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.ZoneOffset

class CodexAuditDatabaseTest {
    private lateinit var db: DriveAlertDatabase
    private lateinit var repository: RoomAlertRepository
    private val today = LocalDate.of(2026, 11, 8)
    private var sequence = 0

    @Before fun prepare() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, DriveAlertDatabase::class.java).build()
        repository = RoomAlertRepository(db.alertDao(), idFactory = { "audit-${++sequence}" }, zoneId = ZoneOffset.UTC)
        for (driver in listOf("audit-driver-a", "audit-driver-b")) {
            db.calibrationDao().insertAndActivate(CalibrationEntity(
                calibrationId = "calibration-$driver", driverUserId = driver,
                earThreshold = .21, marThreshold = .62, headPitchThresholdDegrees = 15.0,
                neutralHeadPitchDegrees = 0.0, calibratedAtEpochMillis = 1L, isActive = true,
            ))
            db.monitoringSessionDao().insert(MonitoringSessionEntity(
                sessionId = "session-$driver", driverUserId = driver, calibrationId = "calibration-$driver",
                deviceId = "device-$driver", startedAtEpochMillis = 1L, endedAtEpochMillis = 2L,
                status = MonitoringSessionStatus.COMPLETED, highestWarningStage = StoredWarningStage.STAGE_3,
                createdAtEpochMillis = 1L, updatedAtEpochMillis = 2L,
            ))
        }
    }
    @After fun close() { db.close() }

    private suspend fun add(driver: String, daysAgo: Long, stage: WarningStage, vararg signs: VisibleSign): String =
        repository.recordConfirmedAlert(ConfirmedAlertInput(
            "session-$driver", driver, today.minusDays(daysAgo).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            stage, signs.toSet(), false, null,
        ))

    @Test fun u511_historyListsAndDetailsStayIsolatedForTwoDrivers() = runBlocking {
        val first = add("audit-driver-a", 1, WarningStage.STAGE_2, VisibleSign.YAWNING)
        val second = add("audit-driver-a", 0, WarningStage.STAGE_3, VisibleSign.HEAD_NODDING)
        val other = add("audit-driver-b", 0, WarningStage.STAGE_1, VisibleSign.PROLONGED_EYE_CLOSURE)
        assertEquals(listOf(second, first), repository.observeAlerts("audit-driver-a").first().map { it.id })
        assertEquals(listOf(other), repository.observeAlerts("audit-driver-b").first().map { it.id })
        assertTrue(repository.observeAlerts("unknown-driver").first().isEmpty())
        assertNull(repository.getAlert("audit-driver-b", first))
        assertNull(repository.getAlert("audit-driver-a", other))
        // Read A again after B to verify that switching readers does not leak the last account's records.
        assertEquals(listOf(second, first), repository.observeAlerts("audit-driver-a").first().map { it.id })
    }

    @Test fun u531_insightsMatchKnownRoomRecordsAndExcludeOtherDriver() = runBlocking {
        add("audit-driver-a", 0, WarningStage.STAGE_1, VisibleSign.YAWNING)
        add("audit-driver-a", 0, WarningStage.STAGE_3, VisibleSign.YAWNING, VisibleSign.PROLONGED_EYE_CLOSURE)
        add("audit-driver-a", 6, WarningStage.STAGE_2, VisibleSign.HEAD_NODDING)
        add("audit-driver-a", 7, WarningStage.STAGE_1, VisibleSign.HEAD_NODDING)
        add("audit-driver-a", 8, WarningStage.STAGE_2, VisibleSign.HEAD_NODDING)
        add("audit-driver-b", 0, WarningStage.STAGE_1, VisibleSign.PROLONGED_EYE_CLOSURE)
        val insights = calculateAlertHistoryInsights(repository.observeAlerts("audit-driver-a").first(), today)
        assertEquals(2, insights.alertsToday)
        assertEquals(3, insights.weeklyAlerts)
        assertEquals(VisibleSign.HEAD_NODDING, insights.mostFrequentSign)
        assertEquals(WarningStage.STAGE_3, insights.highestStage)
        assertEquals(AlertTrend.HIGHER, insights.trend)
        val other = calculateAlertHistoryInsights(repository.observeAlerts("audit-driver-b").first(), today)
        assertEquals(1, other.alertsToday)
        assertEquals(1, other.weeklyAlerts)
        assertEquals(WarningStage.STAGE_1, other.highestStage)
    }
}
