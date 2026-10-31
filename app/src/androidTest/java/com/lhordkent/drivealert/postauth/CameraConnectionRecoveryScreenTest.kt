package com.lhordkent.drivealert.postauth

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.ui.postauth.*
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import java.io.File
import org.junit.*
import org.junit.Assert.*

class CameraConnectionRecoveryScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun liveConnectionOverridesFreshBleStateAndKeepsDisconnectVisible() {
        compose.setContent { DriveAlertTheme { DeviceConnectionScreen({}, vision = DriverVisionUiState(
            streamState = StreamConnectionState.CONNECTED, deviceConfigured = true,
            deviceName = "drivealert-test", deviceAddress = "192.168.43.12")) } }
        compose.onNodeWithText("CONNECTED").assertIsDisplayed()
        compose.onNodeWithText("Disconnect").assertIsDisplayed()
        compose.onNodeWithText("Scan for DriveAlert").assertDoesNotExist()
        compose.onNodeWithText("Tap to reconnect").assertDoesNotExist()
        capture("connected")
    }
    @Test fun videoFailureStillOffersControlRecoveryAndRequiresConfirmationAtLargeFont() {
        var calls = 0
        compose.setContent { DriveAlertTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DeviceConnectionScreen({}, vision = DriverVisionUiState(streamState = StreamConnectionState.UNAVAILABLE,
                    deviceConfigured = true, deviceAddress = "192.168.43.12", errorMessage = "Camera stream unavailable."),
                    onReturnToBleSetup = { calls++ })
            }
        } }
        compose.onNodeWithText("Go back to Bluetooth (BLE) setup").performScrollTo().assertIsDisplayed()
        capture("recovery-large")
        compose.onNodeWithText("Go back to Bluetooth (BLE) setup").performClick()
        assertEquals(0, calls)
        compose.onNodeWithText("Return to Bluetooth").performClick()
        assertEquals(1, calls)
    }
    @Test fun confirmedReturnExposesBleSetupAndFailureDoesNotPretendSuccess() {
        val vision = mutableStateOf(DriverVisionUiState(deviceConfigured = true,
            streamState = StreamConnectionState.UNAVAILABLE, errorMessage = "Could not reach the camera. Retry on the same network."))
        compose.setContent { DriveAlertTheme { DeviceConnectionScreen({}, vision = vision.value,
            onOpenBleSetup = { vision.value = vision.value.copy(bleSetupRequested = true, errorMessage = null) }) } }
        compose.onNodeWithText("Could not reach the camera.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Scan for DriveAlert").assertDoesNotExist()
        compose.onNodeWithText("Open Bluetooth setup").performScrollTo().performClick()
        compose.onNodeWithText("Open Bluetooth setup?").assertIsDisplayed()
        compose.onAllNodesWithText("Open Bluetooth setup").onLast().performClick()
        compose.onNodeWithText("Scan for DriveAlert").assertIsDisplayed()
        compose.onNodeWithText("Camera connection").assertDoesNotExist()
        capture("ble-ready")
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.cacheDir, "camera-recovery-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
