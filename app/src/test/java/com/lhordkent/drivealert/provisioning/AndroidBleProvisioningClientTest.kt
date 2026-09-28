package com.lhordkent.drivealert.provisioning

import android.bluetooth.le.ScanCallback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidBleProvisioningClientTest {
    @Test
    fun `already-started scan is treated as an active scan instead of a failure`() {
        assertTrue(
            AndroidBleProvisioningClient.isScanAlreadyRunning(
                ScanCallback.SCAN_FAILED_ALREADY_STARTED,
            ),
        )
    }

    @Test
    fun `other Android scan errors remain failures`() {
        assertFalse(
            AndroidBleProvisioningClient.isScanAlreadyRunning(
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED,
            ),
        )
    }

    @Test
    fun `DriveAlert advertised names are accepted without a controller UUID filter`() {
        assertTrue(AndroidBleProvisioningClient.isDriveAlertAdvertisement("DriveAlert-A1B2"))
        assertFalse(AndroidBleProvisioningClient.isDriveAlertAdvertisement("OtherDevice-A1B2"))
        assertFalse(AndroidBleProvisioningClient.isDriveAlertAdvertisement(null))
    }
}
