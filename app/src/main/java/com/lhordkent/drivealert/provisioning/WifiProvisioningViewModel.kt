package com.lhordkent.drivealert.provisioning

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhordkent.drivealert.DriveAlertApplication
import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WifiProvisioningViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ProvisioningState())
    val state: StateFlow<ProvisioningState> = mutableState.asStateFlow()
    private val client = AndroidBleProvisioningClient(application.applicationContext, ::dispatch)
    private val repository = (application as DriveAlertApplication).container.provisionedDeviceRepository
    private var driverUserId: String? = null
    private var boundDriverUserId: String? = null

    fun bindDriver(userId: String?) {
        if (boundDriverUserId == userId) return
        // BLE callbacks and scan registrations are process-scoped, not account-scoped.
        // Tear down the previous Driver's session before exposing a clean state to
        // the next account; otherwise its first scan is rejected as already started.
        client.retry()
        boundDriverUserId = userId
        driverUserId = userId
        mutableState.value = ProvisioningState()
    }

    fun startScan() = client.startScan()

    fun selectDevice(deviceId: String) = client.connect(deviceId)

    fun submit(ssid: String, password: String) {
        // Spaces are legal SSID bytes and are significant, including at either end.
        WifiCredentials.create(ssid, password).fold(
            onSuccess = client::submit,
            onFailure = { dispatch(ProvisioningEvent.Failed("Enter an SSID and a valid Wi-Fi password.")) },
        )
    }

    fun connectUsingSavedCredentials() = client.connectUsingSavedCredentials()

    fun retry() = client.retry()

    fun resetAfterManualDisconnect() = client.retry()

    fun permissionDenied() = dispatch(ProvisioningEvent.Failed("Bluetooth permission is required for device setup."))

    fun bluetoothDisabled() = dispatch(ProvisioningEvent.Failed("Turn on Bluetooth to scan for your DriveAlert device."))

    override fun onCleared() {
        client.close()
    }

    private fun dispatch(event: ProvisioningEvent) {
        mutableState.update { current -> ProvisioningReducer.reduce(current, event) }
        if (event is ProvisioningEvent.Provisioned) {
            val userId = driverUserId ?: return
            val ip = event.ip ?: return
            val deviceId = event.hostname ?: event.deviceId ?: ip
            viewModelScope.launch {
                repository.saveActive(
                    ProvisionedDevice(
                        deviceId = deviceId,
                        driverUserId = userId,
                        hostname = event.hostname,
                        lastKnownIp = ip,
                        lastConnectedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}
