package com.lhordkent.drivealert.postauth

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import com.lhordkent.drivealert.data.local.DriveAlertDatabase
import com.lhordkent.drivealert.data.monitoringlog.*
import com.lhordkent.drivealert.ui.postauth.*
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class MonitoringLogScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: DriveAlertDatabase
    private lateinit var repository: MonitoringLogRepository
    private lateinit var writer: MonitoringLogWriter
    private lateinit var scope: CoroutineScope
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, DriveAlertDatabase::class.java).build()
        repository = MonitoringLogRepository(db)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        writer = MonitoringLogWriter(repository, scope)
    }
    @After fun close() { scope.cancel(); db.close() }
    private fun screen(large: Boolean = false) {
        compose.setContent { DriveAlertTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.3f else 1f)) {
                MonitoringLogsScreen("driver", repository, writer, {})
            }
        } }
    }
    private fun seed(completed: Boolean) = runBlocking {
        repository.begin("driver", "one", 1793401200000, false)
        repository.append(MonitoringLogEvent(eventId = "sign", sessionId = "one", driverUserId = "driver", occurredAt = 1793401202300,
            kind = "SIGN", sign = "PROLONGED_EYE_CLOSURE", measuredValue = .18, threshold = .25, durationMs = 2300, requiredDurationMs = 2000))
        repository.append(MonitoringLogEvent(eventId = "alarm", sessionId = "one", driverUserId = "driver", occurredAt = 1793401202310,
            kind = "ALARM", stage = 1, repeated = true, result = "ACCEPTED", responseAt = 1793401202390))
        if (completed) repository.end("driver", "one", 1793401260000, "COMPLETED")
    }
    private fun open() {
        compose.waitUntil(5000) { compose.onAllNodesWithText("Current session", substring = true).fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Completed ·", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val nodes = compose.onAllNodesWithText("Current session", substring = true)
        if (nodes.fetchSemanticsNodes().isNotEmpty()) nodes[0].performClick() else compose.onNodeWithText("Completed ·", substring = true).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Timeline").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun emptyStateAndDriverOnlySettingsEntry() {
        screen()
        compose.waitUntil(5000) { compose.onAllNodesWithText("No monitoring sessions yet").fetchSemanticsNodes().isNotEmpty() }
        capture("empty")
    }
    @Test fun onlyDriverSettingsShowsMonitoringLogs() {
        val view = mutableStateOf(UserView.DRIVER)
        compose.setContent { DriveAlertTheme { ScrollableScreen("Settings", {}) {
            SettingsScreen(view.value, {}, {}, {}, {}, {}, {}, {})
        } } }
        compose.onNodeWithText("Monitoring Logs").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { view.value = UserView.TRUSTED_CONTACT }
        compose.onNodeWithText("Monitoring Logs").assertDoesNotExist()
    }
    @Test fun completedTimelineShowsValuesAndDeletionRequiresConfirmation() {
        seed(true); screen(); open()
        compose.onNodeWithText("EAR 0.180", substring = true).assertIsDisplayed()
        capture("phone")
        compose.onNodeWithText("Delete session log").performClick()
        compose.onNodeWithText("Delete session log?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        runBlocking { assertNotNull(repository.dao.get("driver", "one")) }
        compose.onNodeWithText("Delete session log").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("No monitoring sessions yet").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun activeSessionUpdatesLiveAndHasNoDeleteActionAtLargeFont() {
        seed(false); screen(true); open()
        compose.onNodeWithText("Delete session log").assertDoesNotExist()
        capture("phone-large")
        runBlocking { repository.append(MonitoringLogEvent(eventId = "new", sessionId = "one", driverUserId = "driver", occurredAt = 1793401210000, kind = "ALARM", stage = 2, result = "FAILED")) }
        compose.onNodeWithText("Jump to latest").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Request failed or timed out", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Request failed or timed out", substring = true).assertIsDisplayed()
    }
    @Test fun customRetentionValidatesAndPreviewsDeletionBeforeApplying() {
        seed(true); screen()
        compose.onNodeWithText("Automatic deletion").performClick()
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithText("Number of days").performScrollTo().performTextInput("0")
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Number of days").performScrollTo().performTextReplacement("3651")
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Number of days").performScrollTo().performTextReplacement("14")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Apply automatic deletion?").fetchSemanticsNodes().isNotEmpty() }
        capture("retention")
        runBlocking { assertNull(repository.dao.preference("driver").first()) }
        compose.onNodeWithText("Save and apply").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Apply automatic deletion?").fetchSemanticsNodes().isEmpty() }
        runBlocking { assertEquals(14, repository.dao.preference("driver").first()!!.retentionDays) }
    }
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.waitForIdle()
        // Window/dialog animations run outside Compose's test clock.
        android.os.SystemClock.sleep(400)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.cacheDir, "monitoring-logs-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
