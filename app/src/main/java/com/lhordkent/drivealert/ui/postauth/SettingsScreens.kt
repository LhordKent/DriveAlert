package com.lhordkent.drivealert.ui.postauth

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.BuildConfig
import com.lhordkent.drivealert.data.connection.ConnectionCode
import com.lhordkent.drivealert.monitoring.WarningDeliveryStatus
import com.lhordkent.drivealert.postauth.NotificationPreferences
import com.lhordkent.drivealert.postauth.PreferredVolume
import com.lhordkent.drivealert.postauth.UserView
import com.lhordkent.drivealert.postauth.WarningSound
import com.lhordkent.drivealert.postauth.WarningStage
import com.lhordkent.drivealert.ui.auth.AuthTextField
import com.lhordkent.drivealert.ui.theme.Border
import com.lhordkent.drivealert.ui.theme.DriveRed
import com.lhordkent.drivealert.ui.theme.DriveRedSoft
import com.lhordkent.drivealert.ui.theme.Success
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
fun AccountScreen(
    firstName: String,
    middleName: String,
    lastName: String,
    phoneNumber: String,
    displayName: String,
    email: String,
    connectionCode: String,
    profileErrorMessage: String?,
    isProfileUpdating: Boolean,
    profileUpdateSuccessMessage: String?,
    profileUpdateErrorMessage: String?,
    onUpdateProfile: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftFirstName by rememberSaveable { mutableStateOf(firstName) }
    var draftMiddleName by rememberSaveable { mutableStateOf(middleName) }
    var draftLastName by rememberSaveable { mutableStateOf(lastName) }
    var draftPhoneNumber by rememberSaveable { mutableStateOf(phoneNumber) }
    var attemptedSave by rememberSaveable { mutableStateOf(false) }
    val formattedCode = ConnectionCode.format(connectionCode)

    LaunchedEffect(firstName, middleName, lastName, phoneNumber, editing) {
        if (!editing) {
            draftFirstName = firstName
            draftMiddleName = middleName
            draftLastName = lastName
            draftPhoneNumber = phoneNumber
        }
    }
    LaunchedEffect(profileUpdateSuccessMessage) {
        if (profileUpdateSuccessMessage != null) editing = false
    }

    ScrollableScreen(title = "Account", onBack = onBack) {
        Text("Account profile", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("This profile is linked to your signed-in DriveAlert account.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        if (editing) {
            AuthTextField(
                value = draftFirstName,
                onValueChange = { draftFirstName = it },
                label = "First name",
                error = if (attemptedSave && draftFirstName.isBlank()) "First name is required" else null,
                enabled = !isProfileUpdating,
            )
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = draftMiddleName,
                onValueChange = { draftMiddleName = it },
                label = "Middle name (optional)",
                enabled = !isProfileUpdating,
            )
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = draftLastName,
                onValueChange = { draftLastName = it },
                label = "Last name",
                error = if (attemptedSave && draftLastName.isBlank()) "Last name is required" else null,
                enabled = !isProfileUpdating,
            )
            Spacer(Modifier.height(12.dp))
            AuthTextField(
                value = draftPhoneNumber,
                onValueChange = { draftPhoneNumber = it },
                label = "Phone number (optional)",
                enabled = !isProfileUpdating,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Signed in as ${email.ifBlank { "an unavailable email address" }}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = {
                        draftFirstName = firstName
                        draftMiddleName = middleName
                        draftLastName = lastName
                        draftPhoneNumber = phoneNumber
                        attemptedSave = false
                        editing = false
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProfileUpdating,
                )
                PrimaryButton(
                    text = if (isProfileUpdating) "Saving…" else "Save changes",
                    onClick = {
                        attemptedSave = true
                        if (draftFirstName.isNotBlank() && draftLastName.isNotBlank()) {
                            onUpdateProfile(draftFirstName, draftMiddleName, draftLastName, draftPhoneNumber)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProfileUpdating,
                )
            }
        } else {
            KeyValueRow("Name", displayName.ifBlank { "Not available" })
            HorizontalDivider(color = Border)
            KeyValueRow("Email", email.ifBlank { "Not available" })
            HorizontalDivider(color = Border)
            KeyValueRow("Phone", phoneNumber.ifBlank { "Not provided" })
            Spacer(Modifier.height(18.dp))
            SecondaryButton(
                text = "Edit profile",
                onClick = {
                    attemptedSave = false
                    editing = true
                },
            )
        }
        profileUpdateSuccessMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Success)
        }
        profileUpdateErrorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = DriveRed)
        }
        Spacer(Modifier.height(26.dp))
        SectionTitle("My connection code")
        Spacer(Modifier.height(8.dp))
        Text(
            "Share this code with a Driver or Trusted Contact you know. They will still need your approval before a connection becomes active.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        if (connectionCode.isNotBlank()) {
            GroupSurface {
                Text(
                    formattedCode,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = TextPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = if (copied) "Copied" else "Copy code",
                    onClick = {
                        clipboard.setText(AnnotatedString(formattedCode))
                        copied = true
                    },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Share code",
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Connect with me on DriveAlert using this code: $formattedCode")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share DriveAlert code"))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text(
                profileErrorMessage ?: "Creating your connection code…",
                style = MaterialTheme.typography.bodyMedium,
                color = if (profileErrorMessage == null) TextMuted else DriveRed,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun NotificationSettingsScreen(
    view: UserView,
    preferences: NotificationPreferences,
    onPreferencesChange: (NotificationPreferences) -> Unit,
    onRequestNotificationPermission: () -> Unit = {},
    onBack: () -> Unit,
) {
    ScrollableScreen(title = "Notifications", onBack = onBack) {
        Text(
            "Choose which DriveAlert updates can appear as Android notifications.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(22.dp))
        if (view == UserView.DRIVER) {
            PreferenceSwitch(
                title = "Confirmed warning alerts",
                body = "Show the current Warning Stage and confirmed visible signs while monitoring.",
                checked = preferences.warningAlerts,
                onCheckedChange = {
                    if (it) onRequestNotificationPermission()
                    onPreferencesChange(preferences.copy(warningAlerts = it))
                },
            )
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
    debugDelivery: WarningDeliveryStatus? = null,
    onTestWarning: (WarningStage) -> Unit = {},
    onPreviewSelection: (WarningSound, PreferredVolume) -> Unit = { _, _ -> },
) {
    ScrollableScreen(title = "Warning Sound", onBack = onBack) {
        Text(
            "Choose the preferred sound and device volume. Each selection is played through the connected DriveAlert speaker so you can hear it immediately.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(22.dp))
        SectionTitle("Sound")
        WarningSound.values().forEach { sound ->
            RadioChoice(sound.label, selectedSound == sound) {
                onSoundSelected(sound)
                onPreviewSelection(sound, selectedVolume)
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionTitle("Preferred device volume")
        PreferredVolume.values().forEach { volume ->
            RadioChoice(volume.label, selectedVolume == volume) {
                onVolumeSelected(volume)
                onPreviewSelection(selectedSound, volume)
            }
        }
        Spacer(Modifier.height(16.dp))
        GroupSurface {
            Text("Device application", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(5.dp))
            Text("The selected sound and volume are saved and sent to the connected ESP32 for an immediate speaker preview. Previewing does not create an Alert or change the current Warning Stage.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        debugDelivery?.let { delivery ->
            Spacer(Modifier.height(8.dp))
            Text("Speaker preview: ${delivery.label}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Spacer(Modifier.height(16.dp))
        GroupSurface {
            Text("Minimum is enforced", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(5.dp))
            Text("The warning cannot be muted or reduced below Minimum during active monitoring. No fixed dBA value is assigned.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(16.dp))
            GroupSurface {
                Text("Debug speaker test", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Sends the selected sound and volume directly to the connected device. It does not create an Alert or change warning-stage state.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                val debugStages = listOf(
                    WarningStage.STAGE_1 to "Test normal track (Stage 1)",
                    WarningStage.STAGE_2 to "Test Stage 2 limitation",
                    WarningStage.STAGE_3 to "Test mixed track (Stage 3)",
                )
                debugStages.forEach { (stage, label) ->
                    SecondaryButton(label, { onTestWarning(stage) })
                    Spacer(Modifier.height(8.dp))
                }
            }
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
