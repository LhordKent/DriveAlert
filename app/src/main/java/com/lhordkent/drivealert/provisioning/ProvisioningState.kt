package com.lhordkent.drivealert.provisioning

enum class ProvisioningStage {
    IDLE,
    SCANNING,
    DEVICE_FOUND,
    CONNECTING_BLE,
    PAIRING,
    CONNECTED_BLE,
    SENDING_CREDENTIALS,
    CONNECTING_WIFI,
    PROVISIONED,
    FAILED,
}

data class ProvisioningDevice(
    val id: String,
    val name: String,
    val rssi: Int,
)

data class ProvisioningState(
    val stage: ProvisioningStage = ProvisioningStage.IDLE,
    val devices: List<ProvisioningDevice> = emptyList(),
    val selectedDeviceId: String? = null,
    val message: String = "Ready to find a DriveAlert device.",
    val assignedIp: String? = null,
    val hostname: String? = null,
    val provisionedDeviceId: String? = null,
    val credentialStatusKnown: Boolean = false,
    val savedNetworkSsid: String? = null,
)

sealed interface ProvisioningEvent {
    data object ScanStarted : ProvisioningEvent
    data class DeviceDiscovered(val device: ProvisioningDevice) : ProvisioningEvent
    data class Connecting(val deviceId: String) : ProvisioningEvent
    data object Pairing : ProvisioningEvent
    data object BleReady : ProvisioningEvent
    data class CredentialStatus(val savedSsid: String?) : ProvisioningEvent
    data object CredentialsSending : ProvisioningEvent
    data object WifiConnecting : ProvisioningEvent
    data class Provisioned(val ip: String?, val hostname: String? = null, val deviceId: String? = null) : ProvisioningEvent
    data class Failed(val reason: String) : ProvisioningEvent
    data object Retry : ProvisioningEvent
    data object Reset : ProvisioningEvent
}

object ProvisioningReducer {
    fun reduce(state: ProvisioningState, event: ProvisioningEvent): ProvisioningState = when (event) {
        ProvisioningEvent.ScanStarted -> ProvisioningState(
            stage = ProvisioningStage.SCANNING,
            message = "Scanning for DriveAlert devices…",
        )
        is ProvisioningEvent.DeviceDiscovered -> state.copy(
            stage = ProvisioningStage.DEVICE_FOUND,
            devices = (state.devices.filterNot { it.id == event.device.id } + event.device)
                .sortedByDescending(ProvisioningDevice::rssi),
            message = "Select a DriveAlert device.",
        )
        is ProvisioningEvent.Connecting -> state.copy(
            stage = ProvisioningStage.CONNECTING_BLE,
            selectedDeviceId = event.deviceId,
            message = "Connecting over Bluetooth…",
        )
        ProvisioningEvent.Pairing -> state.copy(
            stage = ProvisioningStage.PAIRING,
            message = "Enter the ESP32 pairing PIN when Android asks.",
        )
        ProvisioningEvent.BleReady -> state.copy(
            stage = ProvisioningStage.CONNECTED_BLE,
            credentialStatusKnown = false,
            savedNetworkSsid = null,
            message = "Checking this device for a saved networkâ€¦",
        )
        is ProvisioningEvent.CredentialStatus -> state.copy(
            stage = ProvisioningStage.CONNECTED_BLE,
            credentialStatusKnown = true,
            savedNetworkSsid = event.savedSsid,
            message = if (event.savedSsid == null) {
                "No saved network was found."
            } else {
                "Saved network found. Choose when to connect."
            },
        )
        ProvisioningEvent.CredentialsSending -> state.copy(
            stage = ProvisioningStage.SENDING_CREDENTIALS,
            message = "Sending Wi-Fi credentials securely…",
        )
        ProvisioningEvent.WifiConnecting -> state.copy(
            stage = ProvisioningStage.CONNECTING_WIFI,
            message = "ESP32 is connecting to Wi-Fi…",
        )
        is ProvisioningEvent.Provisioned -> state.copy(
            stage = ProvisioningStage.PROVISIONED,
            message = "DriveAlert connected successfully.",
            assignedIp = event.ip,
            hostname = event.hostname,
            provisionedDeviceId = event.deviceId,
        )
        is ProvisioningEvent.Failed -> state.copy(
            stage = ProvisioningStage.FAILED,
            message = event.reason,
        )
        ProvisioningEvent.Retry -> ProvisioningState(
            stage = ProvisioningStage.IDLE,
            message = "Ready to scan again.",
        )
        ProvisioningEvent.Reset -> ProvisioningState()
    }
}
