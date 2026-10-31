package com.lhordkent.drivealert.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.data.monitoringlog.*
import com.lhordkent.drivealert.data.local.entity.*
import com.lhordkent.drivealert.data.history.HistoryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class MonitoringLogRepositoryTest {
    private lateinit var db: DriveAlertDatabase
    private val now = 20 * 86_400_000L
    private val repository get() = MonitoringLogRepository(db) { now }
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DriveAlertDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, DriveAlertDatabase::class.java).build() }
    @After fun close() { db.close() }

    @Test fun migration5To6PreservesExistingDataAndCreatesEmptyLogs() {
        val name = "monitoring-logs-migration"
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO driver_preferences (driverUserId, selectedWarningSound, preferredWarningVolume, setupAndDeviceRemindersEnabled, contactRequestUpdatesEnabled, trustedRequestUpdatesEnabled, sharedStage3RecordsEnabled, warningAlertsEnabled, historyRetentionDays, updatedAtEpochMillis) VALUES ('a', 'ROOSTER_CALL', 'HIGH', 1, 0, 1, 0, 0, 7, 1000)")
            execSQL("INSERT INTO driver_calibrations (calibrationId, driverUserId, earThreshold, marThreshold, headPitchThresholdDegrees, neutralHeadPitchDegrees, calibratedAtEpochMillis, isActive) VALUES ('cal', 'a', 0.2, 0.6, 15.0, 0.0, 1000, 1)")
            execSQL("INSERT INTO monitoring_sessions (sessionId, driverUserId, calibrationId, deviceId, startedAtEpochMillis, endedAtEpochMillis, status, highestWarningStage, createdAtEpochMillis, updatedAtEpochMillis) VALUES ('core', 'a', 'cal', 'device', 1000, 2000, 'COMPLETED', 'STAGE_3', 1000, 2000)")
            execSQL("INSERT INTO alerts (alertId, sessionId, driverUserId, detectedAtEpochMillis, warningStageAtDetection, alarmTriggered, alarmTriggeredAtEpochMillis, createdAtEpochMillis) VALUES ('alert', 'core', 'a', 1500, 'STAGE_3', 1, 1500, 1500)")
            close()
        }
        helper.runMigrationsAndValidate(name, 6, true, DriveAlertDatabase.MIGRATION_5_6).use { migrated ->
            migrated.query("SELECT historyRetentionDays FROM driver_preferences WHERE driverUserId = 'a'").use { it.moveToFirst(); assertEquals(7, it.getInt(0)) }
            listOf("alerts", "monitoring_sessions", "driver_calibrations").forEach { table -> migrated.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) } }
            migrated.query("SELECT COUNT(*) FROM monitoring_logs").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun retentionUsesEndTimeProtectsOpenSessionsAndIsAccountScoped() = runBlocking {
        val repo = repository
        val cutoff = now - 86_400_000L
        repo.begin("a", "old", 1, false); repo.end("a", "old", cutoff - 1, "COMPLETED")
        repo.begin("a", "boundary", 1, false); repo.end("a", "boundary", cutoff, "COMPLETED")
        repo.begin("a", "active", 1, false)
        repo.begin("b", "other", 1, false); repo.end("b", "other", 2, "COMPLETED")
        assertEquals(1, repo.dao.deletionCount("a", cutoff))
        repo.saveRetention("a", 1)
        assertNull(repo.dao.get("a", "old")); assertNotNull(repo.dao.get("a", "boundary"))
        assertNotNull(repo.dao.get("a", "active")); assertNotNull(repo.dao.get("b", "other"))
        assertEquals(0, repo.dao.delete("a", "active")); assertEquals(0, repo.dao.delete("b", "boundary"))
        assertTrue(repo.dao.events("a", "old", 50).first().isEmpty())
        assertEquals(1, repo.dao.preference("a").first()!!.retentionDays)
        assertNull(repo.dao.preference("b").first())
        try { repo.saveRetention("a", 3651); fail("invalid days accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun deletingEitherHistoryLeavesTheOtherAndCoreRecordsIntact() = runBlocking {
        db.calibrationDao().insertAndActivate(CalibrationEntity("cal", "a", .2, .6, 15.0, 0.0, calibratedAtEpochMillis = 1, isActive = true))
        db.monitoringSessionDao().insert(MonitoringSessionEntity("core", "a", "cal", "device", 1, 100, MonitoringSessionStatus.COMPLETED, null, 1, 100))
        db.alertDao().insert(AlertEntity("alert", "core", "a", 50, StoredWarningStage.STAGE_1, false, null, 50), setOf(AlertSignEntity("alert", StoredVisibleSign.YAWNING)))
        val repo = repository
        repo.begin("a", "core", 1, false); repo.end("a", "core", 100, "COMPLETED")
        repo.dao.delete("a", "core")
        assertNotNull(db.alertDao().getById("a", "alert")); assertNotNull(db.monitoringSessionDao().getById("a", "core")); assertNotNull(db.calibrationDao().getActive("a"))
        repo.begin("a", "independent", 1, false)
        HistoryRepository(db).deleteAlert("a", "alert")
        assertNotNull(repo.dao.get("a", "independent"))
    }

    @Test fun stablePagingRequestUpdatesAndRecoveryNeverResurrectDeletedLogs() = runBlocking {
        val repo = repository
        repo.begin("a", "one", 1, false)
        repeat(70) { index -> repo.append(MonitoringLogEvent(eventId = "e$index", sessionId = "one", driverUserId = "a", occurredAt = 10, kind = "ALARM", stage = 1, repeated = index > 0, result = "PENDING", processId = "old")) }
        assertEquals(50, repo.dao.events("a", "one", 50).first().size)
        assertEquals(71, repo.dao.events("a", "one", 100).first().size)
        assertTrue(repo.dao.events("b", "one", 50).first().isEmpty())
        repo.dao.result("b", "e0", "ACCEPTED", 20)
        assertEquals("PENDING", repo.dao.events("a", "one", 100).first().first { it.eventId == "e0" }.result)
        repo.dao.result("a", "e0", "ACCEPTED", 20)
        repo.dao.recoverRequests("new")
        val events = repo.dao.events("a", "one", 100).first()
        assertEquals("ACCEPTED", events.first { it.eventId == "e0" }.result)
        assertEquals(69, events.count { it.result == "UNKNOWN_INTERRUPTED" })
        repo.end("a", "one", 100, "COMPLETED"); repo.dao.delete("a", "one")
        repo.append(events.first()); repo.dao.result("a", "e0", "FAILED", 101)
        assertNull(repo.dao.get("a", "one")); assertTrue(repo.dao.events("a", "one", 100).first().isEmpty())
        repo.begin("a", "legacy", 1, true); assertNull(repo.dao.get("a", "legacy"))
    }

    @Test fun confirmationRechecksCountAndRecoveryRepairsAnUnwrittenEnd() = runBlocking {
        val repo = repository
        repo.begin("a", "one", 1, false); repo.end("a", "one", 2, "COMPLETED")
        val count = repo.dao.deletionCount("a", null)
        repo.begin("a", "two", 1, false); repo.end("a", "two", 2, "COMPLETED")
        assertFalse(repo.confirmDeletion("a", null, count, false, null))
        assertNotNull(repo.dao.get("a", "one"))
        assertTrue(repo.confirmDeletion("a", null, 2, false, null))
        db.calibrationDao().insertAndActivate(CalibrationEntity("cal", "a", .2, .6, 15.0, 0.0, calibratedAtEpochMillis = 1, isActive = true))
        db.monitoringSessionDao().insert(MonitoringSessionEntity("core", "a", "cal", "device", 1, 100, MonitoringSessionStatus.INTERRUPTED, null, 1, 100))
        repo.begin("a", "core", 1, false)
        repo.recover("next-process")
        val log = repo.dao.get("a", "core")!!
        assertEquals(100L, log.endedAt); assertEquals("INTERRUPTED", log.status); assertTrue(log.recordingGap)
        assertEquals(1, repo.dao.events("a", "core", 50).first().count { it.kind == "INTERRUPTED" })
        repo.recover("next-process")
        assertEquals(2, repo.dao.events("a", "core", 50).first().size)
    }

    @Test fun writerSnapshotsActualDetectorEvidenceAndRecordsEachAlarmResultInOrder() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val writer = MonitoringLogWriter(repository, scope)
            writer.begin("a", "session", 100, false)
            val eye = com.lhordkent.drivealert.detection.temporal.EyeClosureDetector(.3, 2200, "cal", 3)
            eye.update(0, .2)
            writer.sign("a", "session", 2400, eye.update(2300, .18)!!)
            val yawn = com.lhordkent.drivealert.detection.temporal.YawnDetector(.5, 3100, "cal", 3)
            yawn.update(0, .7)
            writer.sign("a", "session", 3400, yawn.update(3300, .8)!!)
            val head = com.lhordkent.drivealert.detection.temporal.HeadDownDetector(15.0, 3000, .8, "cal", 3)
            head.update(0, 20.0); head.update(1500, 21.0)
            writer.sign("a", "session", 3500, head.update(3000, 22.0)!!)
            val first = writer.request("a", "session", 3600, 1, false)
            writer.result("a", "session", first, 3610, com.lhordkent.drivealert.monitoring.WarningDeliveryStatus.DELIVERED)
            val repeat = writer.request("a", "session", 4000, 1, true)
            writer.result("a", "session", repeat, 4020, com.lhordkent.drivealert.monitoring.WarningDeliveryStatus.FAILED)
            val cancelled = writer.request("a", "session", 4100, 2, false)
            writer.result("a", "session", cancelled, 4200, null)
            writer.end("a", "session", 4300, "COMPLETED")
            withTimeout(5000) { while (repository.dao.get("a", "session")?.endedAt == null) delay(10) }
            val events = repository.dao.events("a", "session", 50).first().asReversed()
            assertEquals(listOf("START", "SIGN", "SIGN", "SIGN", "ALARM", "ALARM", "ALARM", "COMPLETED"), events.map { it.kind })
            assertEquals(.18, events[1].measuredValue!!, 0.0)
            assertEquals(2300L, events[1].durationMs); assertEquals(2200L, events[1].requiredDurationMs)
            assertEquals(3100L, events[2].requiredDurationMs)
            assertEquals(22.0, events[3].measuredValue!!, 0.0)
            assertEquals(.8, events[3].ratioThreshold!!, 0.0); assertEquals(1.0, events[3].downwardRatio!!, 0.0)
            assertEquals("cal", events[3].calibrationId)
            assertEquals(listOf("ACCEPTED", "FAILED", "UNKNOWN_INTERRUPTED"), events.filter { it.kind == "ALARM" }.map { it.result })
            assertTrue(events[5].repeated); assertEquals(4020L, events[5].responseAt)
            assertTrue(writer.failedDrivers.value.isEmpty())
        } finally { scope.cancel() }
    }
}
