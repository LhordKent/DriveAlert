package com.example.drivealert

import com.example.drivealert.model.AlertFilter
import com.example.drivealert.model.AlertSeverity
import com.example.drivealert.model.ConnectionStage
import com.example.drivealert.model.DriveAlertAppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveAlertAppStateTest {
    @Test
    fun connectionDemo_advancesToFailureThenRetryCompletes() {
        val state = DriveAlertAppState()

        repeat(5) { state.advanceConnection() }

        assertEquals(ConnectionStage.Failed, state.connectionStage)
        state.completeConnection()
        assertEquals(ConnectionStage.Connected, state.connectionStage)
        assertTrue(state.deviceConnected)
    }

    @Test
    fun calibration_advancesDeterministically() {
        val state = DriveAlertAppState()

        repeat(3) { state.advanceCalibration() }

        assertEquals(100, state.calibrationProgress)
    }

    @Test
    fun alertFilter_returnsOnlyRequestedRecurrenceSeverity() {
        val state = DriveAlertAppState()
        state.alertFilter = AlertFilter.Escalated

        assertTrue(state.filteredAlerts.isNotEmpty())
        assertTrue(state.filteredAlerts.all { it.severity == AlertSeverity.Escalated })
    }

    @Test
    fun forgettingDevice_resetsSetupState() {
        val state = DriveAlertAppState().apply {
            calibrationProgress = 100
            connectionStage = ConnectionStage.Connected
            showForgetDialog = true
        }

        state.resetDevice()

        assertFalse(state.deviceConnected)
        assertEquals(ConnectionStage.Idle, state.connectionStage)
        assertEquals(0, state.calibrationProgress)
        assertFalse(state.showForgetDialog)
    }

    @Test
    fun retentionAndSoundSelections_areLocalMutableState() {
        val state = DriveAlertAppState()
        state.retention = "Until Manually Deleted"
        state.warningSound = "Alert Tone 2"

        assertEquals("Until Manually Deleted", state.retention)
        assertEquals("Alert Tone 2", state.warningSound)
    }
}
