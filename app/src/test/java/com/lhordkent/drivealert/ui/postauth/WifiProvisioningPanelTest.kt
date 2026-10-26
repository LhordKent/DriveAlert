package com.lhordkent.drivealert.ui.postauth

import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiProvisioningPanelTest {
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
