package com.lhordkent.drivealert.ui.postauth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.postauth.ConnectedDriver
import com.lhordkent.drivealert.postauth.ConnectionRequest
import com.lhordkent.drivealert.postauth.Contact
import com.lhordkent.drivealert.postauth.RequestDirection
import com.lhordkent.drivealert.postauth.Stage3SyncRecord
import com.lhordkent.drivealert.postauth.UserView
import com.lhordkent.drivealert.ui.theme.Border
import com.lhordkent.drivealert.ui.theme.DriveRed
import com.lhordkent.drivealert.ui.theme.Surface
import com.lhordkent.drivealert.ui.theme.TextMuted
import com.lhordkent.drivealert.ui.theme.TextPrimary
import com.lhordkent.drivealert.ui.theme.TextSecondary
import java.time.format.DateTimeFormatter

@Composable
fun DriverContactsScreen(
    contacts: List<Contact>,
    incoming: List<ConnectionRequest>,
    outgoing: List<ConnectionRequest>,
    onInvite: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    cloudErrorMessage: String? = null,
) {
    var pendingRemove by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAccept by rememberSaveable { mutableStateOf<String?>(null) }
    CloudErrorMessage(cloudErrorMessage)
    PrimaryButton("Invite a Trusted Contact", onInvite)
    Spacer(Modifier.height(26.dp))
    SectionTitle("Approved")
    if (contacts.isEmpty()) {
        EmptyState("No approved contacts", "Invite someone you trust to receive future eligible Stage 3 transition or persistence records.")
    } else {
        contacts.forEach { contact ->
            ConnectionIdentityRow(contact.name, contact.email, "Approved") {
                TextButton(onClick = { pendingRemove = contact.id }) { Text("Remove", color = DriveRed) }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    SectionTitle("Requests to you")
    if (incoming.isEmpty()) {
        Text("No incoming requests", style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(vertical = 16.dp))
    } else incoming.forEach { request ->
        RequestRow(request, onAccept = { pendingAccept = request.id }, onDecline = { onDecline(request.id) })
    }
    Spacer(Modifier.height(20.dp))
    SectionTitle("Sent requests")
    if (outgoing.isEmpty()) {
        Text("No sent requests", style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(vertical = 16.dp))
    } else outgoing.forEach { request ->
        RequestRow(request, onCancel = { onCancel(request.id) })
    }
    Spacer(Modifier.height(20.dp))
    Text("Expanded contact options are coming soon.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
    Spacer(Modifier.height(24.dp))

    pendingRemove?.let { id ->
        ConfirmDialog(
            title = "Remove Trusted Contact?",
            body = "This contact will lose access to previously shared records. Your Driver Alert History will remain unchanged.",
            confirmLabel = "Remove",
            onConfirm = { onRemove(id); pendingRemove = null },
            onDismiss = { pendingRemove = null },
        )
    }
    pendingAccept?.let { id ->
        ConfirmDialog(
            title = "Approve Trusted Contact?",
            body = "This approves the connection and future eligible Stage 3 transition or persistence records. Earlier Driver history will not be shared.",
            confirmLabel = "Approve",
            onConfirm = { onAccept(id); pendingAccept = null },
            onDismiss = { pendingAccept = null },
        )
    }
}

@Composable
fun TrustedDriversScreen(
    drivers: List<ConnectedDriver>,
    onDriverSelected: (String) -> Unit,
    onInvite: () -> Unit,
    onRemove: (String) -> Unit,
    cloudErrorMessage: String? = null,
) {
    var pendingRemove by rememberSaveable { mutableStateOf<String?>(null) }
    CloudErrorMessage(cloudErrorMessage)
    Text(
        "Review Stage 3 transition and persistence records shared by connected Drivers.",
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
    )
    Spacer(Modifier.height(18.dp))
    SecondaryButton("Invite a Driver", onInvite)
    Spacer(Modifier.height(24.dp))
    if (drivers.isEmpty()) {
        EmptyState("No connected Drivers", "Invite a Driver or accept a request to begin reviewing shared Stage 3 transition or persistence records.")
    } else drivers.forEach { driver ->
        Column(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onDriverSelected(driver.id) }.padding(vertical = 15.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(driver.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(driver.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                StatusPill("${driver.sharedRecords.size} shared", StatusTone.INFO)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    driver.sharedRecords.maxByOrNull { it.occurredAt }?.let { "Latest ${it.occurredAt.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))}" } ?: "No shared records yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                TextButton(onClick = { pendingRemove = driver.connectionId }) { Text("Disconnect", color = DriveRed) }
            }
            HorizontalDivider(color = Border)
        }
    }
    Spacer(Modifier.height(24.dp))

    pendingRemove?.let { id ->
        ConfirmDialog(
            title = "Disconnect Driver?",
            body = "You will lose access to this Driver's previously shared records.",
            confirmLabel = "Disconnect",
            onConfirm = { onRemove(id); pendingRemove = null },
            onDismiss = { pendingRemove = null },
        )
    }
}

@Composable
fun RequestsScreen(
    incoming: List<ConnectionRequest>,
    outgoing: List<ConnectionRequest>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCancel: (String) -> Unit,
    cloudErrorMessage: String? = null,
) {
    CloudErrorMessage(cloudErrorMessage)
    SectionTitle("Requests to you")
    Text(
        "Accepting a Driver connects the accounts and allows future eligible Stage 3 transition or persistence records to be shared.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
    )
    if (incoming.isEmpty()) {
        EmptyState("No pending requests", "New connection requests will appear here.")
    } else incoming.forEach { request ->
        RequestRow(request, onAccept = { onAccept(request.id) }, onDecline = { onDecline(request.id) })
    }
    Spacer(Modifier.height(24.dp))
    SectionTitle("Sent requests")
    if (outgoing.isEmpty()) {
        Text("No sent requests", style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(vertical = 16.dp))
    } else outgoing.forEach { request ->
        RequestRow(request, onCancel = { onCancel(request.id) })
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun CloudErrorMessage(message: String?) {
    if (message == null) return
    Text(message, style = MaterialTheme.typography.bodyMedium, color = DriveRed)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun RequestRow(
    request: ConnectionRequest,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(request.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(request.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(5.dp))
        Text(
            if (request.direction == RequestDirection.INCOMING) "Invited you to connect" else "Waiting for approval",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
        Spacer(Modifier.height(10.dp))
        if (onAccept != null && onDecline != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Accept", onAccept, Modifier.weight(1f))
                SecondaryButton("Decline", onDecline, Modifier.weight(1f))
            }
        } else if (onCancel != null) {
            SecondaryButton("Cancel request", onCancel)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Border)
    }
}

@Composable
private fun ConnectionIdentityRow(name: String, email: String, status: String, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            StatusPill(status, StatusTone.SUCCESS)
        }
        action()
    }
    HorizontalDivider(color = Border)
}

@Composable
fun InviteConnectionScreen(
    targetView: UserView,
    onSend: (String) -> String?,
    onBack: () -> Unit,
) {
    val isDriverView = targetView == UserView.DRIVER
    var email by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    fun submit() {
        error = onSend(email)
        if (error == null) {
            focus.clearFocus()
            onBack()
        }
    }
    ScrollableScreen(
        title = if (isDriverView) "Invite a Trusted Contact" else "Invite a Driver",
        onBack = onBack,
    ) {
        Text(
            if (isDriverView) {
                "Enter the email of someone you trust. After approval, future eligible Stage 3 transition or persistence records can be shared."
            } else {
                "Enter a Driver's email. They must approve the connection before future eligible Stage 3 transition or persistence records can be shared."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text(if (isDriverView) "Trusted Contact email" else "Driver email") },
            isError = error != null,
            supportingText = error?.let { message -> { Text(message) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().testTag(PostAuthTestTags.INVITE_EMAIL),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                focusedBorderColor = DriveRed,
                unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Send Request", { submit() })
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Cancel", onBack)
    }
}

@Composable
fun SharedRecordsScreen(
    driver: ConnectedDriver,
    onEventSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    ScrollableScreen(title = "Shared Alerts", onBack = onBack) {
        Text(driver.name, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(driver.email, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(18.dp))
        GroupSurface {
            KeyValueRow("Received records", driver.sharedRecords.size.toString())
            KeyValueRow("Latest received", driver.sharedRecords.maxByOrNull { it.receivedAt ?: it.occurredAt }?.receivedAt?.format(DateTimeFormatter.ofPattern("MMM d, h:mm a")) ?: "None")
        }
        Spacer(Modifier.height(22.dp))
        SectionTitle("Stage 3 synchronization timeline")
        Spacer(Modifier.height(6.dp))
        if (driver.sharedRecords.isEmpty()) {
            EmptyState("No shared records", "Future eligible Stage 3 transition or persistence records will appear after they are received.")
        } else driver.sharedRecords.sortedByDescending { it.occurredAt }.forEach { record ->
            Stage3SyncRecordRow(record, onClick = { onEventSelected(record.id) })
            HorizontalDivider(color = Border)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stage3SyncRecordRow(record: Stage3SyncRecord, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(record.kind.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(record.occurredAt.format(DateTimeFormatter.ofPattern("MMM d, h:mm a")), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            SharingPill(record.sharingState)
        }
        Spacer(Modifier.height(7.dp))
        Text(record.signs.joinToString { it.label }, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
