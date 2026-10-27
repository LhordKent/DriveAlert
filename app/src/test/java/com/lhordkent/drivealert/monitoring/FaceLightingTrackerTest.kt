package com.lhordkent.drivealert.monitoring

import org.junit.Assert.*
import org.junit.Test

class FaceLightingTrackerTest {
    @Test
    fun `unknown and initial borderline lighting never enable assessment`() {
        val tracker = FaceLightingTracker()
        assertFalse(tracker.update(0, null).allowsAssessment(0))
        assertEquals(LightingQuality.UNKNOWN, tracker.update(200, sample(200, 45.0, 30.0)).quality)
    }

    @Test
    fun `poor threshold boundaries and high contrast dim lighting`() {
        assertEquals(LightingQuality.INSUFFICIENT, FaceLightingTracker().update(0, sample(0, 24.9, 100.0)).quality)
        assertEquals(LightingQuality.INSUFFICIENT, FaceLightingTracker().update(0, sample(0, 44.9, 29.9)).quality)
        assertEquals(LightingQuality.UNKNOWN, FaceLightingTracker().update(0, sample(0, 25.0, 30.0)).quality)
        val tracker = FaceLightingTracker()
        for (time in 0L..2_000L step 200L) tracker.update(time, sample(time, 35.0, 40.0))
        assertTrue(tracker.update(2_000, sample(2_000, 35.0, 40.0)).allowsAssessment(2_000))
    }

    @Test
    fun `poor pixels gate immediately and recovery needs two continuous seconds`() {
        val tracker = readyTracker()
        val poor = tracker.update(2_200, sample(2_200, 20.0, 10.0))
        assertFalse(poor.allowsAssessment(2_200))
        assertEquals(LightingQuality.INSUFFICIENT, poor.quality)
        for (time in 2_400L..4_200L step 200L) {
            val recovering = tracker.update(time, sample(time, 55.0, 0.0))
            assertTrue(recovering.recovering)
            assertFalse(recovering.allowsAssessment(time))
        }
        assertTrue(tracker.update(4_400, sample(4_400, 55.0, 0.0)).allowsAssessment(4_400))
    }

    @Test
    fun `borderline readings hold usable state but interrupt recovery from darkness`() {
        val tracker = readyTracker()
        assertTrue(tracker.update(2_200, sample(2_200, 50.0, 35.0)).allowsAssessment(2_200))
        tracker.update(2_400, sample(2_400, 20.0, 0.0))
        tracker.update(2_600, sample(2_600))
        val borderline = tracker.update(2_800, sample(2_800, 50.0, 35.0))
        assertFalse(borderline.recovering)
        assertEquals(LightingQuality.INSUFFICIENT, borderline.quality)
        for (time in 3_000L..4_800L step 200L) assertFalse(tracker.update(time, sample(time)).allowsAssessment(time))
        assertTrue(tracker.update(5_000, sample(5_000)).allowsAssessment(5_000))
    }

    @Test
    fun `stale gaps missing samples and reset discard previous usability`() {
        assertFalse(readyTracker().update(5_000, sample(5_000)).allowsAssessment(5_000))
        val tracker = readyTracker()
        val usable = tracker.update(2_000, sample(2_000))
        assertTrue(usable.allowsAssessment(3_000))
        assertFalse(usable.allowsAssessment(3_001))
        assertEquals(LightingQuality.UNKNOWN, tracker.update(3_001, sample(2_000)).quality)
        assertFalse(tracker.update(3_200, sample(3_200)).allowsAssessment(3_200))
        tracker.reset()
        assertEquals(LightingQuality.UNKNOWN, tracker.update(3_400, null).quality)
    }

    @Test
    fun `cached samples do not count as fresh recovery evidence`() {
        val tracker = FaceLightingTracker()
        val first = sample(0)
        tracker.update(0, first)
        tracker.update(900, first)
        assertFalse(tracker.update(1_001, first).allowsAssessment(1_001))
        assertFalse(tracker.update(2_200, sample(2_200)).allowsAssessment(2_200))
    }

    @Test
    fun `missing face only attributes lighting failure to confirmed scene darkness`() {
        assertEquals(VisibilityIssue.FACE_UNAVAILABLE, lightingVisibilityIssue(false, FaceLightingAssessment(), false))
        assertEquals(VisibilityIssue.INSUFFICIENT_LIGHTING, lightingVisibilityIssue(false, FaceLightingAssessment(), true))
        val poor = FaceLightingTracker().update(0, sample(0, 20.0, 0.0))
        assertEquals(VisibilityIssue.INSUFFICIENT_LIGHTING, lightingVisibilityIssue(true, poor, false))
        assertNull(lightingVisibilityIssue(true, readyTracker().update(2_000, sample(2_000)), true))
    }

    @Test
    fun `speech starts three seconds after first poor reading and clears with recovery`() {
        val tracker = readyTracker()
        val controller = VisibilityAlertController()
        for (time in 2_200L..5_000L step 200L) {
            val lighting = tracker.update(time, sample(time, 20.0, 0.0))
            assertTrue(controller.update(time, lightingVisibilityIssue(true, lighting, false)).isEmpty())
        }
        val poor = tracker.update(5_200, sample(5_200, 20.0, 0.0))
        assertEquals(listOf(VisibilityAlertEffect.Notify(VisibilityIssue.INSUFFICIENT_LIGHTING)),
            controller.update(5_200, lightingVisibilityIssue(true, poor, false)))
        for (time in 5_400L..7_200L step 200L) {
            val lighting = tracker.update(time, sample(time))
            assertFalse(lighting.allowsAssessment(time))
            assertTrue(controller.update(time, lightingVisibilityIssue(true, lighting, false)).isEmpty())
        }
        val restored = tracker.update(7_400, sample(7_400))
        assertTrue(restored.allowsAssessment(7_400))
        assertEquals(listOf(VisibilityAlertEffect.Clear), controller.update(7_400, lightingVisibilityIssue(true, restored, false)))
    }

    private fun readyTracker() = FaceLightingTracker().apply {
        for (time in 0L..2_000L step 200L) update(time, sample(time))
    }

    private fun sample(time: Long, luminance: Double = 100.0, contrast: Double = 60.0) =
        FaceLightingSample(time, luminance, contrast)
}
