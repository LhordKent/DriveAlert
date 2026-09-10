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
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.io.Closeable
import java.util.ArrayDeque
import java.util.UUID

@SuppressLint("MissingPermission")
class AndroidBleProvisioningClient(
    private val context: Context,
    private val onEvent: (ProvisioningEvent) -> Unit,
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

    private data class PendingWrite(
        val characteristic: BluetoothGattCharacteristic,
        val payload: ByteArray,
        val startsWifiConnection: Boolean = false,
    )

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: return
            if (!name.startsWith(ProvisioningProtocol.advertisedNamePrefix)) return
            devices[result.device.address] = result.device
            onEvent(ProvisioningEvent.DeviceDiscovered(ProvisioningDevice(result.device.address, name, result.rssi)))
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            onEvent(ProvisioningEvent.Failed("Bluetooth scan failed (code $errorCode)."))
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
                BluetoothDevice.BOND_NONE -> onEvent(ProvisioningEvent.Failed("Bluetooth pairing was not completed."))
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || newState != BluetoothProfile.STATE_CONNECTED) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!intentionalDisconnect) onEvent(ProvisioningEvent.Failed("Bluetooth connection closed."))
                    closeGatt()
                }
                return
            }
            intentionalDisconnect = false
            this@AndroidBleProvisioningClient.gatt = gatt
            val device = selectedDevice ?: gatt.device
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                prepareGatt(gatt)
            } else {
                onEvent(ProvisioningEvent.Pairing)
                if (!device.createBond()) onEvent(ProvisioningEvent.Failed("Android could not start Bluetooth pairing."))
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!gatt.discoverServices()) onEvent(ProvisioningEvent.Failed("Could not start service discovery."))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onEvent(ProvisioningEvent.Failed("DriveAlert service discovery failed."))
                return
            }
            val service: BluetoothGattService = gatt.getService(ProvisioningProtocol.serviceUuid) ?: run {
                onEvent(ProvisioningEvent.Failed("Selected device does not expose the DriveAlert service."))
                return
            }
            ssidCharacteristic = service.getCharacteristic(ProvisioningProtocol.ssidUuid)
            passwordCharacteristic = service.getCharacteristic(ProvisioningProtocol.passwordUuid)
            applyCharacteristic = service.getCharacteristic(ProvisioningProtocol.applyUuid)
            statusCharacteristic = service.getCharacteristic(ProvisioningProtocol.statusUuid)
            val statusCharacteristic = statusCharacteristic
            if (ssidCharacteristic == null || passwordCharacteristic == null || applyCharacteristic == null || statusCharacteristic == null) {
                onEvent(ProvisioningEvent.Failed("DriveAlert provisioning characteristics are incomplete."))
                return
            }
            if (!gatt.setCharacteristicNotification(statusCharacteristic, true)) {
                onEvent(ProvisioningEvent.Failed("Could not enable provisioning status updates."))
                return
            }
            val descriptor = statusCharacteristic.getDescriptor(ProvisioningProtocol.clientConfigurationUuid) ?: run {
                onEvent(ProvisioningEvent.Failed("Provisioning notification descriptor is missing."))
                return
            }
            if (!writeDescriptor(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                onEvent(ProvisioningEvent.Failed("Could not subscribe to provisioning status."))
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onEvent(ProvisioningEvent.BleReady)
                queryStoredCredentials(gatt)
            } else onEvent(ProvisioningEvent.Failed("Secure status subscription failed."))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val completed = inFlightWrite
            inFlightWrite = null
            if (completed?.characteristic?.uuid == ProvisioningProtocol.passwordUuid) completed.payload.fill(0)
            @Suppress("DEPRECATION")
            if (characteristic.uuid == ProvisioningProtocol.passwordUuid) characteristic.value = ByteArray(0)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                clearQueuedSecrets()
                onEvent(ProvisioningEvent.Failed("Secure credential transfer failed."))
                return
            }
            if (completed?.startsWifiConnection == true) onEvent(ProvisioningEvent.WifiConnecting)
            writeNext(gatt)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleStatus(characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
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

    fun startScan() {
        closeGatt()
        devices.clear()
        val scanner = adapter?.bluetoothLeScanner ?: run {
            onEvent(ProvisioningEvent.Failed("Bluetooth is unavailable or turned off."))
            return
        }
        onEvent(ProvisioningEvent.ScanStarted)
        scanning = true
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(ProvisioningProtocol.serviceUuid)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, scanCallback)
        mainHandler.postDelayed({
            if (!scanning) return@postDelayed
            stopScan()
            if (devices.isEmpty()) onEvent(ProvisioningEvent.Failed("No DriveAlert provisioning device was found."))
        }, SCAN_TIMEOUT_MS)
    }

    fun connect(deviceId: String) {
        val device = devices[deviceId] ?: run {
            onEvent(ProvisioningEvent.Failed("Selected DriveAlert device is no longer available."))
            return
        }
        stopScan()
        closeGatt()
        selectedDevice = device
        onEvent(ProvisioningEvent.Connecting(deviceId))
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun submit(credentials: WifiCredentials) {
        val activeGatt = gatt ?: run {
            onEvent(ProvisioningEvent.Failed("Bluetooth is not connected."))
            return
        }
        val ssid = ssidCharacteristic
        val password = passwordCharacteristic
        val apply = applyCharacteristic
        if (ssid == null || password == null || apply == null) {
            onEvent(ProvisioningEvent.Failed("Provisioning service is not ready."))
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
        onEvent(ProvisioningEvent.CredentialsSending)
        writeNext(activeGatt)
    }

    fun connectUsingSavedCredentials() {
        val activeGatt = gatt ?: run {
            onEvent(ProvisioningEvent.Failed("Bluetooth is not connected."))
            return
        }
        val apply = applyCharacteristic ?: run {
            onEvent(ProvisioningEvent.Failed("Provisioning service is not ready."))
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
        closeGatt()
        onEvent(ProvisioningEvent.Retry)
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
            onEvent(ProvisioningEvent.Failed("Could not prepare the Bluetooth connection."))
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
            onEvent(ProvisioningEvent.Failed("Could not queue secure credential transfer."))
        }
    }

    private fun handleStatus(bytes: ByteArray) {
        val status = bytes.toString(Charsets.UTF_8)
        when {
            status == "CONNECTING" -> onEvent(ProvisioningEvent.WifiConnecting)
            status == "CREDENTIALS|NONE" -> onEvent(ProvisioningEvent.CredentialStatus(null))
            status.startsWith("CREDENTIALS|SAVED|") -> {
                val ssid = parseCredentialStatus(status)
                if (ssid == null) onEvent(ProvisioningEvent.Failed("ESP32 returned an invalid saved network."))
                else onEvent(ProvisioningEvent.CredentialStatus(ssid))
            }
            status.startsWith("CONNECTED|") -> {
                val endpoint = parseConnectedStatus(status)
                if (endpoint == null) {
                    onEvent(ProvisioningEvent.Failed("ESP32 returned an invalid network address."))
                    return
                }
                onEvent(ProvisioningEvent.Provisioned(endpoint.ip, endpoint.hostname, selectedDevice?.address))
                clearQueuedSecrets()
                intentionalDisconnect = true
                gatt?.disconnect()
            }
            status == "INVALID_CREDENTIALS" -> onEvent(ProvisioningEvent.Failed("SSID or password format was rejected."))
            status == "CONNECTION_FAILED" -> onEvent(ProvisioningEvent.Failed("ESP32 could not join that Wi-Fi network."))
            status == "ERROR" -> onEvent(ProvisioningEvent.Failed("ESP32 could not store the working credentials."))
        }
    }

    private fun stopScan() {
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
        clearQueuedSecrets()
        intentionalDisconnect = true
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        selectedDevice = null
        ssidCharacteristic = null
        passwordCharacteristic = null
        applyCharacteristic = null
        statusCharacteristic = null
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
    }
}
