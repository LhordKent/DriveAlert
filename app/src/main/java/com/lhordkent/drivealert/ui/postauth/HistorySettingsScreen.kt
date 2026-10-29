package com.lhordkent.drivealert.ui.postauth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.postauth.PostAuthUiState
import com.lhordkent.drivealert.ui.theme.*

@Composable
fun HistorySettingsScreen(state: PostAuthUiState, onSave: (Int?) -> Unit, onConfirm: () -> Unit,
    onDismiss: () -> Unit, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(0) }
    var custom by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.historyPreferencesLoaded, state.historyRetentionDays) {
        val days = state.historyRetentionDays
        selected = when (days) { null -> 0; 1, 3, 7 -> days; else -> -1 }
        custom = if (selected == -1) days.toString() else ""
    }
    val days = if (selected == 0) null else if (selected == -1) custom.toIntOrNull() else selected
    val valid = selected != -1 || days != null && days in 1..3650
    val enabled = state.historyPreferencesLoaded && !state.historyBusy
    ScrollableScreen(title = "History & Storage", onBack = onBack) {
        Text("Choose how long to keep history", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Applies to your Driver Alert History and Trusted Contact shared history on this phone.", color = TextSecondary)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.selectableGroup()) {
            listOf(1 to "1 day", 3 to "3 days", 7 to "7 days", -1 to "Custom", 0 to "Keep until I delete").forEach { (value, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(selected == value, enabled = enabled, role = Role.RadioButton, onClick = { selected = value })
                    .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == value, onClick = null, enabled = enabled)
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (selected == -1) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(custom, { custom = it.take(5) }, label = { Text("Number of days") },
                supportingText = { Text("Enter 1 to 3,650 whole days.") }, isError = custom.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                enabled = enabled, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(20.dp))
        Text(if (selected == 0) "History stays on this phone until you delete it. You can delete individual records or clear history at any time."
            else "Older history is removed automatically. Each day means 24 hours from the event. Background cleanup may be delayed by Android.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Text("Deletion cannot be undone. Choosing a longer period will not restore removed history. Shared records remain available to their driver and other contacts.",
            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(10.dp))
        Text("Current drive records are kept until monitoring ends. Calibration, device setup and Android system notifications are unaffected.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.height(20.dp))
        HistoryFeedback(state.historyError, state.historyMessage)
        PrimaryButton(if (state.historyBusy) "Saving…" else "Save", { onSave(days) },
            enabled = enabled && valid && days != state.historyRetentionDays)
        if (!state.historyPreferencesLoaded) Text("Loading history settings…", color = TextSecondary)
        Spacer(Modifier.height(24.dp))
    }
    state.historyPreview?.let { preview ->
        ConfirmDialog("Apply automatic deletion?",
            "This removes ${preview.affectedRecords} records from currently loaded history. Older shared records received later will also be hidden. Current drive records are kept until monitoring ends. Deletion cannot be undone." +
                (state.historyError?.let { "\n\n$it" } ?: ""),
            if (state.historyBusy) "Saving…" else "Save and apply", onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
fun HistoryFeedback(error: String?, message: String?) {
    error?.let { Text(it, color = DriveRed, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(12.dp)) }
    message?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(12.dp)) }
}

@Composable
fun HistoryDeleteButton(label: String, body: String, enabled: Boolean = true, onConfirm: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SecondaryButton(label, { confirming = true }, enabled = enabled)
    if (confirming) ConfirmDialog("$label?", "$body Deletion cannot be undone.", label,
        onConfirm = { confirming = false; onConfirm() }, onDismiss = { confirming = false })
}
