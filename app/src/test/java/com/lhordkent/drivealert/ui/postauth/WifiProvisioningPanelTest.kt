package com.lhordkent.drivealert.ui.postauth

import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiProvisioningPanelTest {
    @Test
    fun `BLE recovery is offered only after camera connection trouble`() {
        assertTrue(shouldOfferBleRecovery(StreamConnectionState.RECONNECTING))
        assertTrue(shouldOfferBleRecovery(StreamConnectionState.UNAVAILABLE))
        assertFalse(shouldOfferBleRecovery(StreamConnectionState.CONNECTING))
        assertFalse(shouldOfferBleRecovery(StreamConnectionState.CONNECTED))
        assertFalse(shouldOfferBleRecovery(StreamConnectionState.READY))
        assertFalse(shouldOfferBleRecovery(null))
    }
}
