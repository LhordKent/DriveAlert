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
import com.lhordkent.drivealert.data.device.DeviceIdentity
import com.lhordkent.drivealert.device.deviceAccessError

data class DeviceSetupAuthorization(val deviceId: String? = null, val name: String = "DriveAlert device",
    val checking: Boolean = false, val claimable: Boolean = false, val message: String? = null)

class WifiProvisioningViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ProvisioningState())
    val state: StateFlow<ProvisioningState> = mutableState.asStateFlow()
    private var authorizedAccount: String? = null
    private var selectedCredential: com.lhordkent.drivealert.device.DeviceCredential? = null
    private val client = AndroidBleProvisioningClient(application.applicationContext, ::dispatch, allowCommand = {
        authorizedAccount != null && authorizedAccount == driverUserId &&
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid == authorizedAccount
    }, credentialProvider = { selectedCredential }, onSessionAuthorized = { id, token ->
        val uid = authorizedAccount ?: error("Account changed")
        check(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid == uid)
        (application as DriveAlertApplication).container.deviceCredentialStore.saveSession(uid, id, token)
    }, revocationProvider = {
        val uid = authorizedAccount
        val id = selectedCredential?.deviceId
        if (uid != null && id != null) (application as DriveAlertApplication).container.deviceCredentialStore.revocations(uid, id) else emptyList()
    })
    private val repository = (application as DriveAlertApplication).container.provisionedDeviceRepository
    private val recoveryStore = BleSetupRecoveryStore(application)
    private val access = (application as DriveAlertApplication).container.deviceAccessRepository
    private val mutableAuthorization = MutableStateFlow(DeviceSetupAuthorization())
    val authorization = mutableAuthorization.asStateFlow()
    private var generation = 0L
    private var authorizationJob: kotlinx.coroutines.Job? = null
    private var driverUserId: String? = null
    private var boundDriverUserId: String? = null

    fun bindDriver(userId: String?) {
        if (boundDriverUserId == userId) return
        // BLE callbacks and scan registrations are process-scoped, not account-scoped.
        // Tear down the previous Driver's session before exposing a clean state to
        // the next account; otherwise its first scan is rejected as already started.
        client.retry()
        authorizedAccount = null
        selectedCredential = null
        generation++
        authorizationJob?.cancel()
        mutableAuthorization.value = DeviceSetupAuthorization()
        boundDriverUserId = userId
        driverUserId = userId
        mutableState.value = ProvisioningState()
    }

    fun startScan() = client.startScan(recoveryStore.load(driverUserId))

    fun selectDevice(deviceId: String) {
        if (mutableAuthorization.value.checking) return
        val uid = driverUserId ?: return
        val expected = generation
        val canonical = DeviceIdentity.canonical(deviceId) ?: return
        val name = state.value.devices.firstOrNull { it.id == deviceId }?.name ?: "DriveAlert device"
        mutableAuthorization.value = DeviceSetupAuthorization(deviceId, name, checking = true)
        authorizationJob = viewModelScope.launch {
            try {
                if (!access.allowed(uid, canonical)) access.refresh(uid)
                if (expected != generation) return@launch
                if (access.allowed(uid, canonical)) {
                    authorizedAccount = uid
                    selectedCredential = getApplication<DriveAlertApplication>().container.deviceCredentialStore.load(uid, canonical)
                    mutableAuthorization.value = DeviceSetupAuthorization(deviceId, name)
                    client.connect(deviceId)
                } else {
                    val claimed = access.isClaimed(uid, canonical)
                    if (expected == generation) mutableAuthorization.value = DeviceSetupAuthorization(deviceId, name,
                        claimable = !claimed, message = if (claimed) "This device is registered to another account. Ask its primary driver to invite your connection code, then accept in Settings → Subscriptions & Drivers." else null)
                }
            } catch (error: Exception) {
                if (expected == generation) mutableAuthorization.value = DeviceSetupAuthorization(deviceId, name, message = deviceAccessError(error))
            }
        }
    }

    fun claimSelectedDevice() {
        val selected = authorization.value
        if (!selected.claimable || selected.checking) return
        val uid = driverUserId ?: return
        val expected = generation
        val address = selected.deviceId ?: return
        mutableAuthorization.value = selected.copy(checking = true)
        authorizationJob = viewModelScope.launch {
            try {
                access.claim(uid, requireNotNull(DeviceIdentity.canonical(address)), selected.name)
                if (expected == generation && access.allowed(uid, requireNotNull(DeviceIdentity.canonical(address)))) {
                    authorizedAccount = uid
                    selectedCredential = getApplication<DriveAlertApplication>().container.deviceCredentialStore.load(uid, requireNotNull(DeviceIdentity.canonical(address)))
                    mutableAuthorization.value = DeviceSetupAuthorization(address, selected.name)
                    client.connect(address)
                }
            } catch (error: Exception) {
                if (expected == generation) mutableAuthorization.value = selected.copy(checking = false, message = deviceAccessError(error))
            }
        }
    }

    fun dismissClaim() { if (!authorization.value.checking) mutableAuthorization.value = DeviceSetupAuthorization() }

    private fun authorizedCommand(command: () -> Unit) {
        val uid = driverUserId ?: return
        val selectedId = state.value.selectedDeviceId ?: return
        val canonical = DeviceIdentity.canonical(selectedId) ?: return
        val expected = generation
        viewModelScope.launch {
            val permitted = access.allowed(uid, canonical)
            if (expected != generation) return@launch
            if (permitted) command()
            else dispatch(ProvisioningEvent.Failed("Device access is not authorized for this account. Open Settings → Subscriptions & Drivers to verify access online."))
        }
    }

    fun submit(ssid: String, password: String) {
        // Spaces are legal SSID bytes and are significant, including at either end.
        WifiCredentials.create(ssid, password).fold(
            onSuccess = { credentials -> authorizedCommand { client.submit(credentials) } },
            onFailure = { dispatch(ProvisioningEvent.Failed("Enter an SSID and a valid Wi-Fi password.")) },
        )
    }

    fun connectUsingSavedCredentials() = authorizedCommand(client::connectUsingSavedCredentials)

    fun retry() = client.retry()

    fun resetAfterManualDisconnect() = client.retry()

    fun permissionDenied() = dispatch(ProvisioningEvent.Failed("Bluetooth permission is required for device setup."))

    fun bluetoothDisabled() = dispatch(ProvisioningEvent.Failed("Turn on Bluetooth to scan for your DriveAlert device."))

    override fun onCleared() {
        client.close()
    }

    private fun dispatch(event: ProvisioningEvent) {
        if (event !is ProvisioningEvent.Provisioned) mutableState.update { current -> ProvisioningReducer.reduce(current, event) }
        if (event == ProvisioningEvent.BleReady) {
            val state = mutableState.value
            state.devices.firstOrNull { it.id == state.selectedDeviceId }?.let {
                recoveryStore.save(driverUserId, it)
            }
        }
        if (event is ProvisioningEvent.Provisioned) {
            val userId = driverUserId ?: return
            val ip = event.ip ?: return
            val deviceId = event.deviceId?.let(DeviceIdentity::canonical) ?: return
            val expected = generation
            viewModelScope.launch {
                try {
                if (expected != generation || !access.allowed(userId, deviceId)) return@launch
                repository.active(userId)?.let { previous ->
                    if (previous.deviceId != deviceId) access.mapLegacy(userId, previous.deviceId, deviceId)
                }
                if (expected != generation) return@launch
                repository.saveActive(
                    ProvisionedDevice(
                        deviceId = deviceId,
                        driverUserId = userId,
                        hostname = event.hostname,
                        lastKnownIp = ip,
                        lastConnectedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
                if (expected == generation) mutableState.update { ProvisioningReducer.reduce(it, event) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (expected == generation) mutableState.update { ProvisioningReducer.reduce(it, ProvisioningEvent.Failed("Could not save the device connection. Verify access and retry setup.")) }
                }
            }
        }
    }
}
