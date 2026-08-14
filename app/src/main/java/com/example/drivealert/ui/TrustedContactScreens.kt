package com.example.drivealert.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.drivealert.model.AccountRole
import com.example.drivealert.model.AlertSeverity
import com.example.drivealert.model.DriveAlertAppState
import com.example.drivealert.model.MockData
import com.example.drivealert.model.Route
import com.example.drivealert.ui.theme.DriveRed
import com.example.drivealert.ui.theme.DriveRedSoft
import com.example.drivealert.ui.theme.Info
import com.example.drivealert.ui.theme.InfoSoft
import com.example.drivealert.ui.theme.Success
import com.example.drivealert.ui.theme.SuccessSoft
import com.example.drivealert.ui.theme.TextMuted
import com.example.drivealert.ui.theme.TextSecondary
import com.example.drivealert.ui.theme.Warning
import com.example.drivealert.ui.theme.WarningSoft

@Composable
fun ConnectedDriversScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    AppPage("Connected Drivers", "Drivers sharing Escalated Alert Records", role = AccountRole.TrustedContact, currentRoute = Route.ConnectedDrivers, onNavigate = navigate) {
        if (MockData.drivers.isEmpty()) EmptyState(Icons.Rounded.Groups, "No Connected Drivers", "You don't have any connected Drivers yet.")
        else MockData.drivers.forEach { driver ->
            SectionCard(onClick = { state.selectedAlertId = "a3"; navigate(Route.EscalatedRecords) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Person, null, tint = DriveRed, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(driver.name, style = MaterialTheme.typography.titleLarge); Text(driver.email, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = TextMuted)
                }
                Divider(); LabeledValue("Escalated Alerts", driver.escalatedAlerts.toString(), if (driver.escalatedAlerts > 0) DriveRed else Success)
                LabeledValue("Latest Escalated Alert", driver.latestAlert)
                LabeledValue("Latest Sync", driver.latestSync)
            }
        }
    }
}

@Composable
fun PendingRequestsScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    AppPage("Pending Requests", "Manage incoming Driver requests", role = AccountRole.TrustedContact, currentRoute = Route.PendingRequests, onNavigate = navigate) {
        if (state.pendingRequests.isEmpty()) EmptyState(Icons.Rounded.Notifications, "No Pending Requests", "There are currently no Trusted Contact requests.")
        else state.pendingRequests.toList().forEach { request ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Person, null, tint = DriveRed); Spacer(Modifier.width(10.dp)); Column { Text(request.name, style = MaterialTheme.typography.titleMedium); Text(request.email, color = TextSecondary) } }
                Text("Invited you as a Trusted Contact.", color = TextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Accept", Modifier.weight(1f)) { state.pendingRequests.remove(request) }
                    SecondaryButton("Decline", Modifier.weight(1f)) { state.pendingRequests.remove(request) }
                }
            }
        }
    }
}

@Composable
fun EscalatedRecordsScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    val escalated = MockData.alerts.filter { it.severity == AlertSeverity.Escalated }
    AppPage("Escalated Alert Records", "Lhord · synchronized records only", onBack = { navigate(Route.ConnectedDrivers) }, role = AccountRole.TrustedContact, currentRoute = Route.EscalatedRecords, onNavigate = navigate) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Escalated alerts", "2", Modifier.weight(1f), Icons.Rounded.Warning)
            MetricCard("Latest sync", "10:47 AM", Modifier.weight(1f), Icons.Rounded.History)
        }
        SectionCard { StatusPill("Escalated records only", Info, InfoSoft, Icons.Rounded.Info); Text("Initial and Repeated records are not visible in the Trusted Contact view.", color = TextSecondary) }
        if (escalated.isEmpty()) EmptyState(Icons.Rounded.History, "No Escalated Alerts", "No Escalated Alert Records are available for this Driver.")
        else escalated.forEach { alert ->
            SectionCard(onClick = { state.selectedAlertId = alert.id; navigate(Route.EscalatedDetail) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Escalated Alert", style = MaterialTheme.typography.titleLarge); SeverityPill(alert.severity) }
                LabeledValue("Driver", "Lhord"); LabeledValue("Date / Time", "${alert.date} · ${alert.time}")
                LabeledValue("Number of Alerts", "3 confirmed alerts")
                Text(alert.signs.joinToString(" · ") { it.label }, color = TextSecondary)
                LabeledValue("Synchronization", alert.syncTime ?: "Synchronized", Success)
            }
        }
    }
}

@Composable
fun EscalatedDetailScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    val alert = state.selectedAlert
    AppPage("Escalated Alert", "Shared by Lhord", onBack = { navigate(Route.EscalatedRecords) }) {
        SectionCard {
            SeverityPill(AlertSeverity.Escalated)
            Text("3 confirmed alerts within 5 minutes", style = MaterialTheme.typography.headlineSmall)
            Text("This label represents confirmed recurrence, not a medical diagnosis.", color = TextSecondary)
        }
        SectionCard {
            Text("Alert Information", style = MaterialTheme.typography.titleLarge)
            LabeledValue("Driver Name", "Lhord"); LabeledValue("Date", alert.date); LabeledValue("Time", alert.time)
            Divider(); Text("Detected Signs", color = TextSecondary)
            alert.signs.forEach { Text("• ${it.label}") }
        }
        SectionCard {
            Text("Synchronization", style = MaterialTheme.typography.titleLarge)
            StatusPill("Synchronized", Success, SuccessSoft, Icons.Rounded.CheckCircle)
            LabeledValue("Latest Alert Time", alert.time); LabeledValue("Synchronization Time", alert.syncTime ?: "Today, 10:47 AM")
        }
        SecondaryButton("Back to Records") { navigate(Route.EscalatedRecords) }
    }
}

@Composable
fun ContactSettingsScreen(state: DriveAlertAppState, navigate: (String) -> Unit) {
    AppPage("Trusted Contact Settings", role = AccountRole.TrustedContact, currentRoute = Route.ContactSettings, onNavigate = navigate) {
        SettingsRowContact(Icons.Rounded.Groups, "Switch to Driver View") { state.accountRole = AccountRole.Driver; navigate(Route.DriverHome) }
        SettingsRowContact(Icons.Rounded.Person, "Account / Profile") { navigate(Route.Profile) }
        SettingsRowContact(Icons.Rounded.Notifications, "Pending Requests") { navigate(Route.PendingRequests) }
        SettingsRowContact(Icons.Rounded.Settings, "Notification Settings") { navigate(Route.Notifications) }
        Spacer(Modifier.height(4.dp)); SecondaryButton("Log Out") { navigate(Route.Starter) }
    }
}

@Composable private fun SettingsRowContact(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: () -> Unit) {
    SectionCard(onClick = action) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = DriveRed); Spacer(Modifier.width(12.dp)); Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = TextMuted) } }
}
