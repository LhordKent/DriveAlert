package com.example.drivealert.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.drivealert.model.AccountRole
import com.example.drivealert.model.AlertFilter
import com.example.drivealert.model.AlertRecord
import com.example.drivealert.model.AlertSeverity
import com.example.drivealert.model.DetectedSign
import com.example.drivealert.model.DriveAlertAppState
import com.example.drivealert.model.MockData
import com.example.drivealert.model.Route
import com.example.drivealert.ui.theme.Border
import com.example.drivealert.ui.theme.DriveRed
import com.example.drivealert.ui.theme.DriveRedSoft
import com.example.drivealert.ui.theme.Info
import com.example.drivealert.ui.theme.InfoSoft
import com.example.drivealert.ui.theme.InkRaised
import com.example.drivealert.ui.theme.Success
import com.example.drivealert.ui.theme.SuccessSoft
import com.example.drivealert.ui.theme.Surface
import com.example.drivealert.ui.theme.TextMuted
import com.example.drivealert.ui.theme.TextPrimary
import com.example.drivealert.ui.theme.TextSecondary
import com.example.drivealert.ui.theme.Warning
import com.example.drivealert.ui.theme.WarningSoft

@Composable
fun DriverHomeScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    AppPage("Driver home", "Good morning, Lhord", role = AccountRole.Driver, currentRoute = Route.DriverHome, onNavigate = navigate) {
        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("DriveAlert Device", style = MaterialTheme.typography.titleMedium); Text("DA-001", color = TextSecondary) }
                StatusPill(if (state.deviceConnected) "Connected" else "Disconnected", if (state.deviceConnected) Success else DriveRed, if (state.deviceConnected) SuccessSoft else DriveRedSoft, if (state.deviceConnected) Icons.Rounded.CheckCircle else Icons.Rounded.Error)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Monitoring readiness", if (state.deviceConnected) "Ready" else "Setup needed", Modifier.weight(1f), Icons.Rounded.Shield)
            MetricCard("Calibration", if (state.calibrationProgress == 100) "Complete" else "Required", Modifier.weight(1f), Icons.Rounded.Visibility)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Alerts today", "3", Modifier.weight(1f), Icons.Rounded.Notifications)
            MetricCard("Latest alert", "10:46 AM", Modifier.weight(1f), Icons.Rounded.Schedule)
        }
        SectionCard {
            LabeledValue("Latest Alert", "Yawning · 10:42 AM")
            Divider(); LabeledValue("Trusted Contact Status", "Connected", Success)
            Divider(); LabeledValue("Sync Status", "Up to Date", Success)
        }
        if (state.deviceConnected) PrimaryButton("Start Monitoring") { state.monitoring = true; navigate(Route.Monitoring) }
        else PrimaryButton("Set Up Device") { navigate(Route.PowerOn) }
        SecondaryButton(if (state.deviceConnected) "Device Management" else "Reconnect Device") { navigate(if (state.deviceConnected) Route.DeviceManagement else Route.Connect) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun MonitoringScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    if (state.showStopDialog) AlertDialog(
        onDismissRequest = { state.showStopDialog = false },
        title = { Text("Stop monitoring?") }, text = { Text("This ends the local demonstration session. No records are saved.") },
        confirmButton = { TextButton(onClick = { state.monitoring = false; state.showStopDialog = false; navigate(Route.DriverHome) }) { Text("Stop", color = DriveRed) } },
        dismissButton = { TextButton(onClick = { state.showStopDialog = false }) { Text("Continue Session") } },
    )
    AppPage("Monitoring session", "Morning Drive", onBack = { navigate(Route.DriverHome) }, role = AccountRole.Driver, currentRoute = Route.Monitoring, onNavigate = navigate) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(CardShape).background(Surface), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Person, null, tint = TextMuted, modifier = Modifier.size(90.dp))
            Text("CAMERA PREVIEW", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.align(Alignment.TopCenter).padding(14.dp))
            StatusPill("Driver Zone ready", Success, SuccessSoft, Icons.Rounded.CameraAlt)
        }
        SectionCard {
            LabeledValue("Device Connection", if (state.deviceConnected) "Connected" else "Disconnected", if (state.deviceConnected) Success else DriveRed)
            LabeledValue("Session Duration", "01:24:16")
            LabeledValue("Alerts Today", "3")
            LabeledValue("Latest Alert", "Yawning · 10:42 AM")
        }
        SectionCard {
            Text("Alert Status", style = MaterialTheme.typography.titleMedium)
            SeverityPill(AlertSeverity.Repeated)
            Text("Repeated drowsiness alerts detected. Rest is advised.", color = TextSecondary)
        }
        SectionCard {
            Text("Monitored Signs", style = MaterialTheme.typography.titleMedium)
            SignRow(DetectedSign.EyeClosure, "Watching", Warning)
            SignRow(DetectedSign.Yawning, "Normal", Success)
            SignRow(DetectedSign.HeadNodding, "Normal", Success)
        }
        PrimaryButton("Stop Monitoring") { state.showStopDialog = true }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun SignRow(sign: DetectedSign, status: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(sign.label, color = TextSecondary); StatusPill(status, color, if (color == Success) SuccessSoft else WarningSoft)
    }
}

@Composable
fun AlertsScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    AppPage("Alert history", "Confirmed warning records", role = AccountRole.Driver, currentRoute = Route.Alerts, onNavigate = navigate) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Alerts today", "3", Modifier.weight(1f), Icons.Rounded.Notifications)
            MetricCard("This week", "9", Modifier.weight(1f), Icons.Rounded.History)
        }
        SectionCard(onClick = { navigate(Route.Insights) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BarChart, null, tint = DriveRed); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Text("Drowsiness Insights", style = MaterialTheme.typography.titleMedium); Text("Weekly trend and summary", color = TextSecondary) }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = TextMuted)
            }
        }
        AlertFilter.entries.chunked(2).forEach { rowFilters ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowFilters.forEach { filter ->
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = state.alertFilter == filter, onClick = { state.alertFilter = filter },
                    label = { Text(filter.name) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DriveRedSoft, selectedLabelColor = TextPrimary),
                )
            }
            }
        }
        if (state.filteredAlerts.isEmpty()) EmptyState(Icons.Rounded.History, "No Alerts Yet", "Your confirmed DriveAlert warnings will appear here.")
        else state.filteredAlerts.forEach { alert -> AlertRow(alert) { state.selectedAlertId = alert.id; navigate(Route.AlertDetail) } }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun AlertRow(alert: AlertRecord, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text(alert.signs.first().label, style = MaterialTheme.typography.titleMedium); Text("${alert.date} · ${alert.time}", color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
            SeverityPill(alert.severity)
        }
        LabeledValue("Session", alert.session)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Trusted Contact Sync", color = TextSecondary); SyncPill(alert.syncStatus) }
    }
}

@Composable
fun AlertDetailScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    val alert = state.selectedAlert
    AppPage("Alert detail", "Confirmed warning record", onBack = { navigate(Route.Alerts) }) {
        SectionCard {
            Text("Alert Information", style = MaterialTheme.typography.titleLarge)
            LabeledValue("Date", alert.date); LabeledValue("Time", alert.time); LabeledValue("Monitoring Session", alert.session)
            Divider(); Text("Detected Signs", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            alert.signs.forEach { Text("• ${it.label}") }
        }
        SectionCard {
            Text("Alert Severity", style = MaterialTheme.typography.titleMedium); SeverityPill(alert.severity)
            Text("Severity reflects confirmed alert recurrence, not a medical diagnosis.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        SectionCard { Text("Recurrence Information", style = MaterialTheme.typography.titleMedium); Text(alert.recurrence, color = TextSecondary) }
        SectionCard {
            Text("Trusted Contact Sync", style = MaterialTheme.typography.titleMedium); SyncPill(alert.syncStatus)
            alert.syncTime?.let { LabeledValue("Synchronization Time", it) }
        }
        SecondaryButton("View Drowsiness Insights") { navigate(Route.Insights) }
        SecondaryButton("Back to Alerts") { navigate(Route.Alerts) }
    }
}

@Composable
fun InsightsScreen(navigate: (String) -> Unit) {
    AppPage("Drowsiness insights", "Hardcoded sample trends", onBack = { navigate(Route.Alerts) }, role = AccountRole.Driver, currentRoute = Route.Insights, onNavigate = navigate) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Weekly alerts", "9", Modifier.weight(1f), Icons.Rounded.BarChart)
            MetricCard("Most frequent", "Yawning", Modifier.weight(1f), Icons.Rounded.Visibility)
        }
        SectionCard {
            Text("Alert Trend", style = MaterialTheme.typography.titleMedium); Text("Higher than last week", color = Warning)
            WeeklyChart()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = TextMuted) } }
        }
        SectionCard {
            LabeledValue("Session Alert Level", "Repeated", Warning)
            LabeledValue("Synchronization Status", "Up to Date", Success)
            LabeledValue("Synchronization Summary", "2 synced, 1 pending")
        }
        SectionCard { StatusPill("Rest recommendation", Warning, WarningSoft, Icons.Rounded.Warning); Text("Repeated warnings appeared during the Morning Drive. Consider taking a break before the next trip.", color = TextSecondary) }
    }
}

@Composable private fun WeeklyChart() {
    val values = listOf(.2f, .45f, .3f, .7f, .5f, .9f, .6f)
    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(top = 12.dp)) {
        val gap = 10.dp.toPx(); val barWidth = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, value ->
            val h = size.height * value
            drawRoundRect(if (i == 5) DriveRed else Border, topLeft = Offset(i * (barWidth + gap), size.height - h), size = Size(barWidth, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        }
    }
}

@Composable
fun ContactsScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    AppPage("Trusted contacts", "People approved to view escalations", role = AccountRole.Driver, currentRoute = Route.Contacts, onNavigate = navigate) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Person, null, tint = DriveRed); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("Mara Santos", style = MaterialTheme.typography.titleMedium); Text("mara.santos@example.com", color = TextSecondary) }; StatusPill("Approved", Success, SuccessSoft) }
        }
        PrimaryButton("Add Trusted Contact") { state.contactRequestSent = false; navigate(Route.AddContact) }
        SectionCard(onClick = { navigate(Route.DriverAccess) }) { Text("Driver Access", style = MaterialTheme.typography.titleMedium); Text("Manage the Drivers who can use this device.", color = TextSecondary) }
        SectionCard(onClick = { navigate(Route.Subscription) }) { StatusPill("2 additional slots", Info, InfoSoft, Icons.Rounded.Groups); Text("Manage Subscription", style = MaterialTheme.typography.titleMedium); Text("Plans support additional Driver access, not extra Trusted Contacts.", color = TextSecondary) }
    }
}

@Composable
fun AddContactScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    var email by remember { mutableStateOf("") }; var attempted by remember { mutableStateOf(false) }
    AppPage("Add trusted contact", "Request access to Escalated Alert Records", onBack = { navigate(Route.Contacts) }) {
        FormField(email, { email = it; state.contactRequestSent = false }, "Trusted Contact Email", error = if (attempted && !email.contains("@")) "Enter a valid email" else null)
        SectionCard { Icon(Icons.Rounded.Share, null, tint = Warning); Text("Only Escalated Alert Records are eligible for sharing.", color = TextSecondary) }
        if (state.contactRequestSent) SectionCard { StatusPill("Request Sent", Success, SuccessSoft, Icons.Rounded.CheckCircle); Text("The local prototype has updated the request status.", color = TextSecondary) }
        PrimaryButton("Send Request") { attempted = true; if (email.contains("@")) state.contactRequestSent = true }
        SecondaryButton("Cancel") { navigate(Route.Contacts) }
    }
}

@Composable
fun DeviceManagementScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    if (state.showForgetDialog) AlertDialog(
        onDismissRequest = { state.showForgetDialog = false },
        title = { Text("Forget DriveAlert Device?") },
        text = { Text("You will need to set up the device again before starting a monitoring session.") },
        confirmButton = { TextButton(onClick = { state.resetDevice(); navigate(Route.DriverHome) }) { Text("Forget Device", color = DriveRed) } },
        dismissButton = { TextButton(onClick = { state.showForgetDialog = false }) { Text("Cancel") } },
    )
    AppPage("Device management", "DriveAlert Device", onBack = { navigate(Route.DriverSettings) }) {
        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Devices, null, tint = DriveRed, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("DriveAlert Device", style = MaterialTheme.typography.titleLarge); Text("DA-001", color = TextSecondary) }; StatusPill(if (state.deviceConnected) "Connected" else "Disconnected", if (state.deviceConnected) Success else DriveRed, if (state.deviceConnected) SuccessSoft else DriveRedSoft) }
            Divider(); LabeledValue("Provisioning Status", if (state.deviceConnected) "Provisioned" else "Setup Required")
            LabeledValue("Last Connected", "Today, 10:35 AM")
        }
        PrimaryButton("Reconnect Device") { navigate(Route.Connect) }
        SecondaryButton("Reconfigure Wi-Fi") { navigate(Route.Connect) }
        SecondaryButton("Driver Access") { navigate(Route.DriverAccess) }
        SecondaryButton("Remove / Forget Device") { state.showForgetDialog = true }
    }
}

@Composable
fun DriverAccessScreen(navigate: (String) -> Unit) {
    AppPage("Driver access", "Separate profiles for one device", onBack = { navigate(Route.DeviceManagement) }) {
        MockData.drivers.forEach { driver ->
            SectionCard { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(CircleShape).background(InkRaised), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, tint = TextSecondary) }; Spacer(Modifier.width(12.dp)); Column { Text(driver.name, style = MaterialTheme.typography.titleMedium); Text(driver.roleLabel, color = if (driver.roleLabel.startsWith("Primary")) Success else TextSecondary) } } }
        }
        SectionCard { Text("Profiles remain separate", style = MaterialTheme.typography.titleMedium); Text("Each Driver keeps a separate calibration, alert history, settings, Trusted Contacts, and monitoring sessions.", color = TextSecondary) }
        PrimaryButton("Add Driver") { navigate(Route.AddDriver) }
        SecondaryButton("Manage Subscription") { navigate(Route.Subscription) }
    }
}

@Composable
fun AddDriverScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    var email by remember { mutableStateOf("") }; var noSlot by remember { mutableStateOf(false) }
    AppPage("Add Driver Access", "Invite another Driver profile", onBack = { navigate(Route.DriverAccess) }) {
        FormField(email, { email = it; state.driverInviteSent = false }, "Email or Driver identifier")
        if (noSlot) SectionCard { StatusPill("Subscription required", Warning, WarningSoft, Icons.Rounded.LockClock); Text("Additional Driver access requires an active subscription.", color = TextSecondary); SecondaryButton("View Subscription") { navigate(Route.Subscription) } }
        if (state.driverInviteSent) SectionCard { StatusPill("Invitation ready", Success, SuccessSoft, Icons.Rounded.CheckCircle); Text("No real invitation was sent.", color = TextSecondary) }
        PrimaryButton("Send Invitation", enabled = email.isNotBlank()) { state.driverInviteSent = true }
        SecondaryButton(if (noSlot) "Show Available Slot" else "Demonstrate No Slot") { noSlot = !noSlot }
    }
}

@Composable
fun SubscriptionScreen(navigate: (String) -> Unit) {
    var managed by remember { mutableStateOf(false) }
    AppPage("Driver subscription", "Additional access for your device", onBack = { navigate(Route.DriverAccess) }) {
        SectionCard { Text("Your Device", style = MaterialTheme.typography.titleLarge); LabeledValue("Primary Driver", "Included", Success); LabeledValue("Additional Driver Slots", "2") }
        SectionCard { StatusPill("Current plan", Success, SuccessSoft, Icons.Rounded.CheckCircle); Text("Family Driver Access", style = MaterialTheme.typography.titleLarge); Text("Primary Driver plus two additional Driver profiles. Pricing will be finalized later.", color = TextSecondary) }
        SectionCard { Text("Need Another Driver?", style = MaterialTheme.typography.titleLarge); Text("Explore a future plan with more Driver slots. No payment or billing action occurs here.", color = TextSecondary) }
        PrimaryButton(if (managed) "Subscription Demo Updated" else "Manage Subscription") { managed = true }
    }
}

@Composable
fun DriverSettingsScreen(navigate: (String) -> Unit) {
    AppPage("Driver settings", role = AccountRole.Driver, currentRoute = Route.DriverSettings, onNavigate = navigate) {
        SettingsRow(Icons.Rounded.Person, "Account / Profile") { navigate(Route.Profile) }
        SettingsRow(Icons.Rounded.Devices, "Device Management") { navigate(Route.DeviceManagement) }
        SettingsRow(Icons.Rounded.LockClock, "Alert History Retention") { navigate(Route.Retention) }
        SettingsRow(Icons.Rounded.Notifications, "Notification Settings") { navigate(Route.Notifications) }
        SettingsRow(Icons.AutoMirrored.Rounded.VolumeUp, "Warning Sound Selection") { navigate(Route.WarningSound) }
        SettingsRow(Icons.Rounded.Info, "About DriveAlert") { navigate(Route.About) }
        SettingsRow(Icons.Rounded.Groups, "Switch to Trusted Contact View") { navigate(Route.ConnectedDrivers) }
        Spacer(Modifier.height(4.dp)); SecondaryButton("Log Out") { navigate(Route.Starter) }
    }
}

@Composable private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    SectionCard(onClick = onClick) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = DriveRed); Spacer(Modifier.width(12.dp)); Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = TextMuted) } }
}

@Composable
fun RetentionScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    val options = listOf("1 Day", "7 Days", "30 Days", "Until Manually Deleted")
    AppPage("Alert History Retention", "Choose how long saved history remains available", onBack = { navigate(Route.DriverSettings) }) {
        Text("This selection changes frontend state only. No records will be deleted.", color = TextSecondary)
        options.forEach { option ->
            SectionCard(onClick = { state.retention = option }) { Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(state.retention == option, { state.retention = option }); Spacer(Modifier.width(8.dp)); Text(option, style = MaterialTheme.typography.titleMedium) } }
        }
        PrimaryButton("Save Selection") { navigate(Route.DriverSettings) }
    }
}

@Composable
fun WarningSoundScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    val sounds = listOf("Alert Tone 1", "Alert Tone 2", "Digital Beep")
    AppPage("Warning Alert Selection", "Choose the standard base warning sound", onBack = { navigate(Route.DriverSettings) }) {
        sounds.forEach { sound ->
            SectionCard(onClick = { state.warningSound = sound }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(state.warningSound == sound, { state.warningSound = sound }); Text(sound, modifier = Modifier.weight(1f))
                    IconButton(onClick = { state.soundPreviewing = if (state.soundPreviewing == sound) null else sound }) { Icon(if (state.soundPreviewing == sound) Icons.Rounded.PauseCircle else Icons.Rounded.PlayArrow, "Preview $sound", tint = DriveRed) }
                }
                if (state.soundPreviewing == sound) Text("Visual preview only. No sound is playing.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Text("Volume level", style = MaterialTheme.typography.titleMedium)
        listOf("Minimum", "Medium", "High").forEach { level -> SectionCard(onClick = { state.volume = level }) { Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(state.volume == level, { state.volume = level }); Text(level) } } }
        PrimaryButton("Save Selection") { navigate(Route.DriverSettings) }
    }
}

@Composable
fun NotificationsScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    AppPage("Notification settings", "Local visual preferences", onBack = { navigate(if (state.accountRole == AccountRole.TrustedContact) Route.ContactSettings else Route.DriverSettings) }) {
        ToggleRow("App notifications", "Show local prototype notification states", state.notificationsEnabled) { state.notificationsEnabled = it }
        ToggleRow("Escalation updates", "Highlight synchronized Escalated Alert Records", state.escalationNotifications) { state.escalationNotifications = it }
        SectionCard { Text("No push notifications", style = MaterialTheme.typography.titleMedium); Text("These controls do not request notification permission or contact a service.", color = TextSecondary) }
    }
}

@Composable private fun ToggleRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    SectionCard { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary) }; Switch(checked, onChecked) } }
}

@Composable
fun AboutScreen(navigate: (String) -> Unit) {
    AppPage("About DriveAlert", "UI prototype", onBack = { navigate(Route.DriverSettings) }) {
        Spacer(Modifier.height(18.dp)); DriveAlertLogo(Modifier.align(Alignment.CenterHorizontally))
        Text("DriveAlert helps Drivers prepare a monitoring device, understand readiness, and review confirmed warning history in clear language.", color = TextSecondary, textAlign = TextAlign.Center)
        SectionCard { LabeledValue("Prototype Version", "1.0"); LabeledValue("Data Source", "Local mock data"); LabeledValue("Network Activity", "None") }
        SectionCard { StatusPill("UI only", Info, InfoSoft, Icons.Rounded.Info); Text("Detection, synchronization, device communication, authentication, and billing are intentionally outside this phase.", color = TextSecondary) }
    }
}

@Composable
fun ProfileScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    var first by remember { mutableStateOf("Lhord") }; var middle by remember { mutableStateOf("") }; var last by remember { mutableStateOf("Driver") }; var phone by remember { mutableStateOf("0917 555 0101") }
    AppPage("Account / Profile", "Local demonstration profile", onBack = { navigate(if (state.accountRole == AccountRole.TrustedContact) Route.ContactSettings else Route.DriverSettings) }) {
        FormField(first, { first = it; state.profileSaved = false }, "First name", enabled = state.profileEditing)
        FormField(middle, { middle = it; state.profileSaved = false }, "Middle name", enabled = state.profileEditing)
        FormField(last, { last = it; state.profileSaved = false }, "Last name", enabled = state.profileEditing)
        FormField(phone, { phone = it; state.profileSaved = false }, "Phone number", enabled = state.profileEditing)
        if (state.profileSaved) StatusPill("Profile updated locally", Success, SuccessSoft, Icons.Rounded.CheckCircle)
        SecondaryButton(if (state.profileEditing) "Cancel Editing" else "Edit Profile") { state.profileEditing = !state.profileEditing }
        PrimaryButton("Save Changes", enabled = state.profileEditing) { state.profileEditing = false; state.profileSaved = true }
    }
}
