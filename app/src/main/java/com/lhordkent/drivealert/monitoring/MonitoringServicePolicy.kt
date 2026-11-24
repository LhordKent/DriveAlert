package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.frame.StreamConnectionState

internal data class MonitoringNotificationContent(val title: String, val text: String)

internal val startingMonitoringNotification = MonitoringNotificationContent(
    title = "DriveAlert monitoring starting",
    text = "Starting camera monitoring",
)

internal fun monitoringNotificationContent(state: DriverVisionUiState): MonitoringNotificationContent {
    val pausedReason = when {
        state.streamState == StreamConnectionState.CONNECTING || state.streamState == StreamConnectionState.RECONNECTING ->
            "Camera disconnected. Reconnecting…"
        state.streamState == StreamConnectionState.UNAVAILABLE -> "Camera unavailable. Still trying to reconnect."
        state.streamState != StreamConnectionState.CONNECTED -> "Connect the DriveAlert camera."
        !state.alignmentConfirmed -> "Confirm the driver area."
        !state.driverTracking.locked -> state.driverTracking.label
        state.insufficientLighting || state.visibilityIssue == VisibilityIssue.INSUFFICIENT_LIGHTING ||
            state.lighting.quality == LightingQuality.INSUFFICIENT -> "Insufficient lighting."
        state.lighting.recovering || state.lighting.quality == LightingQuality.UNKNOWN -> "Checking visibility."
        state.detection == null -> "Waiting for fresh camera readings."
        else -> null
    }
    return if (pausedReason == null) {
        MonitoringNotificationContent("DriveAlert monitoring active", "Camera connected and monitoring is active")
    } else {
        MonitoringNotificationContent("DriveAlert monitoring paused", pausedReason)
    }
}

internal enum class RecoveryDisposition { NONE, RECOVER_SAME_BOOT, INTERRUPT_AFTER_REBOOT }

internal fun monitoringStartAllowed(isActive: Boolean, startInProgress: Boolean): Boolean =
    !isActive && !startInProgress

internal fun recoveryDisposition(recordBootCount: Int?, currentBootCount: Int): RecoveryDisposition = when {
    recordBootCount == null -> RecoveryDisposition.NONE
    recordBootCount == MonitoringRecoveryStore.UNKNOWN_BOOT_COUNT ||
        currentBootCount == MonitoringRecoveryStore.UNKNOWN_BOOT_COUNT -> RecoveryDisposition.INTERRUPT_AFTER_REBOOT
    recordBootCount == currentBootCount -> RecoveryDisposition.RECOVER_SAME_BOOT
    else -> RecoveryDisposition.INTERRUPT_AFTER_REBOOT
}
