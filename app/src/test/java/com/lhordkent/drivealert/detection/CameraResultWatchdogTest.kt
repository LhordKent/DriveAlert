package com.lhordkent.drivealert.detection

import org.junit.Assert.*
import org.junit.Test

class CameraResultWatchdogTest {
    @Test fun `real phone inference latency does not add to the pipeline silence timer`() {
        val watchdog = CameraResultWatchdog(1_000L)
        // Phone trace: 450 ms processing and ~600 ms capture cadence. The old
        // timer expired at 1,082 ms source age just before a valid result arrived.
        assertTrue(watchdog.accept(0, 450))
        assertFalse(watchdog.expired(1_082))
        assertTrue(watchdog.accept(650, 1_100))
        assertFalse(watchdog.expired(1_732))
        assertTrue(watchdog.accept(1_300, 1_750))
        assertFalse(watchdog.expired(2_750))
        assertTrue(watchdog.expired(2_751)) // Actual inference silence still pauses.
    }

    @Test fun `old or future captured results never refresh the watchdog`() {
        val watchdog = CameraResultWatchdog(1_000L)
        assertTrue(watchdog.accept(0, 100))
        assertFalse(watchdog.accept(0, 1_101))
        assertFalse(watchdog.accept(1_500, 1_101))
        assertTrue(watchdog.expired(1_101))
        watchdog.reset()
        assertFalse(watchdog.expired(10_000))
    }
}
