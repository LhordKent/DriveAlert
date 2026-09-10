package com.lhordkent.drivealert.ui.postauth

import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.detection.model.CalibrationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupConnectionStatusTest {
    @Test
    fun `saved device awaiting manual connection is not reported as configured`() {
        val state = DriverVisionUiState(
            deviceConfigured = true,
            streamState = StreamConnectionState.READY,
        )

        assertEquals("Connect required", state.streamState.setupConnectionStatus().label)
        assertEquals(StatusTone.WARNING, state.streamState.setupConnectionStatus().tone)
        assertFalse(state.isMonitoringReady())
    }

    @Test
    fun `only a live stream with calibration is monitoring ready`() {
        val calibration = CalibrationResult(
            calibrationId = "test-calibration",
            schemaVersion = 3,
            calibratedAtTimestampMs = 1_000,
            neutralEar = 0.3,
            closedEyeEar = 0.15,
            earThreshold = 0.2,
            neutralMar = 0.1,
            openMouthMar = 0.5,
            marThreshold = 0.3,
            neutralHeadPitchDegrees = 0.0,
            downwardPitchMultiplier = 1.0,
        )

        assertTrue(
            DriverVisionUiState(
                streamState = StreamConnectionState.CONNECTED,
                activeCalibration = calibration,
            ).isMonitoringReady(),
        )
        assertFalse(
            DriverVisionUiState(
                deviceConfigured = true,
                streamState = StreamConnectionState.READY,
                activeCalibration = calibration,
            ).isMonitoringReady(),
        )
    }
}
