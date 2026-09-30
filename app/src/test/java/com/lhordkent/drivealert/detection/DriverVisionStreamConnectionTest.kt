package com.lhordkent.drivealert.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverVisionStreamConnectionTest {
    @Test
    fun `same endpoint update does not restart an active stream`() {
        assertFalse(
            shouldConnectStreamForDeviceUpdate(
                streamWanted = true,
                nextStreamUrl = "http://192.168.1.2:81/stream",
                activeSourceStreamUrl = "http://192.168.1.2:81/stream",
                connectionAttemptActive = false,
            ),
        )
    }

    @Test
    fun `changed endpoint restarts an active stream`() {
        assertTrue(
            shouldConnectStreamForDeviceUpdate(
                streamWanted = true,
                nextStreamUrl = "http://192.168.1.3:81/stream",
                activeSourceStreamUrl = "http://192.168.1.2:81/stream",
                connectionAttemptActive = false,
            ),
        )
    }

    @Test
    fun `missing stream reconnects unless an attempt is already running`() {
        assertTrue(
            shouldConnectStreamForDeviceUpdate(
                streamWanted = true,
                nextStreamUrl = "http://192.168.1.2:81/stream",
                activeSourceStreamUrl = null,
                connectionAttemptActive = false,
            ),
        )
        assertFalse(
            shouldConnectStreamForDeviceUpdate(
                streamWanted = true,
                nextStreamUrl = "http://192.168.1.2:81/stream",
                activeSourceStreamUrl = null,
                connectionAttemptActive = true,
            ),
        )
    }
}
