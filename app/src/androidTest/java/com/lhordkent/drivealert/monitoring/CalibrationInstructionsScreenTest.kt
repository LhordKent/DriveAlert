package com.lhordkent.drivealert.monitoring

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.DriveAlertDetectionEngine
import com.lhordkent.drivealert.detection.face.DriverTrackingState
import com.lhordkent.drivealert.detection.face.DriverTrackingStatus
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.detection.model.CalibrationPhase
import com.lhordkent.drivealert.detection.model.CalibrationProgress
import com.lhordkent.drivealert.detection.model.CalibrationStatus
import com.lhordkent.drivealert.ui.postauth.CalibrationScreen
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CalibrationInstructionsScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun vision(phase: CalibrationPhase, status: CalibrationStatus) = DriverVisionUiState(
        streamState = StreamConnectionState.CONNECTED, alignmentConfirmed = true,
        driverTracking = DriverTrackingState(DriverTrackingStatus.LOCKED),
        lighting = FaceLightingAssessment(0, LightingQuality.USABLE),
        detection = DriveAlertDetectionEngine().process(0, null),
        calibrationInProgress = true,
        calibrationProgress = CalibrationProgress(status, phase, 0, 5_000, 0),
    )

    @Test fun instructionsRequireOkayBeforeCollectionAndExplainNormalDrivingPosture() {
        val state = mutableStateOf(vision(CalibrationPhase.NEUTRAL, CalibrationStatus.READY))
        val began = AtomicInteger()
        compose.setContent { DriveAlertTheme {
            CalibrationScreen(state.value, onStartVision = {}, onStartCalibration = {},
                onBeginPhase = { acknowledged ->
                    assertEquals(state.value.calibrationProgress?.revision, acknowledged.revision)
                    began.incrementAndGet()
                    state.value = state.value.copy(calibrationProgress = acknowledged.copy(status = CalibrationStatus.COLLECTING))
                }, onPreviousPhase = {}, onCancel = {}, onBack = {})
        } }
        compose.onNodeWithText("Step 1 of 4: Normal driving posture").assertIsDisplayed()
        compose.onNode(hasText("Sit in your normal driving posture and look ahead", substring = true) and
            hasAnyAncestor(isDialog())).assertIsDisplayed()
        assertEquals(0, began.get())
        capture("instructions")
        compose.onNodeWithText("Okay").performClick()
        compose.waitUntil(6_000) { began.get() == 1 }
        compose.onNodeWithText("Go Back to Previous Step").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Redo All Steps").performScrollTo().assertIsEnabled()
        capture("controls")
    }

    @Test fun previousStepNavigatesBackAndRequiresNewAcknowledgment() {
        val state = mutableStateOf(vision(CalibrationPhase.MOUTH_OPEN, CalibrationStatus.COLLECTING))
        val began = AtomicInteger()
        compose.setContent { DriveAlertTheme {
            CalibrationScreen(state.value, onStartVision = {}, onStartCalibration = {},
                onBeginPhase = { began.incrementAndGet() },
                onPreviousPhase = {
                    val old = state.value.calibrationProgress!!
                    state.value = state.value.copy(calibrationProgress = old.copy(
                        phase = CalibrationPhase.values()[old.phase.ordinal - 1],
                        status = CalibrationStatus.READY, revision = old.revision + 1))
                }, onCancel = {}, onBack = {})
        } }
        compose.onNodeWithText("Go Back to Previous Step").performScrollTo().performClick()
        compose.onNodeWithText("Step 2 of 4: Eyes closed").assertIsDisplayed()
        assertEquals(0, began.get())
        compose.onNodeWithText("Previous Step").performClick()
        compose.onNodeWithText("Step 1 of 4: Normal driving posture").assertIsDisplayed()
        assertEquals(0, began.get())
    }

    @Test fun redoAllNeedsConfirmationAndRenewsAcknowledgmentEvenOnFirstStep() {
        val state = mutableStateOf(vision(CalibrationPhase.NEUTRAL, CalibrationStatus.COLLECTING))
        val restarted = AtomicInteger()
        val began = AtomicInteger()
        compose.setContent { DriveAlertTheme {
            CalibrationScreen(state.value, onStartVision = {},
                onStartCalibration = {
                    restarted.incrementAndGet()
                    state.value = state.value.copy(calibrationProgress = state.value.calibrationProgress!!.copy(
                        phase = CalibrationPhase.NEUTRAL, status = CalibrationStatus.READY, revision = 1))
                }, onBeginPhase = { began.incrementAndGet() }, onPreviousPhase = {}, onCancel = {}, onBack = {})
        } }
        compose.onNodeWithText("Redo All Steps").performScrollTo().performClick()
        compose.onNodeWithText("Redo all calibration steps?").assertIsDisplayed()
        assertEquals(0, restarted.get())
        compose.onNodeWithText("Keep Current Progress").performClick()
        assertEquals(0, restarted.get())
        compose.onNodeWithText("Redo All Steps").performClick()
        compose.onNodeWithText("Okay").performClick()
        compose.onNodeWithText("Step 1 of 4: Normal driving posture").assertIsDisplayed()
        assertEquals(1, restarted.get())
        assertEquals(0, began.get())
        compose.onNodeWithText("Okay").performClick()
        compose.waitUntil(6_000) { began.get() == 1 }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "calibration-navigation-$name.png").outputStream().use {
            val node = if (name == "instructions") compose.onNode(isDialog()) else compose.onRoot()
            node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
