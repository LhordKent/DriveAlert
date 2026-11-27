package com.lhordkent.drivealert.ui.postauth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.device.DeviceDriversState
import com.lhordkent.drivealert.device.DeviceDriversViewModel
import com.lhordkent.drivealert.device.DeviceDriverInviteTarget
import com.lhordkent.drivealert.ui.theme.*

private val visibleDriverAccess = setOf("PENDING", "ACTIVE")

@Composable
fun DeviceDriversPanel(viewModel: DeviceDriversViewModel, userId: String?) {
    val state by viewModel.state.collectAsState()
    var detailsRefresh by remember { mutableIntStateOf(0) }
    val accessIds = state.overview.managed.filter {
        it.ownerUserId == userId && it.deviceId == state.selectedDeviceId && it.status in visibleDriverAccess
    }.map { it.id }.sorted()
    val phoneNumbers = rememberConnectionDetails(userId, accessIds, viewModel::loadDriverPhoneNumbers, detailsRefresh)
    LaunchedEffect(viewModel, userId) { viewModel.refresh() }
    DeviceDriversContent(
        state, { detailsRefresh++; viewModel.refresh() }, viewModel::selectDevice, viewModel::setInviteInput,
        viewModel::lookup, viewModel::confirmActivation, viewModel::dismissTarget, viewModel::transition,
        viewModel::setInviteByEmail, phoneNumbers,
    )
}

@Composable
internal fun DeviceDriversContent(
    state: DeviceDriversState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onInviteInput: (String) -> Unit,
    onLookup: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onTransition: (String, String) -> Unit,
    onInviteByEmail: (Boolean) -> Unit = {},
    phoneNumbers: Map<String, String> = emptyMap(),
) {
    var removal by remember { mutableStateOf<Pair<String, String>?>(null) }
    val savedOwners = state.grants.filter { it.status == "OWNER" && it.allowed }
    val sharedDevices = state.overview.incoming.filter { it.status in visibleDriverAccess }
    GroupSurface {
        Text("Your device access", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("The primary driver can add other drivers to this device. Each driver uses their own account.", color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        run {
            Text("Demo subscriptions · No payment is collected", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            state.error?.let { Text(it, color = DriveRed, modifier = Modifier.padding(top = 12.dp)) }
            state.message?.let { Text(it, color = Success, modifier = Modifier.padding(top = 12.dp)) }
            if (state.overview.owned.isEmpty() && savedOwners.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("You are the primary driver", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                savedOwners.forEach { Text(it.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.padding(top = 8.dp)) }
                Text("Primary access is saved for offline use. Refresh online to manage additional drivers.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
            } else if (state.overview.owned.isEmpty() && sharedDevices.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("No registered device loaded", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Open Setup & Device → Device Connection to register a nearby device. Internet is needed to register or add drivers. Daily monitoring works offline.", color = TextSecondary)
                state.grants.filter { grant ->
                    grant.allowed && state.overview.incoming.none { it.deviceId == grant.deviceId }
                }.forEach {
                    Text("${it.displayName} · ${if (it.status == "OWNER") "Primary driver" else "Additional driver"} · Offline access saved", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (state.overview.owned.size > 1) {
                Spacer(Modifier.height(20.dp))
                Text("Choose a device to manage", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                state.overview.owned.forEach { device ->
                    val selected = state.selectedDeviceId == device.deviceId
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, enabled = !state.busy, role = Role.RadioButton,
                        onClick = { onSelect(device.deviceId) }).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = null, enabled = !state.busy)
                        Spacer(Modifier.width(12.dp))
                        Text(device.displayName, color = TextPrimary)
                    }
                }
            }
            state.overview.owned.firstOrNull { it.deviceId == state.selectedDeviceId }?.let { device ->
                Spacer(Modifier.height(16.dp))
                Text("You are the primary driver", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(device.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("You can add and remove additional drivers.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(24.dp))
                Text("Additional drivers", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                val additionalDrivers = state.overview.managed.filter {
                    it.deviceId == device.deviceId && it.status in visibleDriverAccess
                }
                if (additionalDrivers.isEmpty()) {
                    Text("No additional drivers yet.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                }
                additionalDrivers.forEach { member ->
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Border)
                    Text(member.driverDisplayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        phoneNumbers[member.id] ?: "Phone number unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (member.status == "PENDING") {
                        PrimaryButton("Activate access", { onTransition(member.id, "ACTIVE") }, enabled = !state.busy)
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(
                            "Cancel invitation",
                            { removal = member.id to "CANCELLED" },
                            enabled = !state.busy,
                        )
                    } else {
                        PrimaryButton(
                            "Remove driver",
                            { removal = member.id to "REMOVED" },
                            enabled = !state.busy,
                            containerColor = DriveRedPressed,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Add an additional driver", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    if (state.inviteByEmail) "Enter the email address the driver uses for DriveAlert."
                    else "Enter the connection code from the driver’s DriveAlert Account screen.",
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.inviteInput,
                    onValueChange = onInviteInput,
                    label = { Text(if (state.inviteByEmail) "Email address" else "Driver connection code") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (state.inviteByEmail) KeyboardType.Email else KeyboardType.Ascii,
                        capitalization = if (state.inviteByEmail) KeyboardCapitalization.None else KeyboardCapitalization.Characters,
                        autoCorrect = false,
                    ),
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    if (state.inviteByEmail) "Search" else "Find Driver",
                    onLookup,
                    enabled = !state.busy && state.inviteInput.isNotBlank(),
                    containerColor = DriveRedPressed,
                )
                TextButton(onClick = { onInviteByEmail(!state.inviteByEmail) }, enabled = !state.busy) {
                    Text(if (state.inviteByEmail) "Use a connection code" else "Search by email")
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Shared devices", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (sharedDevices.isEmpty()) {
                Text("No shared devices loaded. Refresh online to check your access.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
            }
            sharedDevices.forEach { member ->
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Border)
                Text(member.deviceDisplayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("${member.status.lowercase().replaceFirstChar { it.uppercase() }} · Device ${member.deviceId.takeLast(6)}", color = TextSecondary)
                if (member.status == "PENDING") {
                    Text("Ask ${member.ownerDisplayName} to activate your access.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                } else if (member.status == "ACTIVE") {
                    Text("Access saved for offline use. Open Setup & Device → Device Connection to pair this phone with the device through Bluetooth.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Leave device", { removal = member.id to "LEFT" }, enabled = !state.busy)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state.overview.owned.isNotEmpty() || savedOwners.isNotEmpty()) {
                Text("After removing a driver, reconnect to the device as the primary driver over Bluetooth to apply the removal on the device too.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
            }
            Text("Each driver's calibration and alert history stay separate.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(20.dp))
            SecondaryButton(if (state.busy) "Updating access…" else "Refresh access online", onRefresh, enabled = !state.busy)
        }
    }
    state.target?.let { target ->
        AlertDialog(onDismissRequest = { if (!state.busy) onDismiss() },
            title = { Text("Add this driver?") },
            text = { Column {
                Text(target.displayName, style = MaterialTheme.typography.titleMedium)
                if (target is DeviceDriverInviteTarget.ByEmail) Text(target.match.email)
                Spacer(Modifier.height(12.dp))
                Text("They can connect to this device as soon as you add them. This is a simulated subscription. No payment is collected.")
                state.error?.let { Text(it, color = DriveRed, modifier = Modifier.padding(top = 12.dp)) }
            } },
            confirmButton = { TextButton(onConfirm, enabled = !state.busy, colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)) { Text(if (state.busy) "Adding…" else "Add driver") } },
            dismissButton = { TextButton(onDismiss, enabled = !state.busy, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) { Text("Cancel") } })
    }
    removal?.let { (id, next) ->
        ConfirmDialog("${if (next == "LEFT") "Leave device" else if (next == "CANCELLED") "Cancel invitation" else "Remove driver"}?",
            "The app blocks access after its next sync. Reconnect as the primary driver over Bluetooth to apply the change on the device too. Saved history is kept.",
            "Confirm", onConfirm = { removal = null; onTransition(id, next) }, onDismiss = { removal = null }, actionColor = TextPrimary)
    }
}
