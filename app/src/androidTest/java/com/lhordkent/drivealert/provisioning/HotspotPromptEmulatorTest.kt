package com.lhordkent.drivealert.provisioning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.ui.postauth.ScrollableScreen
import com.lhordkent.drivealert.ui.postauth.WifiProvisioningPanel
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class HotspotPromptEmulatorTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var provisioning: WifiProvisioningViewModel
    private lateinit var hotspotContext: HotspotContext
    private val dispatch = WifiProvisioningViewModel::class.java
        .getDeclaredMethod("dispatch", ProvisioningEvent::class.java).apply { isAccessible = true }

    @Test
    fun confirmedOffPromptsOnlyAfterBluetoothSetupAndCanBeDismissed() {
        render(initialState = 11)
        prompt().assertDoesNotExist()
        emit(ProvisioningEvent.Connecting("emulated-camera"), ProvisioningEvent.BleReady)
        prompt().assertDoesNotExist()
        emit(ProvisioningEvent.CredentialStatus("DriveAlert emulated network"))
        prompt().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        prompt().assertDoesNotExist()
        compose.onNodeWithText("Connect using saved network").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun confirmedOnSkipsPopupAndTurningOnClosesAnOpenPopup() {
        render(initialState = 13)
        emit(
            ProvisioningEvent.Connecting("emulated-camera"),
            ProvisioningEvent.BleReady,
            ProvisioningEvent.CredentialStatus("DriveAlert emulated network"),
        )
        prompt().assertDoesNotExist()
        compose.onNodeWithText("Connect using saved network").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { hotspotContext.reportState(11) }
        prompt().assertIsDisplayed()
        compose.runOnIdle { hotspotContext.reportState(13) }
        prompt().assertDoesNotExist()
    }

    @Test
    fun missingHotspotStateDoesNotShowPopup() {
        render(initialState = null)
        emit(
            ProvisioningEvent.Connecting("emulated-camera"),
            ProvisioningEvent.BleReady,
            ProvisioningEvent.CredentialStatus("DriveAlert emulated network"),
        )
        prompt().assertDoesNotExist()
        compose.onNodeWithText("Connect using saved network").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun realEmulatorHotspotBroadcastControlsThePrompt() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realHotspot") == "true")
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val originallyOn = isEmulatorHotspotOn()
        render(initialState = null, useSystemHotspot = true)
        prompt().assertDoesNotExist()
        try {
            setEmulatorHotspot(true)
            setEmulatorHotspot(false)
            prompt().assertDoesNotExist()
            emit(
                ProvisioningEvent.Connecting("emulated-camera"),
                ProvisioningEvent.BleReady,
                ProvisioningEvent.CredentialStatus("DriveAlert emulated network"),
            )
            prompt().assertIsDisplayed()

            setEmulatorHotspot(true)
            prompt().assertDoesNotExist()
            emit(ProvisioningEvent.Retry)
            compose.waitForIdle()
            emit(
                ProvisioningEvent.Connecting("emulated-camera"),
                ProvisioningEvent.BleReady,
                ProvisioningEvent.CredentialStatus("DriveAlert emulated network"),
            )
            prompt().assertDoesNotExist()
            compose.onNodeWithText("Connect using saved network").performScrollTo().assertIsDisplayed()
        } finally {
            setEmulatorHotspot(originallyOn)
        }
    }

    private fun isEmulatorHotspotOn(): Boolean =
        shell("dumpsys tethering").lineSequence().any { it.trim().matches(Regex("(?:wlan|softap)\\d+ - TetheredState.*")) }

    private fun setEmulatorHotspot(enabled: Boolean) {
        if (isEmulatorHotspotOn() == enabled) return
        shell("am start --activity-clear-top -a android.settings.TETHER_SETTINGS")
        val hotspotRow = awaitSettingsNode { it.text?.toString()?.replace('\u2011', '-') == "Wi-Fi hotspot" }
        tapSettingsNode(hotspotRow)
        val switchLabel = awaitSettingsNode {
            it.text?.toString()?.replace('\u2011', '-') == "Use Wi-Fi hotspot"
        }
        SystemClock.sleep(450)
        val hotspotSwitch = awaitSettingsNode {
            it.className == "android.widget.Switch" && it.parent == switchLabel.parent
        }
        check(hotspotSwitch.isChecked != enabled) { "The hotspot switch disagrees with Android tethering state" }
        val actionTarget = generateSequence(hotspotSwitch) { it.parent }.first { it.isClickable }
        check(actionTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Android Settings rejected the hotspot tap" }
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline && isEmulatorHotspotOn() != enabled) {
            SystemClock.sleep(250)
        }
        check(isEmulatorHotspotOn() == enabled) { "Emulator hotspot did not reach state $enabled" }
        shell("input keyevent KEYCODE_BACK")
        shell("input keyevent KEYCODE_BACK")
        compose.waitForIdle()
    }

    private fun awaitSettingsNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (predicate(node)) return node
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                find(child)?.let { return it }
            }
            return null
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            automation.rootInActiveWindow?.let { root -> find(root)?.let { return it } }
            SystemClock.sleep(200)
        }
        error("Hotspot control did not appear in Android Settings")
    }

    private fun tapSettingsNode(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    private fun render(initialState: Int?, useSystemHotspot: Boolean = false) {
        compose.setContent {
            val mainContext = LocalContext.current
            val observedContext = remember(mainContext) { HotspotContext(mainContext, initialState) }
            val model: WifiProvisioningViewModel = viewModel()
            SideEffect {
                provisioning = model
                hotspotContext = observedContext
            }
            DriveAlertTheme {
                ScrollableScreen("Device Connection", onBack = {}) {
                    CompositionLocalProvider(LocalContext provides if (useSystemHotspot) mainContext else observedContext) {
                        WifiProvisioningPanel(provisioningViewModel = model)
                    }
                }
            }
        }
    }

    private fun emit(vararg events: ProvisioningEvent) {
        // Exercise the real setup reducer and UI without requiring a physical camera.
        compose.runOnIdle { events.forEach { dispatch.invoke(provisioning, it) } }
    }

    private fun prompt() = compose.onNodeWithText("Turn on your hotspot")

    private class HotspotContext(base: Context, private val initialState: Int?) : ContextWrapper(base) {
        private var hotspotReceiver: BroadcastReceiver? = null

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter,
            broadcastPermission: String?,
            scheduler: Handler?,
            flags: Int,
        ): Intent? = if (filter.hasAction(PhoneHotspotSignal.ACTION)) {
            hotspotReceiver = receiver
            initialState?.let(::stateIntent)
        } else super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags)

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter,
            broadcastPermission: String?,
            scheduler: Handler?,
        ): Intent? = if (filter.hasAction(PhoneHotspotSignal.ACTION)) {
            hotspotReceiver = receiver
            initialState?.let(::stateIntent)
        } else super.registerReceiver(receiver, filter, broadcastPermission, scheduler)

        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            if (receiver === hotspotReceiver) hotspotReceiver = null
            else super.unregisterReceiver(receiver)
        }

        fun reportState(state: Int) {
            checkNotNull(hotspotReceiver).onReceive(this, stateIntent(state))
        }

        private fun stateIntent(state: Int) = Intent(PhoneHotspotSignal.ACTION)
            .putExtra("wifi_state", state)
            .putExtra("android.net.wifi.extra.WIFI_AP_MODE", 1)
    }
}
