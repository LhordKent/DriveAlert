package com.lhordkent.drivealert.ui.postauth

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.postauth.CalibrationState
import com.lhordkent.drivealert.postauth.DeviceConnectionState
import com.lhordkent.drivealert.ui.theme.Surface
import com.lhordkent.drivealert.ui.theme.TextMuted
import com.lhordkent.drivealert.ui.theme.TextPrimary
import com.lhordkent.drivealert.ui.theme.TextSecondary

@Composable
fun DeviceConnectionScreen(
    state: DeviceConnectionState,
    onReconnect: () -> Unit,
    onBack: () -> Unit,
) {
    ScrollableScreen(title = "Device Connection", onBack = onBack) {
        Text("Local device readiness", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("These session-only states preview the connection flow. No hotspot or IoT hardware is controlled.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(22.dp))
        GroupSurface {
            SetupDetailRow("App permissions", "Ready", StatusTone.SUCCESS)
            SetupDetailRow("Local hotspot", "Ready", StatusTone.SUCCESS)
            SetupDetailRow("IoT device", state.label, if (state == DeviceConnectionState.CONNECTED) StatusTone.SUCCESS else StatusTone.WARNING)
            SetupDetailRow("Onboard speaker", "Available", StatusTone.SUCCESS)
            SetupDetailRow("IR camera and illumination", "Available", StatusTone.SUCCESS)
        }
        Spacer(Modifier.height(18.dp))
        Text("The final connection flow will replace these states when the DriveAlert hardware is available.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.height(24.dp))
        SecondaryButton(if (state == DeviceConnectionState.CONNECTED) "Preview Reconnection" else "Reconnect Device", onReconnect)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun CameraAlignmentScreen(
    alignmentReady: Boolean,
    onCompleteCheck: () -> Unit,
    onBack: () -> Unit,
) {
    ScrollableScreen(title = "Camera Alignment", onBack = onBack) {
        Text("Position the camera", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Keep the full face visible, look forward, and keep the camera mount steady.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Surface, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(42.dp), tint = TextMuted)
                Spacer(Modifier.height(10.dp))
                Text("Camera alignment preview", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("No camera feed is active", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        Spacer(Modifier.height(18.dp))
        SetupDetailRow("Alignment status", if (alignmentReady) "Ready" else "Check needed", if (alignmentReady) StatusTone.SUCCESS else StatusTone.WARNING)
        Spacer(Modifier.height(22.dp))
        PrimaryButton(if (alignmentReady) "Run Alignment Preview Again" else "Run Alignment Preview", onCompleteCheck)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun CalibrationScreen(
    state: CalibrationState,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    ScrollableScreen(title = "Driver Calibration", onBack = onBack) {
        Text("Personalized calibration", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Calibration will account for individual eye, mouth, posture, and camera-position differences. This screen currently previews the workflow only.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(22.dp))
        GroupSurface {
            CalibrationCheckpoint("Eyes visible", "Look forward with eyes naturally open")
            CalibrationCheckpoint("Mouth visible", "Keep a relaxed, closed-mouth position")
            CalibrationCheckpoint("Neutral head position", "Sit naturally and face the road ahead")
        }
        Spacer(Modifier.height(18.dp))
        SetupDetailRow("Calibration status", state.label, if (state == CalibrationState.READY) StatusTone.SUCCESS else StatusTone.WARNING)
        Spacer(Modifier.height(22.dp))
        PrimaryButton(if (state == CalibrationState.READY) "Preview Recalibration" else "Complete Calibration Preview", onComplete)
        Spacer(Modifier.height(16.dp))
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

@Composable
private fun CalibrationCheckpoint(title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = TextMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
