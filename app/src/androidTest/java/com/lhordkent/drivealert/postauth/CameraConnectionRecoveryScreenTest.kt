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
    @Test fun connectedCameraDisconnectRequiresConfirmationAtLargeFont() {
        var calls = 0
        compose.setContent { DriveAlertTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DeviceConnectionScreen({}, vision = DriverVisionUiState(streamState = StreamConnectionState.CONNECTED,
                    deviceConfigured = true, deviceAddress = "192.168.43.12"),
                    onDisconnect = { calls++ })
            }
        } }
        compose.onNodeWithText("Disconnect").performScrollTo().assertIsDisplayed()
        capture("recovery-large")
        compose.onNodeWithText("Disconnect").performClick()
        assertEquals(0, calls)
        compose.onNodeWithText("Return to Bluetooth").performClick()
        assertEquals(1, calls)
    }
    @Test fun savedCameraShowsBleSetupForEveryDisconnectedState() {
        val vision = mutableStateOf(DriverVisionUiState(deviceConfigured = true,
            streamState = StreamConnectionState.UNAVAILABLE, errorMessage = "Could not reach the camera. Retry on the same network."))
        compose.setContent { DriveAlertTheme { DeviceConnectionScreen({}, vision = vision.value) } }
        StreamConnectionState.entries.filter { it != StreamConnectionState.CONNECTED }.forEach { state ->
            compose.runOnIdle { vision.value = vision.value.copy(streamState = state) }
            compose.onNodeWithText("Scan for DriveAlert").assertIsDisplayed()
            compose.onNodeWithText("Camera connection").assertDoesNotExist()
        }
        capture("ble-ready")
        compose.runOnIdle { vision.value = vision.value.copy(streamState = StreamConnectionState.CONNECTED) }
        compose.onNodeWithText("Disconnect").assertIsDisplayed()
        compose.runOnIdle { vision.value = vision.value.copy(streamState = StreamConnectionState.UNAVAILABLE) }
        compose.onNodeWithText("Scan for DriveAlert").assertIsDisplayed()
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
