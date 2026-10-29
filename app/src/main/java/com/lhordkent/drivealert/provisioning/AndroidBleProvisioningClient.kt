package com.lhordkent.drivealert.provisioning

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.Closeable
import java.util.ArrayDeque
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidBleProvisioningClient(
    private val context: Context,
    private val onEvent: (ProvisioningEvent) -> Unit,
    private val connectionTimeoutMs: Long = CONNECTION_TIMEOUT_MS,
) : Closeable {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter
    private val devices = mutableMapOf<String, BluetoothDevice>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanning = false
    private var selectedDevice: BluetoothDevice? = null
    private var gatt: BluetoothGatt? = null
    private var ssidCharacteristic: BluetoothGattCharacteristic? = null
    private var passwordCharacteristic: BluetoothGattCharacteristic? = null
    private var applyCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private val writeQueue = ArrayDeque<PendingWrite>()
    private var inFlightWrite: PendingWrite? = null
    private var receiverRegistered = false
    private var intentionalDisconnect = false
    private val connectionTimeout = Runnable {
        if (gatt != null) {
            closeGatt()
            emit(ProvisioningEvent.Failed("DriveAlert did not respond. Keep the device powered, then retry setup. If needed, turn Bluetooth off and on before retrying."))
        }
    }
    private val scanTimeout = Runnable {
        if (!scanning) return@Runnable
        stopScan()
        if (devices.isEmpty()) emit(ProvisioningEvent.Failed("No DriveAlert provisioning device was found."))
    }

    private data class PendingWrite(
        val characteristic: BluetoothGattCharacteristic,
        val payload: ByteArray,
        val startsWifiConnection: Boolean = false,
    )

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onMain {
            // Some ESP32/phone combinations expose the local name only through
            // BluetoothDevice while the 128-bit service UUID is delivered in the
            // scan-response packet. Validate the DriveAlert name here rather than
            // relying on the phone's controller-level UUID filter.
            if (!scanning) return@onMain
            val scanRecord = result.scanRecord
            val advertisedName = scanRecord?.deviceName
            val cachedDeviceName = result.device.name
            val advertisesDriveAlertService = scanRecord?.serviceUuids
                ?.any { it.uuid == ProvisioningProtocol.serviceUuid } == true
            if (!shouldAcceptAdvertisement(advertisedName, cachedDeviceName, advertisesDriveAlertService)) return@onMain
            val name = advertisedName?.takeIf(::isDriveAlertAdvertisement)
                ?: cachedDeviceName?.takeIf(::isDriveAlertAdvertisement)
                ?: DEFAULT_DEVICE_NAME

            // Advertisements repeat several times per second. Emitting every RSSI
            // update needlessly recomposes the entire setup screen and makes taps
            // feel delayed on lower-end phones.
            if (devices.put(result.device.address, result.device) != null &&
                result.device.address !in rememberedAddresses) return@onMain
            rememberedAddresses.remove(result.device.address)
            emit(ProvisioningEvent.DeviceDiscovered(ProvisioningDevice(result.device.address, name, result.rssi)))
        }

        override fun onScanFailed(errorCode: Int) = onMain {
            if (!scanning) return@onMain
            if (isScanAlreadyRunning(errorCode)) {
                // Android reports code 1 when the same callback is registered twice. The
                // original scan is still active, so keep the session alive instead of
                // presenting a false setup failure.
                scanning = true
                return@onMain
            }
            mainHandler.removeCallbacks(scanTimeout)
            scanning = false
            emit(ProvisioningEvent.Failed("Bluetooth scan failed (code $errorCode)."))
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
            if (device?.address != selectedDevice?.address) return
            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                BluetoothDevice.BOND_BONDED -> prepareGatt(gatt)
                BluetoothDevice.BOND_NONE -> emit(ProvisioningEvent.Failed("Bluetooth pairing was not completed."))
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = withActiveGatt(gatt) {
            Log.d("DriveAlertBle", "GATT state=$newState status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (!intentionalDisconnect) emit(ProvisioningEvent.Failed("Bluetooth connection closed (code $status). Retry setup."))
                closeGatt()
                return@withActiveGatt
            }
            if (newState != BluetoothProfile.STATE_CONNECTED) return@withActiveGatt
            intentionalDisconnect = false
            val device = selectedDevice ?: gatt.device
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                prepareGatt(gatt)
            } else {
                emit(ProvisioningEvent.Pairing)
                if (!device.createBond()) emit(ProvisioningEvent.Failed("Android could not start Bluetooth pairing."))
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) = withActiveGatt(gatt) {
            if (!gatt.discoverServices()) emit(ProvisioningEvent.Failed("Could not start service discovery."))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = withActiveGatt(gatt) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                emit(ProvisioningEvent.Failed("DriveAlert service discovery failed."))
                return@withActiveGatt
            }
            val service: BluetoothGattService = gatt.getService(ProvisioningProtocol.serviceUuid) ?: run {
                emit(ProvisioningEvent.Failed("Selected device does not expose the DriveAlert service."))
                return@withActiveGatt
            }
            ssidCharacteristic = service.getCharacteristic(ProvisioningProtocol.ssidUuid)
            passwordCharacteristic = service.getCharacteristic(ProvisioningProtocol.passwordUuid)
            applyCharacteristic = service.getCharacteristic(ProvisioningProtocol.applyUuid)
            statusCharacteristic = service.getCharacteristic(ProvisioningProtocol.statusUuid)
            val statusCharacteristic = statusCharacteristic
            if (ssidCharacteristic == null || passwordCharacteristic == null || applyCharacteristic == null || statusCharacteristic == null) {
                emit(ProvisioningEvent.Failed("DriveAlert provisioning characteristics are incomplete."))
                return@withActiveGatt
            }
            if (!gatt.setCharacteristicNotification(statusCharacteristic, true)) {
                emit(ProvisioningEvent.Failed("Could not enable provisioning status updates."))
                return@withActiveGatt
            }
            val descriptor = statusCharacteristic.getDescriptor(ProvisioningProtocol.clientConfigurationUuid) ?: run {
                emit(ProvisioningEvent.Failed("Provisioning notification descriptor is missing."))
                return@withActiveGatt
            }
            if (!writeDescriptor(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                emit(ProvisioningEvent.Failed("Could not subscribe to provisioning status."))
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = withActiveGatt(gatt) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                emit(ProvisioningEvent.BleReady)
                queryStoredCredentials(gatt)
            } else emit(ProvisioningEvent.Failed("Secure status subscription failed."))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = withActiveGatt(gatt) {
            val completed = inFlightWrite
            inFlightWrite = null
            if (completed?.characteristic?.uuid == ProvisioningProtocol.passwordUuid) completed.payload.fill(0)
            @Suppress("DEPRECATION")
            if (characteristic.uuid == ProvisioningProtocol.passwordUuid) characteristic.value = ByteArray(0)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                clearQueuedSecrets()
                emit(ProvisioningEvent.Failed("Secure credential transfer failed."))
                return@withActiveGatt
            }
            if (completed?.startsWifiConnection == true) emit(ProvisioningEvent.WifiConnecting)
            writeNext(gatt)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value?.copyOf() ?: ByteArray(0)
            withActiveGatt(gatt) { handleStatus(value) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = withActiveGatt(gatt) {
            handleStatus(value)
        }
    }

    init {
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bondReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") context.registerReceiver(bondReceiver, filter)
        }
        receiverRegistered = true
    }

    private val rememberedAddresses = mutableSetOf<String>()

    fun startScan(rememberedDevice: ProvisioningDevice? = null) {
        // A fast double tap or repeated lifecycle event can arrive before Compose
        // renders the SCANNING state. Registering the same callback again causes
        // ScanCallback.SCAN_FAILED_ALREADY_STARTED (error code 1).
        if (scanning) return
        closeGatt()
        devices.clear()
        rememberedAddresses.clear()
        val scanner = adapter?.bluetoothLeScanner ?: run {
            emit(ProvisioningEvent.Failed("Bluetooth is unavailable or turned off."))
            return
        }
        emit(ProvisioningEvent.ScanStarted)
        // A bonded peripheral may be absent from scan results while Android is
        // releasing an old link. Reconnect by address instead of requiring discovery.
        rememberedDevice?.let { remembered ->
            if (android.bluetooth.BluetoothAdapter.checkBluetoothAddress(remembered.id)) {
                val device = adapter?.getRemoteDevice(remembered.id)
                if (device != null) addRememberedDevice(device, remembered.name)
            }
        }
        adapter?.bondedDevices?.filter { isDriveAlertAdvertisement(it.name) }?.forEach {
            addRememberedDevice(it, it.name ?: DEFAULT_DEVICE_NAME)
        }
        scanning = true
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // Do not use a hardware service-UUID filter here. The classic ESP32 BLE
        // stack can place the 128-bit UUID in its scan response, and some Android
        // Bluetooth implementations then discard it before this callback runs.
        scanner.startScan(emptyList(), settings, scanCallback)
        mainHandler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS)
    }

    fun connect(deviceId: String) {
        val device = devices[deviceId] ?: run {
            emit(ProvisioningEvent.Failed("Selected DriveAlert device is no longer available."))
            return
        }
        stopScan()
        closeGatt()
        selectedDevice = device
        intentionalDisconnect = false
        Log.d("DriveAlertBle", "Connecting to selected setup device")
        emit(ProvisioningEvent.Connecting(deviceId))
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            emit(ProvisioningEvent.Failed("Android could not start the Bluetooth connection. Retry setup."))
        } else {
            mainHandler.postDelayed(connectionTimeout, connectionTimeoutMs)
        }
    }

    private fun addRememberedDevice(device: BluetoothDevice, name: String) {
        if (devices.put(device.address, device) != null) return
        rememberedAddresses.add(device.address)
        emit(ProvisioningEvent.DeviceDiscovered(ProvisioningDevice(device.address, name, 0, previouslyKnown = true)))
    }

    fun submit(credentials: WifiCredentials) {
        val activeGatt = gatt ?: run {
            emit(ProvisioningEvent.Failed("Bluetooth is not connected."))
            return
        }
        val ssid = ssidCharacteristic
        val password = passwordCharacteristic
        val apply = applyCharacteristic
        if (ssid == null || password == null || apply == null) {
            emit(ProvisioningEvent.Failed("Provisioning service is not ready."))
            return
        }
        clearQueuedSecrets()
        writeQueue.add(PendingWrite(ssid, credentials.ssid.toByteArray(Charsets.UTF_8)))
        writeQueue.add(PendingWrite(password, credentials.password.toByteArray(Charsets.UTF_8)))
        writeQueue.add(
            PendingWrite(
                apply,
                ProvisioningProtocol.applyCommand.toByteArray(Charsets.UTF_8),
                startsWifiConnection = true,
            ),
        )
        emit(ProvisioningEvent.CredentialsSending)
        writeNext(activeGatt)
    }

    fun connectUsingSavedCredentials() {
        val activeGatt = gatt ?: run {
            emit(ProvisioningEvent.Failed("Bluetooth is not connected."))
            return
        }
        val apply = applyCharacteristic ?: run {
            emit(ProvisioningEvent.Failed("Provisioning service is not ready."))
            return
        }
        clearQueuedSecrets()
        writeQueue.add(
            PendingWrite(
                apply,
                ProvisioningProtocol.connectSavedCommand.toByteArray(Charsets.UTF_8),
                startsWifiConnection = true,
            ),
        )
        writeNext(activeGatt)
    }

    fun retry() {
        stopScan()
        closeGatt()
        emit(ProvisioningEvent.Retry)
    }

    override fun close() {
        stopScan()
        closeGatt()
        if (receiverRegistered) {
            context.unregisterReceiver(bondReceiver)
            receiverRegistered = false
        }
    }

    private fun prepareGatt(gatt: BluetoothGatt?) {
        if (gatt == null) return
        if (!gatt.requestMtu(128) && !gatt.discoverServices()) {
            emit(ProvisioningEvent.Failed("Could not prepare the Bluetooth connection."))
        }
    }

    private fun writeNext(gatt: BluetoothGatt) {
        if (writeQueue.isEmpty()) return
        val pending = writeQueue.removeFirst()
        inFlightWrite = pending
        if (!writeCharacteristic(gatt, pending.characteristic, pending.payload)) {
            if (pending.characteristic.uuid == ProvisioningProtocol.passwordUuid) pending.payload.fill(0)
            inFlightWrite = null
            clearQueuedSecrets()
            emit(ProvisioningEvent.Failed("Could not queue secure credential transfer."))
        }
    }

    private fun handleStatus(bytes: ByteArray) {
        val status = bytes.toString(Charsets.UTF_8)
        when {
            status == "CONNECTING" -> emit(ProvisioningEvent.WifiConnecting)
            status == "CREDENTIALS|NONE" -> {
                mainHandler.removeCallbacks(connectionTimeout)
                emit(ProvisioningEvent.CredentialStatus(null))
            }
            status.startsWith("CREDENTIALS|SAVED|") -> {
                val ssid = parseCredentialStatus(status)
                if (ssid == null) emit(ProvisioningEvent.Failed("DriveAlert Device returned an invalid saved network."))
                else {
                    mainHandler.removeCallbacks(connectionTimeout)
                    emit(ProvisioningEvent.CredentialStatus(ssid))
                }
            }
            status.startsWith("CONNECTED|") -> {
                val endpoint = parseConnectedStatus(status)
                if (endpoint == null) {
                    emit(ProvisioningEvent.Failed("DriveAlert Device returned an invalid network address."))
                    return
                }
                emit(ProvisioningEvent.Provisioned(endpoint.ip, endpoint.hostname, selectedDevice?.address))
                clearQueuedSecrets()
                intentionalDisconnect = true
                gatt?.disconnect()
            }
            status == "INVALID_CREDENTIALS" -> emit(ProvisioningEvent.Failed("SSID or password format was rejected."))
            status == "CONNECTION_FAILED" -> emit(ProvisioningEvent.WifiConnectionFailed)
            status == "ERROR" -> emit(ProvisioningEvent.Failed("DriveAlert Device could not store the working credentials."))
        }
    }

    private fun stopScan() {
        mainHandler.removeCallbacks(scanTimeout)
        if (!scanning) return
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
    }

    private fun queryStoredCredentials(gatt: BluetoothGatt) {
        val apply = applyCharacteristic ?: return
        writeQueue.add(
            PendingWrite(
                apply,
                ProvisioningProtocol.queryCredentialsCommand.toByteArray(Charsets.UTF_8),
            ),
        )
        writeNext(gatt)
    }

    private fun closeGatt() {
        mainHandler.removeCallbacks(connectionTimeout)
        clearQueuedSecrets()
        intentionalDisconnect = true
        val previous = gatt
        // Invalidate first: a late disconnect callback must never close a new link.
        gatt = null
        selectedDevice = null
        ssidCharacteristic = null
        passwordCharacteristic = null
        applyCharacteristic = null
        statusCharacteristic = null
        previous?.disconnect()
        previous?.close()
    }

    private fun emit(event: ProvisioningEvent) {
        if (event is ProvisioningEvent.Failed) closeGatt()
        // Log the transition type only: credential status may contain a private SSID.
        Log.d("DriveAlertBle", "Setup event=${event.javaClass.simpleName}")
        onEvent(event)
    }

    private fun onMain(action: () -> Unit) {
        mainHandler.post { action() }
    }

    private fun withActiveGatt(callbackGatt: BluetoothGatt, action: () -> Unit) {
        mainHandler.post {
            if (gatt === callbackGatt) action()
        }
    }

    private fun clearQueuedSecrets() {
        inFlightWrite?.payload?.fill(0)
        inFlightWrite = null
        writeQueue.forEach { pending ->
            if (pending.characteristic.uuid == ProvisioningProtocol.passwordUuid) pending.payload.fill(0)
        }
        writeQueue.clear()
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = value
        gatt.writeCharacteristic(characteristic)
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }

    companion object {
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val CONNECTION_TIMEOUT_MS = 30_000L
        private const val DEFAULT_DEVICE_NAME = "DriveAlert device"

        data class ConnectedEndpoint(val ip: String, val hostname: String?)

        fun parseConnectedStatus(status: String): ConnectedEndpoint? {
            val parts = status.split('|', limit = 3)
            if (parts.size < 2 || parts[0] != "CONNECTED" || parts[1].isBlank()) return null
            return ConnectedEndpoint(parts[1], parts.getOrNull(2)?.takeIf(String::isNotBlank))
        }

        fun parseCredentialStatus(status: String): String? {
            val prefix = "CREDENTIALS|SAVED|"
            if (!status.startsWith(prefix)) return null
            return status.removePrefix(prefix).takeIf(String::isNotEmpty)
        }

        internal fun isScanAlreadyRunning(errorCode: Int): Boolean =
            errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED

        internal fun isDriveAlertAdvertisement(name: String?): Boolean =
            name?.startsWith(ProvisioningProtocol.advertisedNamePrefix) == true

        internal fun shouldAcceptAdvertisement(
            scanRecordName: String?,
            cachedDeviceName: String?,
            advertisesDriveAlertService: Boolean,
        ): Boolean = advertisesDriveAlertService ||
            isDriveAlertAdvertisement(scanRecordName) ||
            isDriveAlertAdvertisement(cachedDeviceName)
    }
}
