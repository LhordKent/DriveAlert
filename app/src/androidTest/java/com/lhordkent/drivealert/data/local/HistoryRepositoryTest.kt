package com.lhordkent.drivealert.data.local

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.data.history.*
import com.lhordkent.drivealert.data.local.entity.*
import com.lhordkent.drivealert.data.repository.*
import com.lhordkent.drivealert.postauth.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDateTime

class HistoryRepositoryTest {
    private lateinit var db: DriveAlertDatabase
    private var now = 20 * HistoryRetention.DAY_MS
    private val history get() = HistoryRepository(db) { now }
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, DriveAlertDatabase::class.java).build() }
    @After fun close() { db.close() }

    private suspend fun session(user: String, id: String, status: MonitoringSessionStatus) {
        val calibrationId = "calibration-$id"
        db.calibrationDao().insertAndActivate(CalibrationEntity(calibrationId, user, .2, .6, 15.0, 0.0, calibratedAtEpochMillis = 1, isActive = true))
        db.monitoringSessionDao().insert(MonitoringSessionEntity(id, user, calibrationId, "device", 1,
            if (status in listOf(MonitoringSessionStatus.COMPLETED, MonitoringSessionStatus.INTERRUPTED)) 2 else null,
            status, StoredWarningStage.STAGE_3, 1, 1))
    }
    private suspend fun alert(id: String, user: String, session: String, at: Long) {
        db.alertDao().insert(AlertEntity(id, session, user, at, StoredWarningStage.STAGE_3, false, null, at),
            setOf(AlertSignEntity(id, StoredVisibleSign.YAWNING)))
    }
    private fun record(id: String, time: Long) = Stage3SyncRecord(id, LocalDateTime.of(2026, 1, 1, 0, 0),
        SyncRecordKind.STAGE_3_TRANSITION, emptySet(), SharingState.SHARED, occurredAtEpochMillis = time)

    @Test fun cleanupHonorsBoundaryAndAccountAndCascadesSignsButKeepsActiveAndPaused() = runBlocking {
        session("a", "done", MonitoringSessionStatus.COMPLETED)
        session("a", "active", MonitoringSessionStatus.ACTIVE)
        session("a", "paused", MonitoringSessionStatus.PAUSED)
        session("b", "other", MonitoringSessionStatus.COMPLETED)
        val cutoff = now - HistoryRetention.DAY_MS
        alert("old", "a", "done", cutoff - 1)
        alert("boundary", "a", "done", cutoff)
        alert("active-alert", "a", "active", 1)
        alert("paused-alert", "a", "paused", 1)
        alert("other-alert", "b", "other", 1)
        assertEquals(1, history.countExpiredAlerts("a", 1))
        history.setRetention("a", 1)
        assertNull(db.alertDao().getById("a", "old"))
        assertNotNull(db.alertDao().getById("a", "boundary"))
        assertNotNull(db.alertDao().getById("a", "active-alert"))
        assertNotNull(db.alertDao().getById("a", "paused-alert"))
        assertNotNull(db.alertDao().getById("b", "other-alert"))
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM alert_signs WHERE alertId = 'old'").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        assertNotNull(db.monitoringSessionDao().getById("a", "done"))
        assertNotNull(db.calibrationDao().getActive("a"))
    }

    @Test fun manualIsDefaultAndAlertDeletionCannotTouchCurrentDriveOrAnotherAccount() = runBlocking {
        session("a", "active", MonitoringSessionStatus.ACTIVE)
        session("a", "done", MonitoringSessionStatus.COMPLETED)
        alert("old", "a", "done", 1)
        alert("active", "a", "active", 1)
        history.cleanup("a")
        assertNotNull(db.alertDao().getById("a", "old"))
        assertEquals(0, history.deleteAlert("b", "old"))
        assertEquals(0, history.deleteAlert("a", "active"))
        assertEquals(1, history.clearAlerts("a"))
        assertNotNull(db.alertDao().getById("a", "active"))
    }

    @Test fun sharedRemovalSurvivesRecreationRefreshAndLongerRetentionWithoutAffectingOtherViewers() = runBlocking {
        val old = record("old", now - 2 * HistoryRetention.DAY_MS)
        val recent = record("recent", now)
        history.setRetention("trusted-a", 1)
        history.dismissShared("trusted-a", "driver-a", listOf("recent"))
        history.setRetention("trusted-a", 7)
        history.setRetention("trusted-a", null)
        val restored = HistoryRepository(db) { now }.observeVisibility("trusted-a").first()
        assertFalse(restored.allows("driver-a", old))
        assertFalse(restored.allows("driver-a", recent))
        assertTrue(restored.allows("driver-b", recent))
        assertTrue(history.observeVisibility("trusted-b").first().allows("driver-a", old))
        assertEquals(1, db.historyDao().observeDismissals("trusted-a").first().size)
    }

    @Test fun settingsAndSharedRemovalsSurviveDatabaseCloseAndReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-retention-reopen-test"
        context.deleteDatabase(name)
        var disk = Room.databaseBuilder(context, DriveAlertDatabase::class.java, name).build()
        try {
            val first = HistoryRepository(disk) { now }
            first.setRetention("trusted", 14)
            first.dismissShared("trusted", "driver", listOf("one", "two"))
            disk.close()
            disk = Room.databaseBuilder(context, DriveAlertDatabase::class.java, name).build()
            assertEquals(14, disk.driverPreferenceDao().get("trusted")!!.historyRetentionDays)
            val restored = HistoryRepository(disk) { now }.observeVisibility("trusted").first()
            assertFalse(restored.allows("driver", record("one", now)))
            assertFalse(restored.allows("driver", record("two", now)))
        } finally { disk.close(); context.deleteDatabase(name) }
    }

    @Test fun shorterRetentionOnlyDeletesAfterViewModelConfirmationAndCancelKeepsHistory() = runBlocking {
        session("a", "done", MonitoringSessionStatus.COMPLETED)
        alert("old", "a", "done", 1)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var model: PostAuthViewModel
        val store = androidx.lifecycle.ViewModelStore()
        instrumentation.runOnMainSync {
            model = PostAuthViewModel(driverPreferenceRepository = RoomDriverPreferenceRepository(db.driverPreferenceDao()),
                historyRepository = history, initialState = PostAuthUiState(connectedDrivers = listOf(
                    ConnectedDriver("driver", "Driver", "", LocalDateTime.now(), listOf(record("shared-old", 1))),
                )))
            store.put("history", model)
            model.bindPreferences("a")
        }
        fun await(condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 5000
            while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(25)
            assertTrue(condition())
        }
        try {
            await { model.state.historyPreferencesLoaded }
            instrumentation.runOnMainSync { model.previewRetention(1) }
            await { model.state.historyPreview != null && !model.state.historyBusy }
            assertEquals(2, model.state.historyPreview!!.affectedRecords)
            assertNull(db.driverPreferenceDao().get("a"))
            assertNotNull(db.alertDao().getById("a", "old"))
            instrumentation.runOnMainSync { model.dismissRetentionPreview() }
            assertNotNull(db.alertDao().getById("a", "old"))
            instrumentation.runOnMainSync { model.previewRetention(1) }
            await { model.state.historyPreview != null && !model.state.historyBusy }
            instrumentation.runOnMainSync { model.confirmRetention() }
            await { model.state.historyPreview == null && !model.state.historyBusy }
            assertEquals(1, db.driverPreferenceDao().get("a")!!.historyRetentionDays)
            assertNull(db.alertDao().getById("a", "old"))
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun preferenceChangesDoNotOverwriteRetentionAndPeriodicCleanupHandlesEveryConfiguredUser() = runBlocking {
        history.setRetention("a", 3)
        val prefs = RoomDriverPreferenceRepository(db.driverPreferenceDao())
        prefs.setPreferredVolume("a", PreferredVolume.HIGH)
        prefs.setWarningSound("a", WarningSound.ROOSTER_CALL)
        prefs.setNotifications("a", NotificationPreferences(warningAlerts = false))
        assertEquals(3, prefs.observe("a").first().historyRetentionDays)
        assertFalse(prefs.observe("a").first().notifications.warningAlerts)
        history.setRetention("b", 7)
        now += HistoryRetention.DAY_MS
        history.cleanupAll()
        assertEquals(now - 3 * HistoryRetention.DAY_MS, db.historyDao().expiry("a")!!.expiredBeforeEpochMillis)
        assertEquals(now - 7 * HistoryRetention.DAY_MS, db.historyDao().expiry("b")!!.expiredBeforeEpochMillis)
    }

    @Test fun finishingDriveTriggersDeferredCleanupWithoutDeletingPendingStageSync() = runBlocking {
        session("a", "active", MonitoringSessionStatus.ACTIVE)
        alert("old", "a", "active", 1)
        val sync = StageSyncRecordEntity("sync", "active", "a", StageSyncRecordType.STAGE3_TRANSITION,
            1, null, 1, StageSyncEligibility.ELIGIBLE, StageSyncStatus.PENDING, 0, null, null, null, null, "sync", 1)
        db.stageSyncRecordDao().insert(sync, setOf(StageSyncRecordSignEntity("sync", StoredVisibleSign.YAWNING)))
        history.setRetention("a", 1)
        assertNotNull(db.alertDao().getById("a", "old"))
        RoomMonitoringSessionRepository(db.monitoringSessionDao(), onSessionFinished = history::cleanup)
            .finish("a", "active", MonitoringSessionStatus.COMPLETED, now)
        assertNull(db.alertDao().getById("a", "old"))
        assertEquals(1, db.stageSyncRecordDao().getRecordsReadyForSync("a", 10).size)
        assertNotNull(db.calibrationDao().getActive("a"))
    }
}
