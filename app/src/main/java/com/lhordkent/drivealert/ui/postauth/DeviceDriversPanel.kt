package com.lhordkent.drivealert.ui.postauth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.device.DeviceDriversState
import com.lhordkent.drivealert.device.DeviceDriversViewModel
import com.lhordkent.drivealert.ui.theme.*

@Composable
fun DeviceDriversPanel(viewModel: DeviceDriversViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    DeviceDriversContent(state, viewModel::refresh, viewModel::selectDevice, viewModel::setCode,
        viewModel::lookup, viewModel::confirmActivation, viewModel::dismissTarget, viewModel::transition)
}

@Composable
internal fun DeviceDriversContent(
    state: DeviceDriversState,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit,
    onCode: (String) -> Unit,
    onLookup: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onTransition: (String, String) -> Unit,
) {
    var removal by remember { mutableStateOf<Pair<String, String>?>(null) }
    val savedOwners = state.grants.filter { it.status == "OWNER" && it.allowed }
    GroupSurface {
        Text("Your device access", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Each device includes one primary driver. Additional drivers use their own accounts and need an invitation from the primary driver.", color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        val pendingInvitations = state.overview.incoming.count { it.status == "PENDING" }
        if (pendingInvitations > 0) Text("$pendingInvitations pending device invitation${if (pendingInvitations == 1) "" else "s"}", color = Info, modifier = Modifier.padding(bottom = 12.dp))
        run {
            Text("Demo subscriptions · No payment is collected", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            state.error?.let { Text(it, color = DriveRed, modifier = Modifier.padding(top = 12.dp)) }
            state.message?.let { Text(it, color = Success, modifier = Modifier.padding(top = 12.dp)) }
            if (state.overview.owned.isEmpty() && savedOwners.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("You are the primary driver", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                savedOwners.forEach { Text(it.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.padding(top = 8.dp)) }
                Text("Primary access is included and saved for offline use. Refresh online to load invitations and manage additional drivers.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
            } else if (state.overview.owned.isEmpty() && state.overview.incoming.none { it.status in listOf("PENDING", "ACTIVE") }) {
                Spacer(Modifier.height(16.dp))
                Text("No registered device loaded", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Open Setup & Device → Device Connection to select and register your nearby device. Internet is needed once for registration and invitations; daily monitoring stays offline.", color = TextSecondary)
                state.grants.filter { it.allowed }.forEach {
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
                Text("Your access is included with this device. You can invite and remove additional drivers.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(24.dp))
                Text("Additional drivers", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                if (state.overview.managed.none { it.deviceId == device.deviceId }) {
                    Text("No additional drivers yet.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                }
                state.overview.managed.filter { it.deviceId == device.deviceId }.forEach { member ->
                    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Border)
                    Text(member.driverDisplayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("${member.status.lowercase().replaceFirstChar { it.uppercase() }} · Demo subscription", color = TextSecondary)
                    if (member.status in listOf("PENDING", "ACTIVE")) {
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(if (member.status == "PENDING") "Cancel invitation" else "Remove driver",
                            { removal = member.id to if (member.status == "PENDING") "CANCELLED" else "REMOVED" }, enabled = !state.busy)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Invite an additional driver", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Enter their connection code, check their name, then confirm the demo subscription. They must accept before using the device.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(state.code, onCode, label = { Text("Driver connection code") },
                    supportingText = { Text("Use the code from their DriveAlert Account screen.") },
                    singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Find Driver", onLookup, enabled = !state.busy && state.code.isNotBlank(), containerColor = DriveRedPressed)
            }
            Spacer(Modifier.height(24.dp))
            Text("Invitations to you", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (state.overview.incoming.isEmpty()) {
                Text("No device invitations loaded. Refresh online to check for new invitations.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
            }
            state.overview.incoming.forEach { member ->
                HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Border)
                Text(member.deviceDisplayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("${member.status.lowercase().replaceFirstChar { it.uppercase() }} · Device ${member.deviceId.takeLast(6)}", color = TextSecondary)
                if (member.status == "PENDING") {
                    Text("${member.ownerDisplayName} invited your account to use this device. Accept only if you know this primary driver. Your calibration and history stay separate.", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Accept device invitation", { onTransition(member.id, "ACTIVE") }, enabled = !state.busy, containerColor = DriveRedPressed)
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Decline invitation", { onTransition(member.id, "DECLINED") }, enabled = !state.busy)
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
            title = { Text("Activate demo subscription?") },
            text = { Column {
                Text("Add ${target.displayName} as an additional driver on this device?")
                Spacer(Modifier.height(12.dp))
                Text("This is a simulated subscription. No payment will be collected. Access begins after they accept your invitation.")
                state.error?.let { Text(it, color = DriveRed, modifier = Modifier.padding(top = 12.dp)) }
            } },
            confirmButton = { TextButton(onConfirm, enabled = !state.busy, colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)) { Text(if (state.busy) "Activating…" else "Confirm Activation") } },
            dismissButton = { TextButton(onDismiss, enabled = !state.busy, colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)) { Text("Cancel") } })
    }
    removal?.let { (id, next) ->
        ConfirmDialog("${if (next == "LEFT") "Leave device" else if (next == "CANCELLED") "Cancel invitation" else "Remove driver"}?",
            "The app blocks access after its next sync. Reconnect as the primary driver over Bluetooth to apply the change on the device too. Saved history is kept.",
            "Confirm", onConfirm = { removal = null; onTransition(id, next) }, onDismiss = { removal = null }, actionColor = TextPrimary)
    }
}
