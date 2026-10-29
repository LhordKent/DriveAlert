package com.lhordkent.drivealert.provisioning

import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class BleSetupRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun rememberedDeviceSurvivesStoreRecreationAndIsScopedToDriver() {
        val prefs = context.getSharedPreferences("ble_setup_recovery_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val device = ProvisioningDevice("02:00:00:00:00:01", "DriveAlert-Test", -42)
            BleSetupRecoveryStore(prefs).save("driver-a", device)
            val restored = BleSetupRecoveryStore(prefs).load("driver-a")!!
            assertEquals(device.id, restored.id)
            assertEquals(device.name, restored.name)
            assertTrue(restored.previouslyKnown)
            assertNull(BleSetupRecoveryStore(prefs).load("driver-b"))
            assertNull(BleSetupRecoveryStore(prefs).load(null))
            assertEquals(setOf("driver-a.address", "driver-a.name"), prefs.all.keys)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test
    fun rememberedDeviceIsSelectableWithoutAdvertisingAndUnreachableConnectionTimesOut() {
        val adapter = context.getSystemService(BluetoothManager::class.java).adapter
        assumeTrue(adapter?.isEnabled == true)
        val events = CopyOnWriteArrayList<ProvisioningEvent>()
        lateinit var client: AndroidBleProvisioningClient
        val device = ProvisioningDevice("02:00:00:00:00:01", "DriveAlert-Test", 0, previouslyKnown = true)
        instrumentation.runOnMainSync {
            client = AndroidBleProvisioningClient(context, { events.add(it) }, connectionTimeoutMs = 500)
        }
        try {
            instrumentation.runOnMainSync {
                client.startScan(device)
                assertTrue(events.any { it is ProvisioningEvent.DeviceDiscovered && it.device.id == device.id && it.device.previouslyKnown })
                client.connect(device.id)
            }
            val deadline = System.currentTimeMillis() + 5_000
            while (events.none { it is ProvisioningEvent.Failed } && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertTrue("Unreachable BLE must leave CONNECTING instead of hanging", events.any { it is ProvisioningEvent.Failed })
            instrumentation.runOnMainSync {
                client.retry()
                client.startScan(device)
                assertTrue(events.last() is ProvisioningEvent.DeviceDiscovered)
            }
        } finally {
            instrumentation.runOnMainSync { client.close() }
        }
    }

    @Test
    fun disconnectFromPreviousAttemptCannotCloseReplacementConnection() {
        assumeTrue(context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true)
        val events = CopyOnWriteArrayList<ProvisioningEvent>()
        lateinit var client: AndroidBleProvisioningClient
        val device = ProvisioningDevice("02:00:00:00:00:01", "DriveAlert-Test", 0, true)
        instrumentation.runOnMainSync {
            client = AndroidBleProvisioningClient(context, { events.add(it) })
        }
        try {
            // Use actual Android GATT handles; simulate only the delayed callback
            // that arrives after the user retries. No peripheral is required.
            val field = AndroidBleProvisioningClient::class.java.getDeclaredField("gatt").apply { isAccessible = true }
            val callbackField = AndroidBleProvisioningClient::class.java.getDeclaredField("gattCallback").apply { isAccessible = true }
            lateinit var replacement: BluetoothGatt
            instrumentation.runOnMainSync {
                client.startScan(device)
                client.connect(device.id)
                val previous = field.get(client) as BluetoothGatt
                client.retry()
                client.startScan(device)
                client.connect(device.id)
                replacement = field.get(client) as BluetoothGatt
                assertNotSame(previous, replacement)
                (callbackField.get(client) as BluetoothGattCallback)
                    .onConnectionStateChange(previous, 133, BluetoothProfile.STATE_DISCONNECTED)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertSame(replacement, field.get(client))
                assertTrue(events.last() is ProvisioningEvent.Connecting)
            }
        } finally {
            instrumentation.runOnMainSync { client.close() }
        }
    }

    @Test
    fun liveDeviceReconnectsAfterClientRecreationWithoutWifiCommand() {
        val address = InstrumentationRegistry.getArguments().getString("liveBleDevice")
        assumeTrue("Pass liveBleDevice to run against hardware", address != null)
        val device = ProvisioningDevice(address!!, "DriveAlert device", 0, true)
        repeat(2) {
            val events = CopyOnWriteArrayList<ProvisioningEvent>()
            lateinit var client: AndroidBleProvisioningClient
            instrumentation.runOnMainSync {
                client = AndroidBleProvisioningClient(context, { events.add(it) })
            }
            try {
                instrumentation.runOnMainSync {
                    client.startScan(device)
                    client.connect(device.id)
                }
                val deadline = System.currentTimeMillis() + 35_000
                while (events.none { it is ProvisioningEvent.CredentialStatus || it is ProvisioningEvent.Failed } &&
                    System.currentTimeMillis() < deadline) Thread.sleep(100)
                assertTrue("Attempt $it must read saved-network status; events=${events.map { it.javaClass.simpleName }}",
                    events.any { it is ProvisioningEvent.CredentialStatus })
                assertFalse(events.any { it is ProvisioningEvent.WifiConnecting || it is ProvisioningEvent.Provisioned })
            } finally {
                instrumentation.runOnMainSync { client.close() }
            }
            Thread.sleep(1_000)
        }
    }
}
