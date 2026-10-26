package com.lhordkent.drivealert.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringServicePolicyTest {
    @Test
    fun repeatedStartsAreRejectedWhileStartingOrActive() {
        assertTrue(monitoringStartAllowed(isActive = false, startInProgress = false))
        assertFalse(monitoringStartAllowed(isActive = false, startInProgress = true))
        assertFalse(monitoringStartAllowed(isActive = true, startInProgress = false))
    }

    @Test
    fun recoveryOccursOnlyWithinTheSameBoot() {
        assertEquals(RecoveryDisposition.NONE, recoveryDisposition(null, 12))
        assertEquals(RecoveryDisposition.RECOVER_SAME_BOOT, recoveryDisposition(12, 12))
        assertEquals(RecoveryDisposition.INTERRUPT_AFTER_REBOOT, recoveryDisposition(11, 12))
        assertEquals(RecoveryDisposition.INTERRUPT_AFTER_REBOOT, recoveryDisposition(-1, -1))
    }
}
