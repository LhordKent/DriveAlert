package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.postauth.MonitoringPresentation
import com.lhordkent.drivealert.postauth.MonitoringScenario
import com.lhordkent.drivealert.postauth.presentation

enum class CameraVisibilityTone { INFO, GOOD, LIMITED, UNAVAILABLE }

data class CameraVisibilityPresentation(
    val label: String,
    val detail: String,
    val tone: CameraVisibilityTone,
    val monitoring: MonitoringPresentation,
)

fun cameraVisibilityPresentation(
    streamState: StreamConnectionState,
    scenario: MonitoringScenario,
    hasFreshDetection: Boolean,
    lowLight: Boolean?,
    pendingIssue: VisibilityIssue? = null,
    headAvailable: Boolean = true,
    lighting: FaceLightingAssessment? = null,
    driverTracking: com.lhordkent.drivealert.detection.face.DriverTrackingState? = null,
    alignmentConfirmed: Boolean = true,
): CameraVisibilityPresentation {
    fun unavailable(label: String, detail: String, tone: CameraVisibilityTone) = CameraVisibilityPresentation(
        label, detail, tone, MonitoringPresentation("Monitoring paused", detail, false, false, false),
    )
    when (streamState) {
        StreamConnectionState.SETUP_REQUIRED, StreamConnectionState.READY -> return unavailable(
            "Not connected", "Connect the DriveAlert camera to check visibility.", CameraVisibilityTone.INFO,
        )
        StreamConnectionState.CONNECTING -> return unavailable(
            "Starting camera", "Waiting for fresh camera frames. Monitoring is unavailable until the view is restored.",
            CameraVisibilityTone.INFO,
        )
        StreamConnectionState.RECONNECTING, StreamConnectionState.UNAVAILABLE -> return unavailable(
            "Disconnected", "Camera disconnected. Reconnecting; monitoring is unavailable until the view is restored.",
            CameraVisibilityTone.UNAVAILABLE,
        )
        StreamConnectionState.CONNECTED -> Unit
    }
    if (!alignmentConfirmed) return unavailable("Set driver area", "Confirm the driver area in Camera Alignment before monitoring.", CameraVisibilityTone.INFO)
    if (driverTracking != null && !driverTracking.locked) return unavailable(driverTracking.label, driverTracking.guidance, CameraVisibilityTone.UNAVAILABLE)
    if (!hasFreshDetection) return unavailable(
        "Checking view", "Waiting for a fresh face-tracking result. Monitoring is unavailable while the view is being checked.",
        CameraVisibilityTone.INFO,
    )
    if (lighting?.recovering == true && scenario != MonitoringScenario.FACE_TRACKING_UNAVAILABLE) return unavailable(
        "Checking visibility", "Checking visibility. Monitoring resumes after two seconds of usable facial lighting.", CameraVisibilityTone.INFO,
    )
    if (pendingIssue == VisibilityIssue.INSUFFICIENT_LIGHTING || lighting?.quality == LightingQuality.INSUFFICIENT) {
        val detail = "Insufficient lighting. Restore camera visibility when safe. Drowsiness assessment is paused."
        return unavailable("Insufficient lighting", detail, CameraVisibilityTone.UNAVAILABLE).let {
            it.copy(monitoring = it.monitoring.copy(headline = "Monitoring paused—insufficient lighting"))
        }
    }
    if (lighting?.quality == LightingQuality.UNKNOWN && scenario != MonitoringScenario.FACE_TRACKING_UNAVAILABLE) return unavailable(
        "Checking visibility", "Waiting for usable facial lighting. Drowsiness assessment is paused.", CameraVisibilityTone.INFO,
    )
    val effectiveScenario = when {
        scenario == MonitoringScenario.FACE_TRACKING_UNAVAILABLE || pendingIssue == VisibilityIssue.FACE_UNAVAILABLE ->
            MonitoringScenario.FACE_TRACKING_UNAVAILABLE
        scenario == MonitoringScenario.EYE_AND_YAWNING_UNAVAILABLE || pendingIssue == VisibilityIssue.BOTH_REGIONS_OBSTRUCTED ->
            MonitoringScenario.EYE_AND_YAWNING_UNAVAILABLE
        scenario == MonitoringScenario.EYE_UNAVAILABLE && pendingIssue == VisibilityIssue.LOWER_FACE_OBSTRUCTED ||
            scenario == MonitoringScenario.YAWNING_UNAVAILABLE && pendingIssue == VisibilityIssue.EYE_REGION_OBSTRUCTED ->
            MonitoringScenario.EYE_AND_YAWNING_UNAVAILABLE
        pendingIssue == VisibilityIssue.EYE_REGION_OBSTRUCTED -> MonitoringScenario.EYE_UNAVAILABLE
        pendingIssue == VisibilityIssue.LOWER_FACE_OBSTRUCTED -> MonitoringScenario.YAWNING_UNAVAILABLE
        else -> scenario
    }
    val monitoring = effectiveScenario.presentation().let {
        if (!headAvailable && it.headAvailable) {
            it.copy(headline = "Monitoring limited", headAvailable = false,
                guidance = "Head monitoring is unavailable. Eye and yawning monitoring can continue.")
        } else it
    }
    if (effectiveScenario == MonitoringScenario.FACE_TRACKING_UNAVAILABLE) {
        val detail = if (lowLight == true) {
            "Face not visible. Low light may be affecting monitoring. Check lighting and camera position when safe."
        } else {
            "Face not visible. Check camera position or an obstruction when safe. Monitoring resumes when your face is visible."
        }
        return unavailable("Face not visible", detail, CameraVisibilityTone.UNAVAILABLE)
    }
    if (effectiveScenario != MonitoringScenario.NORMAL || !headAvailable) {
        val detail = monitoring.guidance + if (lowLight == true) " Low light may also affect visibility." else ""
        return CameraVisibilityPresentation(
            "Limited view", detail,
            if (effectiveScenario == MonitoringScenario.EYE_AND_YAWNING_UNAVAILABLE) CameraVisibilityTone.UNAVAILABLE else CameraVisibilityTone.LIMITED,
            monitoring.copy(guidance = detail),
        )
    }
    val detail = if (lowLight == true) {
        "Low light. Monitoring is available while your face and monitoring signs remain visible."
    } else {
        "Your face and monitoring signs are visible."
    }
    return CameraVisibilityPresentation(
        if (lowLight == true) "Low light" else "Clear view", detail,
        if (lowLight == true) CameraVisibilityTone.LIMITED else CameraVisibilityTone.GOOD,
        monitoring.copy(guidance = detail),
    )
}
