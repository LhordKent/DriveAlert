package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private class FakeClock(var now: Long) {
        val pauses = mutableListOf<Long>()
        val pause: suspend (Long) -> Unit = { pauses += it; now += it }
    }

    private fun policy(clock: FakeClock) = EndpointResolutionPolicy(
        nowEpochMillis = { clock.now },
        pause = clock.pause,
    )

    @Test
    fun freshHandoffPollsSavedIpUntilCameraFinishesRestarting() = runBlocking {
        val clock = FakeClock(now = 10_000)
        val fresh = persisted.copy(lastConnectedAtEpochMillis = 9_000)
        var checks = 0
        var discoveryCalled = false
        val resolved = policy(clock).resolve(fresh, { ++checks >= 8 }) {
            discoveryCalled = true
            null
        }
        assertEquals(fresh, resolved)
        assertEquals(8, checks)
        assertFalse(discoveryCalled)
        assertTrue(clock.pauses.all { it == EndpointResolutionPolicy.FAST_POLL_INTERVAL_MS })
    }

    @Test
    fun freshHandoffFallsBackToMdnsAfterPollBudget() = runBlocking {
        val clock = FakeClock(now = 10_000)
        val fresh = persisted.copy(lastConnectedAtEpochMillis = 9_000)
        val discovered = fresh.copy(lastKnownIp = "192.168.43.25")
        val resolved = policy(clock).resolve(fresh, { false }) { discovered }
        assertEquals(discovered, resolved)
        assertTrue(clock.now - 10_000 >= EndpointResolutionPolicy.FAST_POLL_BUDGET_MS)
        assertTrue(clock.now - 10_000 < EndpointResolutionPolicy.FAST_POLL_BUDGET_MS + EndpointResolutionPolicy.FAST_POLL_INTERVAL_MS)
    }

    @Test
    fun oldSavedAddressGoesStraightToMdns() = runBlocking {
        val clock = FakeClock(now = 10_000_000)
        var checks = 0
        val discovered = persisted.copy(lastKnownIp = "192.168.43.25")
        val resolved = policy(clock).resolve(persisted, { checks++; false }) { discovered }
        assertEquals(discovered, resolved)
        assertEquals(1, checks)
        assertTrue(clock.pauses.isEmpty())
    }
}
