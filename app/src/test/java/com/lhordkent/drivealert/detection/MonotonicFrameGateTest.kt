package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.detection.frame.MonotonicFrameGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonotonicFrameGateTest {
    @Test
    fun acceptsFirstFrameThenDropsFramesInsideInterval() {
        val gate = MonotonicFrameGate(100)

        assertTrue(gate.tryAcquire(1_000))
        assertFalse(gate.tryAcquire(1_099))
        assertTrue(gate.tryAcquire(1_100))
    }

    @Test
    fun resetAndClockRollbackAcceptFreshFrame() {
        val gate = MonotonicFrameGate(100)

        assertTrue(gate.tryAcquire(1_000))
        assertTrue(gate.tryAcquire(900))
        assertFalse(gate.tryAcquire(950))
        gate.reset()
        assertTrue(gate.tryAcquire(950))
    }
}
