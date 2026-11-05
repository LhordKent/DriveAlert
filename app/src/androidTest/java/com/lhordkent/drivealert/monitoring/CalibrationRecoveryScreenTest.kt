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
import org.junit.Rule
import org.junit.Test

class CalibrationRecoveryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyStepExplainsTheDeviceCompletionPingBeforeCollection() {
        val state = mutableStateOf(DriverVisionUiState(calibrationInProgress = true,
            calibrationProgress = CalibrationProgress(CalibrationStatus.READY, CalibrationPhase.NEUTRAL, 0, 5_000, 0)))
        compose.setContent { DriveAlertTheme {
            CalibrationScreen(state.value, onStartVision = {}, onStartCalibration = {},
                onBeginPhase = {}, onPreviousPhase = {}, onCancel = {}, onBack = {})
        } }
        for (phase in listOf(CalibrationPhase.NEUTRAL, CalibrationPhase.EYES_CLOSED, CalibrationPhase.MOUTH_OPEN, CalibrationPhase.HEAD_DOWN)) {
            compose.runOnIdle { state.value = state.value.copy(calibrationProgress = state.value.calibrationProgress!!.copy(phase = phase)) }
            compose.onNodeWithText("The DriveAlert speaker plays a ping when this step finishes.", substring = true).assertExists()
            compose.onNodeWithText("Okay", useUnmergedTree = true).assertIsDisplayed()
            if (phase == CalibrationPhase.EYES_CLOSED) {
                compose.onNodeWithText("then open your eyes", substring = true).assertIsDisplayed()
                compose.waitForIdle()
                android.os.SystemClock.sleep(350)
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "calibration-ping-review").apply { mkdirs() }
                val file = File(directory, "eyes-closed.png")
                file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
                listOf("mkdir -p /sdcard/Download/DriveAlert-review", "cp ${file.absolutePath} /sdcard/Download/DriveAlert-review/calibration-ping.png").forEach { command ->
                    instrumentation.uiAutomation.executeShellCommand(command).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
                }
            }
        }
    }

    @Test fun calibrationExplainsPauseAndRetainsProgressWhenFreshLightingReturns() {
        val locked = DriverTrackingState(DriverTrackingStatus.LOCKED)
        val state = mutableStateOf(DriverVisionUiState(
            streamState = StreamConnectionState.CONNECTED,
            alignmentConfirmed = true,
            driverTracking = locked,
            calibrationInProgress = true,
            calibrationProgress = CalibrationProgress(CalibrationStatus.COLLECTING,
                CalibrationPhase.EYES_CLOSED, 2_000, 5_000, 20),
        ))
        compose.setContent { DriveAlertTheme {
            CalibrationScreen(state.value, onStartVision = {}, onStartCalibration = {},
                onBeginPhase = {}, onPreviousPhase = {}, onCancel = {}, onBack = {})
        } }
        compose.onNodeWithText("Waiting for lighting sample").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Paused").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2.0 / 5.0 seconds").performScrollTo().assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "calibration-recovery-screen.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { state.value = state.value.copy(
            lighting = FaceLightingAssessment(0, LightingQuality.USABLE),
            detection = DriveAlertDetectionEngine().process(0, null),
        ) }
        compose.onNodeWithText("In progress").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2.0 / 5.0 seconds").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(
            driverTracking = DriverTrackingState(DriverTrackingStatus.ACQUIRING),
            lighting = FaceLightingAssessment(), detection = null,
        ) }
        compose.onNodeWithText("Waiting for driver").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Paused").performScrollTo().assertIsDisplayed()
    }
}
