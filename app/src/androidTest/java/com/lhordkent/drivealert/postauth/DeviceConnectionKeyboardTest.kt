package com.lhordkent.drivealert.postauth

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.DriveAlertApplication
import com.lhordkent.drivealert.provisioning.ProvisioningStage
import com.lhordkent.drivealert.provisioning.ProvisioningState
import com.lhordkent.drivealert.provisioning.WifiProvisioningViewModel
import com.lhordkent.drivealert.ui.postauth.DeviceConnectionScreen
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeviceConnectionKeyboardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun credentialsAndConnectRemainReachableAboveKeyboard() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DriveAlertApplication
        lateinit var provisioning: WifiProvisioningViewModel
        var bleReturns = 0
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            provisioning = WifiProvisioningViewModel(application)
            // Seed the saved-network form without connecting to or changing a real device.
            val field = WifiProvisioningViewModel::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val state = field.get(provisioning) as MutableStateFlow<ProvisioningState>
            state.value = ProvisioningState(
                stage = ProvisioningStage.CONNECTED_BLE,
                credentialStatusKnown = true,
                savedNetworkSsid = "gab",
            )
        }
        compose.setContent {
            DriveAlertTheme {
                DeviceConnectionScreen(
                    onBack = {},
                    provisioningViewModel = provisioning,
                    onOpenBleSetup = {
                        bleReturns++
                        provisioning.resetAfterManualDisconnect()
                    },
                )
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { compose.activity.window.decorView.hasWindowFocus() }
        val ssid = compose.onNodeWithText("Wi-Fi or hotspot SSID")
        val password = compose.onNodeWithText("Wi-Fi password")
        val connect = compose.onNodeWithText("Replace network and connect")
        ssid.performScrollTo().performClick()
        ssid.assertIsFocused()
        compose.runOnUiThread {
            androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                .show(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(5_000) { keyboardVisible() }
        compose.waitForIdle()
        assertAboveKeyboard("Wi-Fi or hotspot SSID")
        ssid.performImeAction()
        password.assertIsFocused()
        compose.waitForIdle()
        assertAboveKeyboard("Wi-Fi password")
        connect.performScrollTo().assertIsDisplayed()
        assertAboveKeyboard("Replace network and connect")
        // Return to the password field and dismiss via Done without submitting credentials.
        password.performScrollTo().performClick().performImeAction()
        compose.waitUntil(5_000) { !keyboardVisible() }
        connect.performScrollTo().assertIsDisplayed()
        password.performScrollTo().performClick()
        compose.waitUntil(5_000) { keyboardVisible() }
        val backToBle = compose.onNodeWithText("Back to BLE setup")
        backToBle.performScrollTo().assertIsDisplayed()
        assertAboveKeyboard("Back to BLE setup")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(application.getExternalFilesDir(null), "device-connection-keyboard.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        backToBle.performClick()
        compose.waitUntil(5_000) { !keyboardVisible() }
        compose.onNodeWithText("Scan for DriveAlert").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, bleReturns) }
    }

    private fun keyboardVisible(): Boolean =
        ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun assertAboveKeyboard(text: String) {
        val nodeBottom = compose.onNodeWithText(text).fetchSemanticsNode().boundsInWindow.bottom
        var keyboardTop = 0
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            keyboardTop = view.height - (ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)
        }
        assertTrue("$text is covered by the keyboard: $nodeBottom > $keyboardTop", nodeBottom <= keyboardTop)
    }
}
