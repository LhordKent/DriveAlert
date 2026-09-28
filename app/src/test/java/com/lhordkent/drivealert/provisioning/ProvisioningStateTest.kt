package com.lhordkent.drivealert.provisioning

import android.Manifest
import com.lhordkent.drivealert.ui.postauth.wifiCredentialFieldsAvailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningStateTest {
    @Test
    fun `scan and discovery produce selectable device state`() {
        var state = ProvisioningReducer.reduce(ProvisioningState(), ProvisioningEvent.ScanStarted)
        assertEquals(ProvisioningStage.SCANNING, state.stage)
        state = ProvisioningReducer.reduce(
            state,
            ProvisioningEvent.DeviceDiscovered(ProvisioningDevice("device-1", "DriveAlert-A1B2", -42)),
        )
        assertEquals(ProvisioningStage.DEVICE_FOUND, state.stage)
        assertEquals("device-1", state.devices.single().id)
    }

    @Test
    fun `BLE connection and credential submission states are explicit`() {
        var state = ProvisioningState(devices = listOf(ProvisioningDevice("device-1", "DriveAlert-A1B2", -42)))
        state = ProvisioningReducer.reduce(state, ProvisioningEvent.Connecting("device-1"))
        assertEquals(ProvisioningStage.CONNECTING_BLE, state.stage)
        state = ProvisioningReducer.reduce(state, ProvisioningEvent.Pairing)
        assertEquals(ProvisioningStage.PAIRING, state.stage)
        state = ProvisioningReducer.reduce(state, ProvisioningEvent.BleReady)
        assertEquals(ProvisioningStage.CONNECTED_BLE, state.stage)
        assertFalse(state.credentialStatusKnown)
        state = ProvisioningReducer.reduce(state, ProvisioningEvent.CredentialStatus("maming "))
        assertTrue(state.credentialStatusKnown)
        assertEquals("maming ", state.savedNetworkSsid)
        state = ProvisioningReducer.reduce(state, ProvisioningEvent.CredentialsSending)
        assertEquals(ProvisioningStage.SENDING_CREDENTIALS, state.stage)
        state = ProvisioningReducer.reduce(state, ProvisioningEvent.WifiConnecting)
        assertEquals(ProvisioningStage.CONNECTING_WIFI, state.stage)
    }

    @Test
    fun `BLE readiness never transitions directly to Wi-Fi`() {
        val ready = ProvisioningReducer.reduce(ProvisioningState(), ProvisioningEvent.BleReady)
        assertEquals(ProvisioningStage.CONNECTED_BLE, ready.stage)
        val saved = ProvisioningReducer.reduce(ready, ProvisioningEvent.CredentialStatus("maming "))
        assertEquals(ProvisioningStage.CONNECTED_BLE, saved.stage)
        assertFalse(saved.stage == ProvisioningStage.CONNECTING_WIFI)
    }

    @Test
    fun `saved ESP32 network never hides credential replacement fields`() {
        val state = ProvisioningState(
            stage = ProvisioningStage.CONNECTED_BLE,
            credentialStatusKnown = true,
            savedNetworkSsid = "PreviousOwnerHotspot",
        )

        assertTrue(wifiCredentialFieldsAvailable(state))
    }

    @Test
    fun `success records assigned endpoint`() {
        val state = ProvisioningReducer.reduce(
            ProvisioningState(stage = ProvisioningStage.CONNECTING_WIFI),
            ProvisioningEvent.Provisioned("192.168.4.20", "drivealert-a1b2", "device-1"),
        )
        assertEquals(ProvisioningStage.PROVISIONED, state.stage)
        assertEquals("192.168.4.20", state.assignedIp)
        assertEquals("drivealert-a1b2", state.hostname)
        assertEquals("device-1", state.provisionedDeviceId)
    }

    @Test
    fun `failure can retry from a clean state`() {
        val failed = ProvisioningReducer.reduce(
            ProvisioningState(stage = ProvisioningStage.CONNECTING_WIFI),
            ProvisioningEvent.Failed("Connection failed."),
        )
        assertEquals(ProvisioningStage.FAILED, failed.stage)
        val retried = ProvisioningReducer.reduce(failed, ProvisioningEvent.Retry)
        assertEquals(ProvisioningStage.IDLE, retried.stage)
        assertTrue(retried.devices.isEmpty())
    }

    @Test
    fun `credential representation always redacts password`() {
        val secret = "not-a-real-password"
        val credentials = WifiCredentials.create("TestNetwork", secret).getOrThrow()
        assertFalse(credentials.toString().contains(secret))
        assertTrue(credentials.toString().contains("<redacted>"))
    }

    @Test
    fun `credential validation enforces Wi-Fi field lengths`() {
        assertTrue(WifiCredentials.create("Network", "12345678").isSuccess)
        assertTrue(WifiCredentials.create("OpenNetwork", "").isSuccess)
        assertTrue(WifiCredentials.create("", "12345678").isFailure)
        assertTrue(WifiCredentials.create("Network", "short").isFailure)
    }

    @Test
    fun `credential validation preserves significant SSID whitespace`() {
        val credentials = WifiCredentials.create("maming ", "12345678").getOrThrow()
        assertEquals("maming ", credentials.ssid)
        assertEquals(7, credentials.ssid.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `permission policy separates Android 12 from legacy scanning`() {
        assertEquals(
            setOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            BlePermissionPolicy.runtimePermissions(31).toSet(),
        )
        assertEquals(
            setOf(Manifest.permission.ACCESS_FINE_LOCATION),
            BlePermissionPolicy.runtimePermissions(30).toSet(),
        )
    }
}
