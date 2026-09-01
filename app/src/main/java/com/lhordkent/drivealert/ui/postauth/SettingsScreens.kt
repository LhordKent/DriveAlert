package com.lhordkent.drivealert.ui.postauth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.postauth.NotificationPreferences
import com.lhordkent.drivealert.postauth.PreferredVolume
import com.lhordkent.drivealert.postauth.UserView
import com.lhordkent.drivealert.postauth.WarningSound
import com.lhordkent.drivealert.ui.theme.Border
import com.lhordkent.drivealert.ui.theme.DriveRed
import com.lhordkent.drivealert.ui.theme.DriveRedSoft
import com.lhordkent.drivealert.ui.theme.TextMuted
import com.lhordkent.drivealert.ui.theme.TextPrimary
import com.lhordkent.drivealert.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    view: UserView,
    onSwitchView: () -> Unit,
    onAccount: () -> Unit,
    onSetupDevice: () -> Unit,
    onNotifications: () -> Unit,
    onWarningSound: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val isDriver = view == UserView.DRIVER

    SectionTitle("View")
    SettingsRow(
        title = if (isDriver) "Switch to Trusted Contact view" else "Switch to Driver view",
        supportingText = if (isDriver) "Review connected Drivers and shared Stage 3 records" else "Return to setup, monitoring preview, and Driver alerts",
        onClick = onSwitchView,
    )
    HorizontalDivider(color = Border)
    Spacer(Modifier.height(22.dp))
    SectionTitle("Account and preferences")
    SettingsRow("Account", onAccount, supportingText = "Name and signed-in email")
    if (isDriver) SettingsRow("Setup & Device", onSetupDevice, supportingText = "Connection, camera alignment, and notification readiness")
    SettingsRow("Notifications", onNotifications, supportingText = "Choose which interface updates are enabled")
    if (isDriver) SettingsRow("Warning Sound", onWarningSound, supportingText = "Sound and preferred device volume")
    SettingsRow("About DriveAlert", onAbout, supportingText = "Purpose, Warning Stages, privacy, and limitations")
    HorizontalDivider(color = Border)
    Spacer(Modifier.height(22.dp))
    SettingsRow("Log out", { confirmLogout = true }, destructive = true)
    Spacer(Modifier.height(24.dp))

    if (confirmLogout) ConfirmDialog(
        title = "Log out of DriveAlert?",
        body = "Your session-only post-auth changes will be cleared.",
        confirmLabel = "Log out",
        onConfirm = { onLogout(); confirmLogout = false },
        onDismiss = { confirmLogout = false },
    )
}

@Composable
fun AccountScreen(displayName: String, email: String, onBack: () -> Unit) {
    ScrollableScreen(title = "Account", onBack = onBack) {
        Text("Account profile", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("This profile is linked to your signed-in DriveAlert account.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        KeyValueRow("Name", displayName.ifBlank { "Not available" })
        HorizontalDivider(color = Border)
        KeyValueRow("Email", email.ifBlank { "Not available" })
        HorizontalDivider(color = Border)
        Spacer(Modifier.height(18.dp))
        Text("Profile editing is not available in this backend phase.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun NotificationSettingsScreen(
    view: UserView,
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit,
    onBack: () -> Unit,
) {
    ScrollableScreen(title = "Notifications", onBack = onBack) {
        Text(
            "These preferences affect interface state only. DriveAlert does not request Android notification permission in this phase.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(22.dp))
        if (view == UserView.DRIVER) {
            PreferenceSwitch(
                title = "Setup and device reminders",
                body = "Reminders about setup readiness and device connection.",
                checked = preferences.setupAndDeviceReminders,
                onCheckedChange = { onPreferencesChange(preferences.copy(setupAndDeviceReminders = it)) },
            )
            PreferenceSwitch(
                title = "Contact request updates",
                body = "Updates when a Trusted Contact request changes.",
                checked = preferences.driverRequestUpdates,
                onCheckedChange = { onPreferencesChange(preferences.copy(driverRequestUpdates = it)) },
            )
        } else {
            PreferenceSwitch(
                title = "Connection requests",
                body = "Updates when a Driver requests a connection.",
                checked = preferences.trustedRequestUpdates,
                onCheckedChange = { onPreferencesChange(preferences.copy(trustedRequestUpdates = it)) },
            )
            PreferenceSwitch(
                title = "New shared Stage 3 records",
                body = "Updates when an eligible record is received from a connected Driver.",
                checked = preferences.sharedStage3Records,
                onCheckedChange = { onPreferencesChange(preferences.copy(sharedStage3Records = it)) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PreferenceSwitch(title: String, body: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { onCheckedChange(!checked) }.padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = DriveRed),
        )
    }
    HorizontalDivider(color = Border)
}

@Composable
fun WarningSoundScreen(
    selectedSound: WarningSound,
    selectedVolume: PreferredVolume,
    onSoundSelected: (WarningSound) -> Unit,
    onVolumeSelected: (PreferredVolume) -> Unit,
    onBack: () -> Unit,
) {
    ScrollableScreen(title = "Warning Sound", onBack = onBack) {
        Text(
            "Choose the preferred sound and global device volume. This screen does not play audio or control hardware.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(22.dp))
        SectionTitle("Sound")
        WarningSound.values().forEach { sound ->
            RadioChoice(sound.label, selectedSound == sound) { onSoundSelected(sound) }
        }
        Spacer(Modifier.height(22.dp))
        SectionTitle("Preferred device volume")
        PreferredVolume.values().forEach { volume ->
            RadioChoice(volume.label, selectedVolume == volume) { onVolumeSelected(volume) }
        }
        Spacer(Modifier.height(16.dp))
        GroupSurface {
            Text("Device application", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(5.dp))
            Text("The selected sound and volume are saved for this session and will be applied when hardware control is connected.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(16.dp))
        GroupSurface {
            Text("Minimum is enforced", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(5.dp))
            Text("The warning cannot be muted or reduced below Minimum during active monitoring. No fixed dBA value is assigned.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RadioChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = DriveRed, unselectedColor = TextMuted),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}

@Composable
fun AboutDriveAlertScreen(onBack: () -> Unit) {
    ScrollableScreen(title = "About DriveAlert", onBack = onBack) {
        Text("The Quiet Co-Pilot", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "DriveAlert supports setup, monitoring readiness, confirmed warning history, and limited Trusted Contact visibility.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(Modifier.height(26.dp))
        SectionTitle("Visible signs")
        Spacer(Modifier.height(8.dp))
        Text("Prolonged Eye Closure\nYawning\nHead Nodding", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        Spacer(Modifier.height(26.dp))
        SectionTitle("Warning Stages")
        Spacer(Modifier.height(8.dp))
        StageExplanation("Stage 1", "Initial Warning")
        StageExplanation("Stage 2", "Stronger Warning")
        StageExplanation("Stage 3", "Maximum Warning Stage")
        Spacer(Modifier.height(10.dp))
        Text(
            "Warning Stages represent increasing system intervention, not drowsiness severity. Stage 3 transition and persistence records may become eligible for Trusted Contact sharing.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(26.dp))
        SectionTitle("Limits and privacy")
        Spacer(Modifier.height(8.dp))
        Text(
            "DriveAlert does not diagnose whether a Driver is drowsy. Trusted Contacts receive only approved Stage 3 transition or persistence record details: date, time, detected visible signs, and Warning Stage. Camera media, location, and raw vision measurements are not included.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(26.dp))
        KeyValueRow("App version", "1.0")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StageExplanation(stage: String, meaning: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stage, style = MaterialTheme.typography.labelLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(meaning, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1.5f))
    }
}
