package com.lhordkent.drivealert.ui.postauth

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.detection.model.CalibrationProgress
import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.detection.frame.SharedBitmapFrame
import com.lhordkent.drivealert.detection.model.CalibrationPhase
import com.lhordkent.drivealert.detection.model.CalibrationStatus
import com.lhordkent.drivealert.ui.theme.Surface
import com.lhordkent.drivealert.ui.theme.TextMuted
import com.lhordkent.drivealert.ui.theme.TextPrimary
import com.lhordkent.drivealert.ui.theme.TextSecondary
import kotlinx.coroutines.flow.StateFlow

@Composable
@Suppress("UNUSED_PARAMETER")
fun DeviceConnectionScreen(
    onBack: () -> Unit,
    provisioningViewModel: com.lhordkent.drivealert.provisioning.WifiProvisioningViewModel? = null,
    vision: DriverVisionUiState? = null,
    onDisconnect: () -> Unit = {},
) {
    ScrollableScreen(title = "Device Connection", onBack = onBack) {
        Text("Connect your DriveAlert camera", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Use secure Bluetooth provisioning to send your hotspot or local Wi-Fi details to the ESP32-CAM.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(22.dp))
        if (provisioningViewModel == null) {
            WifiProvisioningPanel(vision = vision, onDisconnect = onDisconnect)
        } else {
            WifiProvisioningPanel(provisioningViewModel, vision, onDisconnect)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun CameraAlignmentScreen(
    vision: DriverVisionUiState,
    previewFrames: StateFlow<SharedBitmapFrame?>? = null,
    onStartVision: () -> Unit,
    onConfirmAlignment: () -> Unit,
    onBack: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onStartVision() }
    ScrollableScreen(title = "Camera Alignment", onBack = onBack) {
        Text("Position the camera", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Keep the full face visible, look forward, and keep the camera mount steady.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(20.dp))
        LiveCameraPreview(vision.streamState, previewFrames)
        Spacer(Modifier.height(18.dp))
        SetupDetailRow(
            "Camera stream",
            vision.streamState.readableLabel(),
            if (vision.streamState == StreamConnectionState.CONNECTED) StatusTone.SUCCESS else StatusTone.WARNING,
        )
        SetupDetailRow(
            "Face view",
            when (vision.faceDetected) { true -> "Face visible"; false -> "Face not detected"; null -> "Waiting for video" },
            if (vision.faceDetected == true) StatusTone.SUCCESS else StatusTone.WARNING,
        )
        SetupDetailRow("Alignment", if (vision.alignmentConfirmed) "Confirmed" else "Confirmation needed", if (vision.alignmentConfirmed) StatusTone.SUCCESS else StatusTone.WARNING)
        vision.errorMessage?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            if (vision.alignmentConfirmed) "Alignment Confirmed" else "Confirm Alignment",
            onConfirmAlignment,
            enabled = vision.streamState == StreamConnectionState.CONNECTED && !vision.alignmentConfirmed,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun CalibrationScreen(
    vision: DriverVisionUiState,
    previewFrames: StateFlow<SharedBitmapFrame?>? = null,
    onStartVision: () -> Unit,
    onStartCalibration: () -> Unit,
    onBeginPhase: () -> Unit,
    onRepeatPhase: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onStartVision() }
    val progress = vision.calibrationProgress
    ScrollableScreen(title = "Driver Calibration", onBack = onBack) {
        Text("Personalized calibration", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Follow each step while the camera measures your natural eye, mouth, and head positions.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(22.dp))
        LiveCameraPreview(vision.streamState, previewFrames)
        Spacer(Modifier.height(18.dp))
        SetupDetailRow(
            "Calibration status",
            when {
                !vision.calibrationInProgress && vision.activeCalibration != null -> "Calibrated"
                vision.calibrationInProgress -> "In progress"
                else -> "Not calibrated"
            },
            if (vision.activeCalibration != null) StatusTone.SUCCESS else StatusTone.WARNING,
        )
        Spacer(Modifier.height(18.dp))
        GroupSurface {
            if (!vision.alignmentConfirmed) {
                Text(
                    "Confirm camera alignment before starting calibration.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            } else if (vision.calibrationInProgress && progress != null && progress.phase != CalibrationPhase.COMPLETE) {
                SetupDetailRow("Current step", progress.phase.instruction(), StatusTone.INFO)
                SetupDetailRow("Measurement", if (vision.measurementAvailable(progress.phase)) "Ready" else "Hold the requested position", if (vision.measurementAvailable(progress.phase)) StatusTone.SUCCESS else StatusTone.WARNING)
                SetupDetailRow(
                    "Valid time",
                    "%.1f / %.1f seconds".format(
                        progress.validDurationMs / 1_000.0,
                        progress.requiredDurationMs / 1_000.0,
                    ),
                    StatusTone.WARNING,
                )
            } else Text("Calibration is stored locally for this Driver and remains available offline.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(22.dp))
        val canStart = vision.alignmentConfirmed && vision.streamState == StreamConnectionState.CONNECTED
        when {
            !vision.calibrationInProgress ->
                PrimaryButton(if (vision.activeCalibration == null) "Start Calibration" else "Recalibrate", onStartCalibration, enabled = canStart)
            progress?.status == CalibrationStatus.READY ->
                PrimaryButton("Start ${progress.phase.instruction()}", onBeginPhase, enabled = canStart)
            else -> PrimaryButton("Collecting valid measurements", {}, enabled = false)
        }
        if (vision.calibrationInProgress && progress != null && progress.status != CalibrationStatus.COMPLETE) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Repeat This Step", onRepeatPhase, enabled = canStart)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Cancel Calibration", onCancel)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LiveCameraPreview(
    streamState: StreamConnectionState,
    previewFrames: StateFlow<SharedBitmapFrame?>?,
) {
    // Collect the high-frequency image flow only inside the preview. Detection,
    // navigation and the rest of the screen no longer recompose for every JPEG.
    val sharedFrame = previewFrames?.collectAsState()?.value
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(Surface),
        contentAlignment = Alignment.Center,
    ) {
        // Released SharedBitmapFrames deliberately leave Bitmap lifetime to the
        // Android/Compose GC contract. A composition that already observed a frame
        // can therefore finish drawing it without briefly showing the placeholder.
        val bitmap = sharedFrame?.bitmap
        if (bitmap != null && !bitmap.isRecycled) {
            Image(bitmap.asImageBitmap(), "Live DriveAlert camera preview", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Canvas(Modifier.matchParentSize().padding(horizontal = 54.dp, vertical = 30.dp)) {
                drawOval(Color.White.copy(alpha = 0.82f), style = Stroke(width = 4f))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(42.dp), tint = TextMuted)
                Spacer(Modifier.height(10.dp))
                Text(streamState.readableLabel(), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Keep the camera powered and on the same network", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

private fun StreamConnectionState.readableLabel() = when (this) {
    StreamConnectionState.SETUP_REQUIRED -> "Setup required"
    StreamConnectionState.READY -> "Ready to connect"
    StreamConnectionState.CONNECTING -> "Connecting"
    StreamConnectionState.CONNECTED -> "Connected"
    StreamConnectionState.RECONNECTING -> "Reconnecting"
    StreamConnectionState.UNAVAILABLE -> "Stream unavailable"
}

private fun CalibrationPhase.instruction() = when (this) {
    CalibrationPhase.NEUTRAL -> "Neutral position"
    CalibrationPhase.EYES_CLOSED -> "Eyes closed"
    CalibrationPhase.MOUTH_OPEN -> "Mouth open"
    CalibrationPhase.HEAD_DOWN -> "Head down"
    CalibrationPhase.COMPLETE -> "Complete"
}

private fun DriverVisionUiState.measurementAvailable(phase: CalibrationPhase): Boolean {
    val values = detection?.measurements ?: return false
    return when (phase) {
        CalibrationPhase.NEUTRAL -> values.ear != null && values.mar != null && values.rawHeadPitchDegrees != null
        CalibrationPhase.EYES_CLOSED -> values.ear != null
        CalibrationPhase.MOUTH_OPEN -> values.mar != null
        CalibrationPhase.HEAD_DOWN -> values.rawHeadPitchDegrees != null
        CalibrationPhase.COMPLETE -> true
    }
}

@Composable
private fun SetupDetailRow(label: String, value: String, tone: StatusTone) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
        StatusPill(value, tone)
    }
}
