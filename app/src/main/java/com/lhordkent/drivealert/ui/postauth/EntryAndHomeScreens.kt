package com.lhordkent.drivealert.ui.postauth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.R
import com.lhordkent.drivealert.postauth.PostAuthUiState
import com.lhordkent.drivealert.ui.theme.Border
import com.lhordkent.drivealert.ui.theme.Ink
import com.lhordkent.drivealert.ui.theme.Surface
import com.lhordkent.drivealert.ui.theme.TextMuted
import com.lhordkent.drivealert.ui.theme.TextPrimary
import com.lhordkent.drivealert.ui.theme.TextSecondary
import java.time.format.DateTimeFormatter

@Composable
fun ChooseViewScreen(onDriver: () -> Unit, onTrustedContact: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.drivealert_logo),
                contentDescription = "DriveAlert logo",
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(32.dp))
            Text(
                "Choose your view",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You can switch views later from Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
            Spacer(Modifier.height(34.dp))
            ViewChoice(
                icon = Icons.Rounded.DirectionsCar,
                title = "Driver view",
                body = "Review device setup, preview monitoring, and see your alert history.",
                action = "Continue as Driver",
                onClick = onDriver,
            )
            Spacer(Modifier.height(16.dp))
            ViewChoice(
                icon = Icons.Rounded.People,
                title = "Trusted Contact view",
                body = "Review Stage 3 transition and persistence records shared by connected Drivers.",
                action = "Continue as Trusted Contact",
                onClick = onTrustedContact,
            )
        }
    }
}

@Composable
private fun ViewChoice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    GroupSurface {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(18.dp))
        SecondaryButton(action, onClick)
    }
}

@Composable
fun SetupDeviceScreen(
    onContinue: () -> Unit,
    onDeviceConnection: () -> Unit,
    onCameraAlignment: () -> Unit,
    onCalibration: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) {
        ScrollableScreen(title = "Setup & Device", onBack = onBack) {
            SetupDeviceContent(onContinue, onDeviceConnection, onCameraAlignment, onCalibration)
        }
    } else {
        Column(Modifier.fillMaxSize().background(Ink)) {
            DriveAlertTopBar(title = "Setup & Device", showLogo = true)
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 600.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
            ) { SetupDeviceContent(onContinue, onDeviceConnection, onCameraAlignment, onCalibration) }
        }
    }
}

@Composable
private fun SetupDeviceContent(
    onContinue: () -> Unit,
    onDeviceConnection: () -> Unit,
    onCameraAlignment: () -> Unit,
    onCalibration: () -> Unit,
) {
    Text("Set up DriveAlert", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
    Spacer(Modifier.height(8.dp))
    Text(
        "Review the connection, camera alignment, and notification preference before opening Driver Home.",
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
    )
    Spacer(Modifier.height(24.dp))
    SetupStep(Icons.Rounded.Router, "Device connection", "Ready for preview", "Review permissions, local hotspot, IoT speaker, and IR camera readiness.", onDeviceConnection)
    SetupStep(Icons.Rounded.CameraAlt, "Camera alignment", "Alignment ready", "Check face position and camera placement before monitoring.", onCameraAlignment)
    SetupStep(Icons.Rounded.CheckCircle, "Driver calibration", "Calibrated", "Review the mock eye, mouth, and neutral-head calibration checkpoints.", onCalibration)
    SetupStep(Icons.Rounded.Notifications, "Notifications", "Recommended", "Notifications are helpful but do not block monitoring readiness.")
    Spacer(Modifier.height(8.dp))
    GroupSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = TextMuted, modifier = Modifier.size(22.dp))
            Text(
                "DriveAlert reports selected visible signs associated with drowsiness. It does not diagnose whether a Driver is drowsy.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    PrimaryButton("Continue to Home", onContinue)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SetupStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    status: String,
    body: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(Surface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = TextPrimary) }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                StatusPill(status, StatusTone.SUCCESS)
            }
            Spacer(Modifier.height(5.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
fun DriverHomeScreen(
    state: PostAuthUiState,
    onPreviewMonitoring: () -> Unit,
    onSetupDevice: () -> Unit,
    onOpenAlerts: () -> Unit,
) {
    val latest = state.driverAlerts.maxByOrNull { it.occurredAt }
    val todayCount = state.driverAlerts.count { it.occurredAt.toLocalDate() == java.time.LocalDate.now() }

    Column {
        Text("Ready for preview", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Setup requirements are ready for this interface preview.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(20.dp))
        GroupSurface {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Monitoring readiness", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text("Ready", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                StatusPill("Ready", StatusTone.SUCCESS)
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Preview Monitoring", onPreviewMonitoring)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Setup & Device", onSetupDevice)
        }
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Recent activity")
            androidx.compose.material3.TextButton(onClick = onOpenAlerts) { Text("View alerts") }
        }
        Spacer(Modifier.height(4.dp))
        KeyValueRow("Alerts today", if (state.isLocalDataLoading) "Loading" else todayCount.toString())
        KeyValueRow("Latest alert", if (state.isLocalDataLoading) "Loading" else latest?.occurredAt?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "None")
        KeyValueRow(
            "Latest session",
            if (state.isLocalDataLoading) "Loading" else state.monitoringSessions.firstOrNull()?.status ?: "None",
        )
        KeyValueRow(
            "Trusted Contact",
            if (state.approvedContacts.isEmpty()) "Not connected" else "${state.approvedContacts.size} approved",
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Confirmed events remain in Driver Alert History. Separate Stage 3 transition or persistence records may be shared with approved Trusted Contacts.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(Modifier.height(24.dp))
    }
}
