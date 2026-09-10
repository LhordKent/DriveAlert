package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointResolutionPolicyTest {
    private val persisted = ProvisionedDevice("camera", "driver", "drivealert-e9d4", "192.168.43.12", lastConnectedAtEpochMillis = 1)

    @Test
    fun reachablePersistedIpWinsWithoutDiscovery() = runBlocking {
        var discoveryCalled = false
        val resolved = EndpointResolutionPolicy().resolve(persisted, { true }) {
            discoveryCalled = true
            null
        }
        assertEquals(persisted, resolved)
        assertFalse(discoveryCalled)
    }

    @Test
    fun mdnsReplacesStaleDhcpAddress() = runBlocking {
        val discovered = persisted.copy(lastKnownIp = "192.168.43.25")
        assertEquals(discovered, EndpointResolutionPolicy().resolve(persisted, { false }) { discovered })
    }

    @Test
    fun unresolvedDeviceReturnsNull() = runBlocking {
        assertNull(EndpointResolutionPolicy().resolve(persisted, { false }) { null })
    }
}
