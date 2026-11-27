package com.lhordkent.drivealert.postauth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lhordkent.drivealert.data.connection.TrustedContactEmailMatch
import com.lhordkent.drivealert.data.device.*
import com.lhordkent.drivealert.device.DeviceDriversState
import com.lhordkent.drivealert.device.DeviceDriverInviteTarget
import com.lhordkent.drivealert.ui.postauth.DeviceDriversContent
import com.lhordkent.drivealert.ui.postauth.AdditionalDriverConnectionNotice
import com.lhordkent.drivealert.ui.postauth.ScrollableScreen
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceDriversScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun demoRequiresExplicitConfirmationAndCancellationDoesNotActivate() {
        val reply = DeviceDriverInviteTarget.ByEmail(TrustedContactEmailMatch("Maria", "maria@example.test", "match"))
        val state = mutableStateOf(DeviceDriversState(overview = DeviceAccessOverview(owned = listOf(DeviceOwnership("AABBCCDDEEFF", "owner", "Family camera"))),
            selectedDeviceId = "AABBCCDDEEFF"))
        var confirmations = 0
        var searches = 0
        compose.setContent { DriveAlertTheme { ScrollableScreen("Subscriptions & Drivers", {}) {
            DeviceDriversContent(
                state.value, {}, {},
                { state.value = state.value.copy(inviteInput = it) },
                { searches++; state.value = state.value.copy(target = reply) },
                { confirmations++ },
                { state.value = state.value.copy(target = null) },
                { _, _ -> },
                { state.value = state.value.copy(inviteByEmail = it, inviteInput = "") },
            )
        } } }
        compose.onNodeWithText("Use a connection code").performScrollTo().performClick()
        compose.onNodeWithText("Driver connection code").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Search by email").performScrollTo().performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)).performScrollTo().performTextInput("maria@example.test")
        compose.onNodeWithText("Search", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Add this driver?").assertIsDisplayed()
        compose.onNodeWithText("Maria").assertIsDisplayed()
        compose.onAllNodesWithText("maria@example.test").onLast().assertIsDisplayed()
        assertEquals(1, searches)
        assertEquals(0, confirmations)
        capture("demo-confirmation")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, confirmations)
        compose.onNodeWithText("Search", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Add driver").performClick()
        assertEquals(1, confirmations)
    }

    @Test fun receiverSeesAccessWithoutAnAcceptanceStepAndCanLeave() {
        val member = DeviceDriverMembership("AABBCCDDEEFF__driver", "AABBCCDDEEFF", "owner", "driver", "Maria", "Family camera", "PENDING", 1)
        val state = mutableStateOf(DeviceDriversState(overview = DeviceAccessOverview(incoming = listOf(member))))
        var next: String? = null
        compose.setContent { DriveAlertTheme { ScrollableScreen("Subscriptions & Drivers", {}) {
            DeviceDriversContent(state.value, {}, {}, {}, {}, {}, {}, { _, status ->
                next = status
                state.value = state.value.copy(overview = DeviceAccessOverview(incoming = listOf(member.copy(status = status, revision = 3))))
            })
        } } }
        compose.onNodeWithText("Manage driver access").assertDoesNotExist()
        compose.onNodeWithText("You are the primary driver").assertDoesNotExist()
        compose.onNodeWithText("Choose a device to manage").assertDoesNotExist()
        compose.onNodeWithText("Ask Primary driver to activate your access.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Accept device invitation").assertDoesNotExist()
        compose.onNodeWithText("Decline invitation").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(overview = DeviceAccessOverview(incoming = listOf(member.copy(status = "ACTIVE", revision = 2)))) }
        compose.onNodeWithText("Access saved for offline use.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Leave device").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals("LEFT", next)
        compose.onNodeWithText("Family camera", substring = true).assertDoesNotExist()
        compose.onNodeWithText("No shared devices loaded.", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun removedDriversDisappearAndTerminalAccessRecordsDoNotRender() {
        val deviceId = "AABBCCDDEEFF"
        val active = DeviceDriverMembership("${deviceId}__driver", deviceId, "owner", "driver", "Active Driver", "Family camera", "ACTIVE", 2)
        val ended = listOf("REMOVED", "CANCELLED", "DECLINED", "LEFT").map { status ->
            active.copy(id = "${deviceId}__$status", driverDisplayName = "Prior $status", status = status)
        }
        val state = mutableStateOf(DeviceDriversState(
            overview = DeviceAccessOverview(owned = listOf(DeviceOwnership(deviceId, "owner", "Family camera")), managed = listOf(active) + ended),
            selectedDeviceId = deviceId,
        ))
        val numbers = mutableStateOf(emptyMap<String, String>())
        compose.setContent { DriveAlertTheme { ScrollableScreen("Subscriptions & Drivers", {}) {
            DeviceDriversContent(state.value, {}, {}, {}, {}, {}, {}, { id, status ->
                val members = state.value.overview.managed.map { if (it.id == id) it.copy(status = status) else it }
                state.value = state.value.copy(overview = state.value.overview.copy(managed = members))
            }, phoneNumbers = numbers.value)
        } } }
        ended.forEach { compose.onNodeWithText(it.driverDisplayName).assertDoesNotExist() }
        compose.onNodeWithText("Active Driver").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Phone number unavailable").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { numbers.value = mapOf(active.id to "09123456789") }
        compose.onNodeWithText("09123456789").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Active · Demo subscription").assertDoesNotExist()
        capture("additional-driver-phone")
        compose.onNodeWithText("Remove driver").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Active Driver").assertDoesNotExist()
        compose.onNodeWithText("No additional drivers yet.").performScrollTo().assertIsDisplayed()
    }

    @Test fun firstBluetoothNoticeCanBeAcknowledgedWithoutAnInvitationDecision() {
        val show = mutableStateOf(true)
        compose.setContent {
            DriveAlertTheme { if (show.value) AdditionalDriverConnectionNotice { show.value = false } }
        }
        compose.onNodeWithText("The primary driver added you to this device.").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("The primary driver added you to this device.").assertDoesNotExist()
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
        compose.onNodeWithText("Add an additional driver").performScrollTo().assertIsDisplayed()
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
