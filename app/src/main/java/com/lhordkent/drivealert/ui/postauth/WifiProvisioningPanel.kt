package com.lhordkent.drivealert.ui.postauth

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhordkent.drivealert.provisioning.BlePermissionPolicy
import com.lhordkent.drivealert.provisioning.ProvisioningStage
import com.lhordkent.drivealert.provisioning.WifiProvisioningViewModel
import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.ui.theme.DriveRed
import com.lhordkent.drivealert.ui.theme.Success
import com.lhordkent.drivealert.ui.theme.TextMuted
import com.lhordkent.drivealert.ui.theme.TextPrimary
import com.lhordkent.drivealert.ui.theme.TextSecondary

@Composable
fun WifiProvisioningPanel(
    provisioningViewModel: WifiProvisioningViewModel = viewModel(),
    vision: DriverVisionUiState? = null,
    onDisconnect: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by provisioningViewModel.state.collectAsState()
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var useDifferentNetwork by rememberSaveable { mutableStateOf(false) }
    val permissions = remember { BlePermissionPolicy.runtimePermissions() }
    val streamState = vision?.streamState
    val connectionVerified = streamState == StreamConnectionState.CONNECTED
    val displayedStatus = when {
        state.stage != ProvisioningStage.PROVISIONED -> state.stage.name.replace('_', ' ')
        connectionVerified -> "CONNECTED"
        streamState == StreamConnectionState.UNAVAILABLE -> "STREAM UNAVAILABLE"
        streamState == StreamConnectionState.RECONNECTING -> "RECONNECTING"
        else -> "VERIFYING"
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (permissions.all { results[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            provisioningViewModel.startScan()
        } else {
            provisioningViewModel.permissionDenied()
        }
    }

    fun scanWithPermission() {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) provisioningViewModel.startScan()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    LaunchedEffect(state.stage) {
        if (state.stage == ProvisioningStage.PROVISIONED) password = ""
        if (state.stage != ProvisioningStage.CONNECTED_BLE) useDifferentNetwork = false
    }

    GroupSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("BLE provisioning", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    if (state.stage == ProvisioningStage.PROVISIONED && !connectionVerified) {
                        "Wi-Fi was accepted. Verifying the live camera stream."
                    } else state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            StatusPill(
                label = displayedStatus,
                tone = when (state.stage) {
                    ProvisioningStage.PROVISIONED -> when {
                        connectionVerified -> StatusTone.SUCCESS
                        streamState == StreamConnectionState.UNAVAILABLE -> StatusTone.ERROR
                        else -> StatusTone.WARNING
                    }
                    ProvisioningStage.FAILED -> StatusTone.ERROR
                    ProvisioningStage.IDLE -> StatusTone.NEUTRAL
                    else -> StatusTone.WARNING
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        when (state.stage) {
            ProvisioningStage.IDLE -> PrimaryButton("Scan for DriveAlert", ::scanWithPermission)
            ProvisioningStage.SCANNING -> Text("Keep the ESP32 powered and in provisioning mode.", color = TextMuted)
            ProvisioningStage.DEVICE_FOUND -> {
                state.devices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { provisioningViewModel.selectDevice(device.id) }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(device.name, color = TextPrimary)
                        Text("${device.rssi} dBm", color = TextMuted)
                    }
                }
            }
            ProvisioningStage.CONNECTED_BLE -> {
                when {
                    !state.credentialStatusKnown -> Text(
                        "Reading the saved network from your DriveAlert device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    state.savedNetworkSsid != null && !useDifferentNetwork -> {
                        Text("Saved network", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("\u201c${state.savedNetworkSsid}\u201d", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The ESP32 will remain offline until you press Connect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton("Connect", provisioningViewModel::connectUsingSavedCredentials)
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("Use a different network", onClick = { useDifferentNetwork = true })
                    }
                    else -> {
                        Text(
                            "Enable your hotspot, then enter its details. The network name is sent exactly as entered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = ssid,
                            onValueChange = { ssid = it },
                            label = { Text("Wi-Fi or hotspot SSID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (ssid.firstOrNull()?.isWhitespace() == true || ssid.lastOrNull()?.isWhitespace() == true) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "This network name includes a space at the beginning or end. It will be sent exactly as entered.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Wi-Fi password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton("Connect", onClick = { provisioningViewModel.submit(ssid, password) })
                        if (state.savedNetworkSsid != null) {
                            Spacer(Modifier.height(8.dp))
                            SecondaryButton("Use saved network", onClick = { useDifferentNetwork = false })
                        }
                    }
                }
            }
            ProvisioningStage.PROVISIONED -> {
                Text(
                    if (connectionVerified) {
                        "Live camera connected at ${state.assignedIp ?: "the assigned address"}."
                    } else {
                        "The ESP32 reported ${state.assignedIp ?: "an address"}, but the camera stream is not connected."
                    },
                    color = if (connectionVerified) Success else TextSecondary,
                )
                state.hostname?.let { Text("Device name: $it.local", color = TextSecondary) }
                Spacer(Modifier.height(6.dp))
                if (!connectionVerified) {
                    Text(
                        vision?.errorMessage ?: "Keep the ESP32 powered and connected to the same hotspot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (streamState == StreamConnectionState.UNAVAILABLE) DriveRed else TextMuted,
                    )
                } else {
                    Text("Connection verified through the live stream.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    when {
                        vision?.disconnectInProgress == true -> "Disconnecting"
                        connectionVerified -> "Disconnect"
                        else -> "Reconnect device"
                    },
                    onClick = if (connectionVerified) onDisconnect else ::scanWithPermission,
                    enabled = vision?.disconnectInProgress != true,
                )
            }
            ProvisioningStage.FAILED -> {
                Text(state.message, color = DriveRed, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Retry setup", onClick = {
                    provisioningViewModel.retry()
                    scanWithPermission()
                })
            }
            else -> Unit
        }
    }
}
