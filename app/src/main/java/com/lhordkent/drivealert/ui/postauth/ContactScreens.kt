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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lhordkent.drivealert.data.connection.ConnectionContactDetails
import com.lhordkent.drivealert.postauth.ConnectedDriver
import com.lhordkent.drivealert.postauth.ConnectionRequest
import com.lhordkent.drivealert.postauth.ConnectionInviteUiState
import com.lhordkent.drivealert.postauth.Contact
import com.lhordkent.drivealert.postauth.RequestDirection
import com.lhordkent.drivealert.postauth.Stage3SyncRecord
import com.lhordkent.drivealert.ui.theme.Border
import com.lhordkent.drivealert.ui.theme.DriveRed
import com.lhordkent.drivealert.ui.theme.Surface
import com.lhordkent.drivealert.ui.theme.TextMuted
import com.lhordkent.drivealert.ui.theme.TextPrimary
import com.lhordkent.drivealert.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal val sharedAlertDateTimeFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Composable
fun DriverContactsScreen(
    contacts: List<Contact>,
    onInvite: () -> Unit,
    onSentRequests: () -> Unit,
    onRemove: (String) -> Unit,
    details: Map<String, ConnectionContactDetails> = emptyMap(),
    isLoading: Boolean = false,
    actionInProgressIds: Set<String> = emptySet(),
    cloudErrorMessage: String? = null,
) {
    var pendingRemove by rememberSaveable { mutableStateOf<String?>(null) }
    CloudErrorMessage(cloudErrorMessage)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Invite", onInvite, Modifier.weight(1f))
        PrimaryButton("Sent requests", onSentRequests, Modifier.weight(1f))
    }
    Spacer(Modifier.height(26.dp))
    SectionTitle("Contacts")
    if (isLoading && contacts.isEmpty()) {
        LoadingMessage("Loading trusted contacts…")
    } else if (contacts.isEmpty() && cloudErrorMessage == null) {
        EmptyState("No contacts yet", "Invite someone you trust to receive shared Stage 3 alerts.")
    } else {
        contacts.forEach { contact ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionIdentity(contact.name, contact.email, details[contact.id], Modifier.weight(1f))
                TextButton(
                    onClick = { pendingRemove = contact.id },
                    enabled = contact.id !in actionInProgressIds,
                ) {
                    Text(if (contact.id in actionInProgressIds) "Removing…" else "Remove", color = DriveRed)
                }
            }
            HorizontalDivider(color = Border)
        }
    }
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
}

@Composable
fun SentConnectionRequestsScreen(
    outgoing: List<ConnectionRequest>,
    onCancel: (String) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean,
    actionInProgressIds: Set<String>,
    cloudErrorMessage: String?,
) {
    ScrollableScreen("Sent requests", onBack) {
        CloudErrorMessage(cloudErrorMessage)
        when {
            isLoading && outgoing.isEmpty() -> LoadingMessage("Loading sent requests…")
            outgoing.isEmpty() && cloudErrorMessage == null -> EmptyState(
                "No sent requests",
                "Invitations waiting for a response will appear here.",
            )
            else -> outgoing.forEach { request ->
                RequestRow(request, actionInProgress = request.id in actionInProgressIds, onCancel = { onCancel(request.id) })
            }
        }
    }
}

@Composable
fun TrustedDriversScreen(
    drivers: List<ConnectedDriver>,
    onDriverSelected: (String) -> Unit,
    onRemove: (String) -> Unit,
    details: Map<String, ConnectionContactDetails> = emptyMap(),
    isLoading: Boolean = false,
    actionInProgressIds: Set<String> = emptySet(),
    cloudErrorMessage: String? = null,
) {
    var pendingRemove by rememberSaveable { mutableStateOf<String?>(null) }
    CloudErrorMessage(cloudErrorMessage)
    Text(
        "Review Stage 3 transition and persistence records shared by connected Drivers.",
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
    )
    Spacer(Modifier.height(24.dp))
    if (isLoading && drivers.isEmpty()) {
        LoadingMessage("Loading connected Drivers…")
    } else if (drivers.isEmpty() && cloudErrorMessage == null) {
        EmptyState("No connected Drivers", "Accept a Driver's request to begin reviewing shared Stage 3 transition or persistence records.")
    } else drivers.forEach { driver ->
        Column(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button) { onDriverSelected(driver.id) }
                .padding(vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionIdentity(driver.name, driver.email, details[driver.connectionId], Modifier.weight(1f))
                TextButton(
                    onClick = { pendingRemove = driver.connectionId },
                    enabled = driver.connectionId !in actionInProgressIds,
                ) {
                    Text(if (driver.connectionId in actionInProgressIds) "Disconnecting…" else "Disconnect", color = DriveRed)
                }
            }
            Spacer(Modifier.height(8.dp))
            val recordCount = if (driver.sharedRecordsLoading) {
                "Loading shared records…"
            } else {
                "${driver.sharedRecords.size} shared"
            }
            val unread = if (driver.unreadRecordCount > 0) " · ${driver.unreadRecordCount} new" else ""
            Text(recordCount + unread, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            if (!driver.sharedRecordsLoading) {
                driver.sharedRecords.maxByOrNull { it.occurredAt }?.let { latest ->
                    Text(
                        "Latest ${latest.occurredAt.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
        }
        HorizontalDivider(color = Border)
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
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    isLoading: Boolean = false,
    actionInProgressIds: Set<String> = emptySet(),
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
    if (isLoading && incoming.isEmpty()) {
        LoadingMessage("Loading requests…")
    } else if (incoming.isEmpty() && cloudErrorMessage == null) {
        EmptyState("No pending requests", "Share the connection code from your Account screen with a Driver so they can invite you.")
    } else incoming.forEach { request ->
        RequestRow(
            request,
            actionInProgress = request.id in actionInProgressIds,
            onAccept = { onAccept(request.id) },
            onDecline = { onDecline(request.id) },
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun LoadingMessage(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(vertical = 16.dp))
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
    actionInProgress: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(request.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        if (request.email.isNotBlank()) Text(request.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.height(5.dp))
        Text(
            if (request.direction == RequestDirection.INCOMING) "Invited you to connect" else "Waiting for approval",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
        )
        Spacer(Modifier.height(10.dp))
        if (onAccept != null && onDecline != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(if (actionInProgress) "Working…" else "Accept", onAccept, Modifier.weight(1f), enabled = !actionInProgress)
                SecondaryButton("Decline", onDecline, Modifier.weight(1f), enabled = !actionInProgress)
            }
        } else if (onCancel != null) {
            SecondaryButton(if (actionInProgress) "Cancelling…" else "Cancel request", onCancel, enabled = !actionInProgress)
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Border)
    }
}

@Composable
private fun ConnectionIdentity(
    name: String,
    email: String,
    details: ConnectionContactDetails?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(details?.email ?: email.ifBlank { "Email unavailable" }, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(details?.phoneNumber ?: "Phone number unavailable", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
internal fun <T> rememberConnectionDetails(
    userId: String?,
    connectionIds: List<String>,
    load: suspend (String, List<String>) -> Map<String, T>,
    refreshKey: Int = 0,
): Map<String, T> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var details by remember(userId, connectionIds) { mutableStateOf(emptyMap<String, T>()) }
    LaunchedEffect(lifecycle, userId, connectionIds, refreshKey) {
        if (userId == null || connectionIds.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            details = try {
                load(userId, connectionIds)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }
    return details
}

@Composable
fun InviteConnectionScreen(
    inviteState: ConnectionInviteUiState,
    onLookup: (String) -> Unit,
    onLookupEmail: (String) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    senderName: String = "",
    senderEmail: String = "",
) {
    var useEmail by rememberSaveable { mutableStateOf(true) }
    var input by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val match = inviteState.emailMatch
    val targetName = match?.displayName ?: inviteState.targetDisplayName
    val canSearch = input.isNotBlank() && !inviteState.isLookingUp && !inviteState.isSending
    DisposableEffect(Unit) {
        onDispose { onClear() }
    }
    LaunchedEffect(inviteState.requestSent) {
        if (inviteState.requestSent) {
            onClear()
            onBack()
        }
    }
    fun lookup() {
        if (!canSearch) return
        focus.clearFocus()
        if (useEmail) onLookupEmail(input) else onLookup(input)
    }
    ScrollableScreen(
        title = "Invite a Trusted Contact",
        onBack = onBack,
    ) {
        Text(
            if (useEmail) "Enter the email address your Trusted Contact uses for DriveAlert."
            else "Enter the connection code from your Trusted Contact’s DriveAlert Account screen.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(Modifier.height(22.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = if (useEmail) it else it.uppercase(); onClear() },
            label = { Text(if (useEmail) "Email address" else "Connection code") },
            placeholder = if (useEmail) null else { { Text("DA-XXXXX-XXXXX-XXXXX-XXXXX-XXXXXX") } },
            isError = inviteState.errorMessage != null,
            supportingText = inviteState.errorMessage?.let { message -> { Text(message) } },
            singleLine = true,
            enabled = !inviteState.isSending,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (useEmail) KeyboardType.Email else KeyboardType.Ascii,
                capitalization = if (useEmail) KeyboardCapitalization.None else KeyboardCapitalization.Characters,
                autoCorrect = false,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { lookup() }),
            modifier = Modifier.fillMaxWidth().testTag(if (useEmail) PostAuthTestTags.INVITE_EMAIL else PostAuthTestTags.INVITE_CODE),
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
        if (targetName == null) {
            SecondaryButton(
                text = when {
                    inviteState.isLookingUp -> "Searching…"
                    useEmail -> "Search"
                    else -> "Find account"
                },
                onClick = ::lookup,
                enabled = canSearch,
            )
        } else {
            GroupSurface {
                if (match == null) {
                    Text("Account found", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                    Spacer(Modifier.height(6.dp))
                }
                Text(targetName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                match?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it.email, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                connectionSharingNotice(senderName, senderEmail),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.testTag(PostAuthTestTags.INVITE_SHARING_NOTICE),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = when {
                    inviteState.isSending -> "Sending invite…"
                    useEmail -> "Send invite"
                    else -> "Send connection request"
                },
                onClick = onSend,
                enabled = !inviteState.isSending,
            )
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = { focus.clearFocus(); useEmail = !useEmail; input = ""; onClear() },
            enabled = !inviteState.isSending,
        ) {
            Text(if (useEmail) "Use a connection code" else "Search by email")
        }
        SecondaryButton("Cancel", { onClear(); onBack() })
    }
}

@Composable
fun SharedRecordsScreen(
    driver: ConnectedDriver,
    onClearHistory: () -> Unit = {},
    historyBusy: Boolean = false,
    historyError: String? = null,
    historyMessage: String? = null,
    onBack: () -> Unit,
) {
    ScrollableScreen(title = "Shared Alerts", onBack = onBack) {
        HistoryFeedback(historyError, historyMessage)
        HistoryDeleteButton("Clear history", "Remove this driver's shared records only from your history on this phone? The driver's record and other contacts' history are kept.",
            enabled = !historyBusy && !driver.sharedRecordsLoading && driver.sharedRecords.isNotEmpty(), onConfirm = onClearHistory)
        Spacer(Modifier.height(20.dp))
        Text(driver.name, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(driver.email, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(18.dp))
        GroupSurface {
            KeyValueRow("Received records", driver.sharedRecords.size.toString())
            KeyValueRow("Unread records", driver.unreadRecordCount.toString())
            KeyValueRow("Latest received", driver.sharedRecords.maxByOrNull { it.receivedAt ?: it.occurredAt }?.receivedAt?.format(DateTimeFormatter.ofPattern("MMM d, h:mm a")) ?: "None")
        }
        Spacer(Modifier.height(22.dp))
        SectionTitle("Stage 3 synchronization timeline")
        Spacer(Modifier.height(6.dp))
        CloudErrorMessage(driver.sharedRecordsErrorMessage)
        if (driver.sharedRecordsLoading && driver.sharedRecords.isEmpty()) {
            LoadingMessage("Loading shared records…")
        } else if (driver.sharedRecords.isEmpty() && driver.sharedRecordsErrorMessage == null) {
            EmptyState("No shared records", "Future eligible Stage 3 transition or persistence records will appear after they are received.")
        } else driver.sharedRecords.sortedByDescending { it.receivedAt ?: it.occurredAt }.forEach { record ->
            Stage3SyncRecordRow(record)
            HorizontalDivider(color = Border)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stage3SyncRecordRow(record: Stage3SyncRecord) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text(record.occurredAt.format(sharedAlertDateTimeFormatter), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(7.dp))
        Text(record.signs.joinToString { it.label }.ifBlank { "No recognized signs" }, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

/** Tells the sender exactly what the other person will see; nothing is typed by the sender. */
internal fun connectionSharingNotice(name: String, email: String): String {
    val shared = listOfNotNull(name.trim().ifBlank { null }?.let { "name ($it)" }, email.trim().ifBlank { null }?.let { "email ($it)" })
    return if (shared.isEmpty()) "Your name and email will be visible to the person you send this request to."
    else "Your ${shared.joinToString(" and ")} will be visible to the person you send this request to."
}
