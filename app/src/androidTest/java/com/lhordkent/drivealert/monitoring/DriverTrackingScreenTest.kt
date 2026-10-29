package com.lhordkent.drivealert.monitoring

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.face.*
import com.lhordkent.drivealert.detection.frame.SharedBitmapFrame
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.postauth.MonitoringScenario
import com.lhordkent.drivealert.ui.postauth.CameraAlignmentScreen
import com.lhordkent.drivealert.ui.postauth.MonitoringPreviewScreen
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DriverTrackingScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun preview() = MutableStateFlow<SharedBitmapFrame?>(SharedBitmapFrame.create(
        Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.DKGRAY) },
    ))
    private fun locked() = DriverTrackingState(DriverTrackingStatus.LOCKED, DriverRegion(0.35, 0.25, 0.65, 0.65), 1, 640, 480)

    @Test fun alignmentRequiresLockAndRegionControlsAreDisabledDuringMonitoring() {
        val state = mutableStateOf(DriverVisionUiState(streamState = StreamConnectionState.CONNECTED,
            driverTracking = DriverTrackingState(DriverTrackingStatus.ACQUIRING)))
        var confirmed = false
        val frames = preview()
        compose.setContent { DriveAlertTheme {
            CameraAlignmentScreen(state.value, frames, {}, { confirmed = true },
                onDriverRegionChange = { state.value = state.value.copy(driverRegion = it) }, onBack = {})
        } }
        capture("alignment-top")
        compose.onNodeWithText("Confirm Alignment").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(driverTracking = locked()) }
        compose.onNodeWithText("Confirm Alignment").assertIsEnabled().performClick()
        assertTrue(confirmed)
        compose.onNodeWithContentDescription("Area width").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(0.5, state.value.driverRegion.width, 0.001)
        capture("alignment-controls")
        compose.runOnIdle { state.value = state.value.copy(monitoring = ActiveMonitoringState(isActive = true)) }
        compose.onNodeWithContentDescription("Area width").assertIsNotEnabled()
        compose.onNodeWithText("Confirm Alignment").performScrollTo().assertIsNotEnabled()
        frames.value?.release()
    }

    @Test fun ambiguityPausesMonitoringAndDoesNotEraseWarningHistory() {
        val state = DriverVisionUiState(streamState = StreamConnectionState.CONNECTED, alignmentConfirmed = true,
            driverTracking = DriverTrackingState(DriverTrackingStatus.AMBIGUOUS),
            monitoring = ActiveMonitoringState(isActive = true, confirmedEventCount = 2))
        val frames = preview()
        compose.setContent { DriveAlertTheme {
            MonitoringPreviewScreen(MonitoringScenario.NORMAL, vision = state, previewFrames = frames,
                monitoring = state.monitoring, onExit = {},
                cameraVisibility = cameraVisibilityPresentation(state.streamState, MonitoringScenario.NORMAL, true, false,
                    driverTracking = state.driverTracking))
        } }
        compose.onNodeWithText("Monitoring paused").assertIsDisplayed()
        capture("monitoring-ambiguous")
        compose.onNodeWithText("Confirmed events").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        frames.value?.release()
    }

    @Test fun multiFaceProcessorLoadsAndRejectsBlankFrame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val finished = java.util.concurrent.CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val selected = java.util.concurrent.atomic.AtomicReference<DriverTrackingState?>()
        val resultRevision = java.util.concurrent.atomic.AtomicLong()
        val released = java.util.concurrent.CountDownLatch(1)
        val processor = MediaPipeFaceLandmarkerProcessor(context,
            onObservation = { _, observation, attributes, lighting, tracking, revision ->
                try {
                    assertNull(observation)
                    assertNull(attributes.probabilities)
                    assertNull(lighting)
                    selected.set(tracking)
                    resultRevision.set(revision)
                } catch (error: Throwable) { failure.set(error) }
                finally { finished.countDown() }
            },
            onError = { failure.set(it); finished.countDown() },
        )
        try {
            val revision = processor.configure(DriverRegion.DEFAULT)
            assertTrue(processor.submitLazy(640, 480, 100,
                { Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888) }, { released.countDown() }))
            assertTrue(finished.await(30, java.util.concurrent.TimeUnit.SECONDS))
            failure.get()?.let { throw it }
            assertEquals(DriverTrackingStatus.LOST, selected.get()?.status)
            assertEquals(revision, resultRevision.get())
            assertTrue(released.await(5, java.util.concurrent.TimeUnit.SECONDS))
        } finally { processor.close() }
    }

    @Test fun savedRegionIsScopedAndCanBeInvalidated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DriverRegionStore(context)
        val value = SavedDriverRegion(DriverRegion.DEFAULT, 640, 480)
        val key = "instrumentation-camera-region"
        try {
            store.save(key, value)
            assertEquals(value, DriverRegionStore(context).load(key))
            assertNull(store.load("another-camera-region"))
            store.remove(key)
            assertNull(store.load(key))
        } finally { store.remove(key) }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "driver-lock-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
