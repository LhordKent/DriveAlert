package com.lhordkent.drivealert.ui.postauth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.ui.theme.*

internal fun showsSavedCameraConnection(vision: DriverVisionUiState?): Boolean =
    vision?.streamState == StreamConnectionState.CONNECTED ||
        vision?.let { it.deviceConfigured && !it.bleSetupRequested } == true

@Composable
fun SavedCameraConnectionPanel(vision: DriverVisionUiState, onDisconnect: () -> Unit,
    onRetry: () -> Unit, onReturnToBle: () -> Unit, onOpenBleSetup: () -> Unit = {}) {
    val connected = vision.streamState == StreamConnectionState.CONNECTED
    val busy = vision.disconnectInProgress
    var confirmReturn by remember { mutableStateOf(false) }
    var confirmSearch by remember { mutableStateOf(false) }
    GroupSurface {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Camera connection", style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            StatusPill(if (connected) "CONNECTED" else "CHECKING", if (connected) StatusTone.SUCCESS else StatusTone.WARNING)
        }
        Spacer(Modifier.height(12.dp))
        Text(if (connected) "Live camera connected over Wi-Fi." else "Restoring the saved camera connection over Wi-Fi.", color = TextSecondary)
        vision.deviceName?.let { Text(it, color = TextSecondary, modifier = Modifier.padding(top = 8.dp)) }
        vision.deviceAddress?.let { Text("Camera address: $it", color = TextMuted, style = MaterialTheme.typography.bodySmall) }
        vision.errorMessage?.let { Text(it, color = TextPrimary, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(16.dp))
        if (connected) {
            SecondaryButton(if (busy) "Disconnecting…" else "Disconnect", { confirmReturn = true }, enabled = !busy)
        } else {
            SecondaryButton("Try Wi-Fi connection again", onRetry, enabled = !busy)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(if (busy) "Returning to Bluetooth…" else "Go back to Bluetooth (BLE) setup", { confirmReturn = true }, enabled = !busy)
            Spacer(Modifier.height(12.dp))
            Text("Returning to Bluetooth uses the camera’s Wi-Fi control connection. If the camera is offline, keep it powered and on the same network, then retry.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Open Bluetooth setup", { confirmSearch = true }, enabled = !busy)
            Text("Use this if the camera has already returned to Bluetooth setup.", color = TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (confirmReturn) ConfirmDialog("Return camera to Bluetooth setup?",
        "This stops monitoring and asks the camera to restart into Bluetooth setup. Saved Wi-Fi details are kept.",
        "Return to Bluetooth", onConfirm = { confirmReturn = false; if (connected) onDisconnect() else onReturnToBle() },
        onDismiss = { confirmReturn = false })
    if (confirmSearch) ConfirmDialog("Open Bluetooth setup?",
        "This stops monitoring and opens Bluetooth discovery. It does not restart the camera. The camera must already be in Bluetooth setup mode.",
        "Open Bluetooth setup", onConfirm = { confirmSearch = false; onOpenBleSetup() }, onDismiss = { confirmSearch = false })
}
