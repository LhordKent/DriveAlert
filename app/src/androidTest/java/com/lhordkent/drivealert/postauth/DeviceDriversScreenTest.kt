package com.lhordkent.drivealert.postauth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lhordkent.drivealert.data.connection.ConnectionCodeTarget
import com.lhordkent.drivealert.data.device.*
import com.lhordkent.drivealert.device.DeviceDriversState
import com.lhordkent.drivealert.ui.postauth.DeviceDriversContent
import com.lhordkent.drivealert.ui.postauth.ScrollableScreen
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceDriversScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun demoRequiresExplicitConfirmationAndCancellationDoesNotActivate() {
        val state = mutableStateOf(DeviceDriversState(overview = DeviceAccessOverview(owned = listOf(DeviceOwnership("AABBCCDDEEFF", "owner", "Family camera"))),
            selectedDeviceId = "AABBCCDDEEFF", target = ConnectionCodeTarget("driver", "Maria", "DA23456789ABCDEFGHJKLMNPQRST")))
        var confirmations = 0
        compose.setContent { DriveAlertTheme { ScrollableScreen("Subscriptions & Drivers", {}) {
            DeviceDriversContent(state.value, {}, {}, {}, {}, { confirmations++ }, { state.value = state.value.copy(target = null) }, { _, _ -> })
        } } }
        compose.onNodeWithText("Activate demo subscription?").assertIsDisplayed()
        capture("demo-confirmation")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, confirmations)
        compose.runOnIdle { state.value = state.value.copy(target = ConnectionCodeTarget("driver", "Maria", "DA23456789ABCDEFGHJKLMNPQRST")) }
        compose.onNodeWithText("Confirm Activation").performClick()
        assertEquals(1, confirmations)
    }

    @Test fun pendingInvitationAllowsAcceptOrDeclineAndExplainsOfflineUse() {
        val member = DeviceDriverMembership("AABBCCDDEEFF__driver", "AABBCCDDEEFF", "owner", "driver", "Maria", "Family camera", "PENDING", 1)
        var next: String? = null
        compose.setContent { DriveAlertTheme { ScrollableScreen("Subscriptions & Drivers", {}) {
            DeviceDriversContent(DeviceDriversState(overview = DeviceAccessOverview(incoming = listOf(member))), {}, {}, {}, {}, {}, {}, { _, status -> next = status })
        } } }
        compose.onNodeWithText("Manage driver access").assertDoesNotExist()
        compose.onNodeWithText("You are the primary driver").assertDoesNotExist()
        compose.onNodeWithText("Choose a device to manage").assertDoesNotExist()
        compose.onNodeWithText("Accept device invitation").performScrollTo().performClick()
        capture("device-invitation")
        assertEquals("ACTIVE", next)
        compose.onNodeWithText("Decline invitation").performScrollTo().performClick()
        assertEquals("DECLINED", next)
    }

    @Test fun primaryRoleAndSingleDeviceAreReadableWithoutExpandingOrSelecting() {
        val state = mutableStateOf(DeviceDriversState(overview = DeviceAccessOverview(owned = listOf(
            DeviceOwnership("AABBCCDDEEFF", "owner", "DriveAlert-E9D4"))), selectedDeviceId = "AABBCCDDEEFF"))
        compose.setContent { DriveAlertTheme { ScrollableScreen("Subscriptions & Drivers", {}) {
            DeviceDriversContent(state.value, {}, { id -> state.value = state.value.copy(selectedDeviceId = id) }, {}, {}, {}, {}, { _, _ -> })
        } } }
        compose.onNodeWithText("Manage driver access").assertDoesNotExist()
        compose.onNodeWithText("Hide driver access").assertDoesNotExist()
        compose.onNodeWithText("You are the primary driver").assertIsDisplayed()
        compose.onNodeWithText("DriveAlert-E9D4").assert(hasClickAction().not())
        compose.onNodeWithText("Choose a device to manage").assertDoesNotExist()
        capture("primary-driver")
        compose.onNodeWithText("Invite an additional driver").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = DeviceDriversState(grants = listOf(
            DeviceAccessGrant("owner", "AABBCCDDEEFF", "owner", "DriveAlert-E9D4", "OWNER", 1, 1))) }
        compose.onNodeWithText("You are the primary driver").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No registered device loaded").assertDoesNotExist()
        compose.onNodeWithText("Find Driver").assertDoesNotExist()
        compose.runOnIdle { state.value = DeviceDriversState(overview = DeviceAccessOverview(owned = listOf(
            DeviceOwnership("AABBCCDDEEFF", "owner", "DriveAlert-E9D4"),
            DeviceOwnership("112233445566", "owner", "Work camera"))), selectedDeviceId = "AABBCCDDEEFF") }
        compose.onNodeWithText("Choose a device to manage").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Work camera").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("112233445566", state.value.selectedDeviceId) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Semantics can become idle before the native window compositor presents a dialog.
        android.os.SystemClock.sleep(350)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = java.io.File(context.getExternalFilesDir(null), "device-subscription-review").apply { mkdirs() }
        val file = java.io.File(directory, "$name.png")
        file.outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        // Preserve review artifacts across Gradle's target-package uninstall.
        listOf("mkdir -p /sdcard/Download/DriveAlert-review", "cp ${file.absolutePath} /sdcard/Download/DriveAlert-review/$name-capture.png").forEach { command ->
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
        }
    }
}
