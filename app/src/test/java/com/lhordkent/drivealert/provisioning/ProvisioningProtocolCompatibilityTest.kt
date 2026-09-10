package com.lhordkent.drivealert.provisioning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProvisioningProtocolCompatibilityTest {
    @Test
    fun parsesLegacyConnectedStatus() {
        val endpoint = AndroidBleProvisioningClient.parseConnectedStatus("CONNECTED|192.168.43.12")
        assertEquals("192.168.43.12", endpoint?.ip)
        assertNull(endpoint?.hostname)
    }

    @Test
    fun parsesHostnameConnectedStatus() {
        val endpoint = AndroidBleProvisioningClient.parseConnectedStatus("CONNECTED|192.168.43.12|drivealert-e9d4")
        assertEquals("192.168.43.12", endpoint?.ip)
        assertEquals("drivealert-e9d4", endpoint?.hostname)
    }

    @Test
    fun rejectsMalformedStatus() {
        assertNull(AndroidBleProvisioningClient.parseConnectedStatus("CONNECTED|"))
    }

    @Test
    fun parsesSavedSsidWithoutRemovingSignificantWhitespace() {
        assertEquals(
            "maming ",
            AndroidBleProvisioningClient.parseCredentialStatus("CREDENTIALS|SAVED|maming "),
        )
    }

    @Test
    fun savedSsidMayContainProtocolDelimiter() {
        assertEquals(
            "mobile|hotspot",
            AndroidBleProvisioningClient.parseCredentialStatus("CREDENTIALS|SAVED|mobile|hotspot"),
        )
    }

    @Test
    fun rejectsEmptyOrUnrelatedCredentialStatus() {
        assertNull(AndroidBleProvisioningClient.parseCredentialStatus("CREDENTIALS|SAVED|"))
        assertNull(AndroidBleProvisioningClient.parseCredentialStatus("CREDENTIALS|NONE"))
    }
}
