package com.lhordkent.drivealert.monitoring

import org.junit.Assert.*
import org.junit.Test

class SceneBrightnessTrackerTest {
    @Test
    fun `three consecutive dark samples produce a low light hint`() {
        val tracker = SceneBrightnessTracker(smoothingAlpha = 1.0)
        assertFalse(tracker.update(0, 20.0).lowLight)
        assertFalse(tracker.update(1_000, 20.0).lowLight)
        assertTrue(tracker.update(2_000, 20.0).lowLight)
    }

    @Test
    fun `middle band interrupts darkness and does not clear an existing hint`() {
        val tracker = SceneBrightnessTracker(smoothingAlpha = 1.0)
        tracker.update(0, 20.0)
        tracker.update(1_000, 60.0)
        assertFalse(tracker.update(2_000, 20.0).lowLight)
        tracker.update(3_000, 20.0)
        assertTrue(tracker.update(4_000, 20.0).lowLight)
        assertTrue(tracker.update(5_000, 60.0).lowLight)
    }

    @Test
    fun `three bright readings clear darkness without an LED minimum on timer`() {
        val tracker = SceneBrightnessTracker(smoothingAlpha = 1.0)
        repeat(3) { tracker.update(it * 1_000L, 20.0) }
        assertTrue(tracker.update(3_000, 150.0).lowLight)
        assertTrue(tracker.update(4_000, 150.0).lowLight)
        assertFalse(tracker.update(5_000, 150.0).lowLight)
    }

    @Test
    fun `readings are smoothed`() {
        val tracker = SceneBrightnessTracker()
        tracker.update(0, 100.0)
        assertEquals(80.0, tracker.update(1_000, 20.0).smoothed, 0.0001)
    }

    @Test
    fun `reset and stale gaps discard the previous dark scene`() {
        val tracker = SceneBrightnessTracker(smoothingAlpha = 1.0)
        repeat(3) { tracker.update(it * 1_000L, 20.0) }
        tracker.reset()
        assertFalse(tracker.update(3_000, 20.0).lowLight)
        tracker.update(4_000, 20.0)
        assertTrue(tracker.update(5_000, 20.0).lowLight)
        val afterGap = tracker.update(9_000, 150.0)
        assertFalse(afterGap.lowLight)
        assertEquals(150.0, afterGap.smoothed, 0.0001)
    }
}
