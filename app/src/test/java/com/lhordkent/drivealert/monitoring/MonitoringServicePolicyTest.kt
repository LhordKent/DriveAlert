package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.face.DriverTrackingState
import com.lhordkent.drivealert.detection.face.DriverTrackingStatus
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringServicePolicyTest {
    @Test
    fun repeatedStartsAreRejectedWhileStartingOrActive() {
        assertTrue(monitoringStartAllowed(isActive = false, startInProgress = false))
        assertFalse(monitoringStartAllowed(isActive = false, startInProgress = true))
        assertFalse(monitoringStartAllowed(isActive = true, startInProgress = false))
    }

    @Test
    fun reconnectingCameraReportsMonitoringPaused() {
        for (streamState in listOf(StreamConnectionState.CONNECTING, StreamConnectionState.RECONNECTING, StreamConnectionState.UNAVAILABLE)) {
            val notification = monitoringNotificationContent(DriverVisionUiState(streamState = streamState))
            assertEquals("DriveAlert monitoring paused", notification.title)
            assertTrue(notification.text.contains("reconnect", ignoreCase = true))
        }
    }

    @Test
    fun connectedCameraWithoutFreshReadingsReportsMonitoringPaused() {
        val state = DriverVisionUiState(
            streamState = StreamConnectionState.CONNECTED,
            alignmentConfirmed = true,
            driverTracking = DriverTrackingState(status = DriverTrackingStatus.LOCKED),
            lighting = FaceLightingAssessment(quality = LightingQuality.USABLE),
        )

        val notification = monitoringNotificationContent(state)

        assertEquals("DriveAlert monitoring paused", notification.title)
        assertEquals("Waiting for fresh camera readings.", notification.text)
    }

    @Test
    fun recoveryOccursOnlyWithinTheSameBoot() {
        assertEquals(RecoveryDisposition.NONE, recoveryDisposition(null, 12))
        assertEquals(RecoveryDisposition.RECOVER_SAME_BOOT, recoveryDisposition(12, 12))
        assertEquals(RecoveryDisposition.INTERRUPT_AFTER_REBOOT, recoveryDisposition(11, 12))
        assertEquals(RecoveryDisposition.INTERRUPT_AFTER_REBOOT, recoveryDisposition(-1, -1))
    }
}
