package com.lhordkent.drivealert.ui.postauth

import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.provisioning.PhoneHotspotSignal
import com.lhordkent.drivealert.provisioning.PhoneHotspotState
import com.lhordkent.drivealert.provisioning.ProvisioningStage
import com.lhordkent.drivealert.provisioning.ProvisioningState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiProvisioningPanelTest {
    @Test
    fun `hotspot prompt requires completed BLE setup and confirmed hotspot off`() {
        val ready = ProvisioningState(stage = ProvisioningStage.CONNECTED_BLE, credentialStatusKnown = true)

        assertTrue(shouldPromptForHotspot(ready, PhoneHotspotState.OFF, dismissed = false))
        assertFalse(shouldPromptForHotspot(ready, PhoneHotspotState.ON, dismissed = false))
        assertFalse(shouldPromptForHotspot(ready, PhoneHotspotState.UNKNOWN, dismissed = false))
        assertFalse(shouldPromptForHotspot(ready, PhoneHotspotState.OFF, dismissed = true))
        assertFalse(shouldPromptForHotspot(ready.copy(credentialStatusKnown = false), PhoneHotspotState.OFF, dismissed = false))
        assertFalse(shouldPromptForHotspot(ready.copy(stage = ProvisioningStage.CONNECTING_WIFI), PhoneHotspotState.OFF, dismissed = false))
    }

    @Test
    fun `only definite tethered hotspot states are recognized`() {
        assertEquals(PhoneHotspotState.OFF, PhoneHotspotSignal.decode(state = 11, mode = 1))
        assertEquals(PhoneHotspotState.ON, PhoneHotspotSignal.decode(state = 13, mode = 1))
        assertEquals(PhoneHotspotState.UNKNOWN, PhoneHotspotSignal.decode(state = 12, mode = 1))
        assertEquals(PhoneHotspotState.UNKNOWN, PhoneHotspotSignal.decode(state = 10, mode = 1))
        assertEquals(PhoneHotspotState.UNKNOWN, PhoneHotspotSignal.decode(state = 11, mode = 2))
        assertEquals(PhoneHotspotState.UNKNOWN, PhoneHotspotSignal.decode(state = null, mode = null))
    }

    @Test
    fun `BLE recovery is offered throughout camera startup until connected`() {
        assertTrue(shouldOfferBleRecovery(StreamConnectionState.SETUP_REQUIRED))
        assertTrue(shouldOfferBleRecovery(StreamConnectionState.READY))
        assertTrue(shouldOfferBleRecovery(StreamConnectionState.CONNECTING))
        assertTrue(shouldOfferBleRecovery(StreamConnectionState.RECONNECTING))
        assertTrue(shouldOfferBleRecovery(StreamConnectionState.UNAVAILABLE))
        assertTrue(shouldOfferBleRecovery(null))
        assertFalse(shouldOfferBleRecovery(StreamConnectionState.CONNECTED))
    }
}
