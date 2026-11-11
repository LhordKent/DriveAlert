package com.lhordkent.drivealert.monitoring

import org.junit.Assert.*
import org.junit.Test

class FaceLightingTrackerTest {
    @Test
    fun `covered dark camera chooses lighting instead of missing driver`() {
        val lost = com.lhordkent.drivealert.detection.face.DriverTrackingState(
            status = com.lhordkent.drivealert.detection.face.DriverTrackingStatus.LOST, detectedFaceCount = 0)
        assertEquals(VisibilityIssue.INSUFFICIENT_LIGHTING, monitoringVisibilityIssue(false, FaceLightingAssessment(),
            true, lost, true, com.lhordkent.drivealert.detection.model.RegionalVisibility()))
        assertEquals(VisibilityIssue.FACE_UNAVAILABLE, monitoringVisibilityIssue(false, FaceLightingAssessment(),
            false, lost, true, com.lhordkent.drivealert.detection.model.RegionalVisibility()))
    }

    @Test
    fun `an out of area face or ambiguity cannot be mislabeled darkness`() {
        for (count in listOf(1, 4)) {
            val tracking = com.lhordkent.drivealert.detection.face.DriverTrackingState(
                status = com.lhordkent.drivealert.detection.face.DriverTrackingStatus.LOST, detectedFaceCount = count)
            assertEquals(VisibilityIssue.FACE_UNAVAILABLE, monitoringVisibilityIssue(false, FaceLightingAssessment(),
                true, tracking, true, com.lhordkent.drivealert.detection.model.RegionalVisibility()))
        }
    }

    @Test
    fun `setup detects covered camera without requiring face lighting samples or alignment`() {
        val state = com.lhordkent.drivealert.detection.DriverVisionUiState(
            streamState = com.lhordkent.drivealert.detection.frame.StreamConnectionState.CONNECTED,
            lowLight = true,
            driverTracking = com.lhordkent.drivealert.detection.face.DriverTrackingState(
                status = com.lhordkent.drivealert.detection.face.DriverTrackingStatus.LOST, detectedFaceCount = 0))
        assertTrue(state.insufficientLighting)
        assertFalse(state.copy(streamState = com.lhordkent.drivealert.detection.frame.StreamConnectionState.RECONNECTING).insufficientLighting)
        assertFalse(state.copy(lowLight = false).insufficientLighting)
    }

    @Test
    fun `every fresh sample resolves to usable or insufficient with no threshold gap`() {
        for (luminance in listOf(0.0, 24.9, 25.0, 35.5, 44.9, 45.0, 100.0)) {
            for (contrast in listOf(0.0, 19.9, 20.0, 24.0, 255.0)) {
                val tracker = FaceLightingTracker()
                var result = FaceLightingAssessment()
                for (time in 0L..2_000L step 500) result = tracker.update(time, sample(time, luminance, contrast))
                val expected = if (luminance < 25.0 || luminance < 45.0 && contrast < 20.0)
                    LightingQuality.INSUFFICIENT else LightingQuality.USABLE
                assertEquals("L=$luminance C=$contrast", expected, result.quality)
            }
        }
    }

    @Test
    fun `recorded dim camera readings become usable after two seconds instead of waiting forever`() {
        val tracker = FaceLightingTracker()
        // Captured from the connected TECNO/ESP32-CAM while the face was locked.
        val readings = listOf(50.7 to 34.0, 49.6 to 38.0, 50.4 to 37.0, 52.1 to 37.0,
            49.2 to 38.0, 51.5 to 38.0, 53.1 to 35.0, 49.6 to 37.0, 45.0 to 34.0,
            47.6 to 34.0, 48.8 to 33.0)
        readings.forEachIndexed { index, (luminance, contrast) ->
            val time = index * 200L
            val assessment = tracker.update(time + 380, sample(time, luminance, contrast))
            if (time < 2_000) {
                assertTrue(assessment.recovering)
                assertFalse(assessment.allowsAssessment(time + 380))
            } else {
                assertEquals(LightingQuality.USABLE, assessment.quality)
                assertTrue(assessment.allowsAssessment(time + 380))
            }
        }
    }

    @Test
    fun `deep darkness still pauses immediately after admitting the dim camera trace`() {
        val tracker = FaceLightingTracker()
        for (time in 0L..2_000L step 200) tracker.update(time, sample(time, 48.0, 33.0))
        val dark = tracker.update(2_200, sample(2_200, 20.0, 15.0))
        assertEquals(LightingQuality.INSUFFICIENT, dark.quality)
        assertFalse(dark.allowsAssessment(2_200))
        assertEquals("Insufficient lighting", dark.statusLabel)
    }

    @Test
    fun `captured brightness 35 point 5 and contrast 24 become usable without a third lighting state`() {
        val tracker = FaceLightingTracker()
        for (time in 0L..10_000L step 200) {
            val assessment = tracker.update(time, sample(time, 35.5, 24.0))
            if (time < 2_000) {
                assertEquals("Checking lighting", assessment.statusLabel)
                assertFalse(assessment.allowsAssessment(time))
            } else {
                assertEquals("Usable", assessment.statusLabel)
                assertTrue(assessment.allowsAssessment(time))
            }
        }
        assertEquals("Waiting for lighting sample", tracker.update(10_200, null).statusLabel)
    }

    @Test
    fun `missing samples and initial recovery never enable assessment`() {
        val tracker = FaceLightingTracker()
        assertFalse(tracker.update(0, null).allowsAssessment(0))
        val checking = tracker.update(200, sample(200, 30.0, 22.0))
        assertEquals(LightingQuality.UNKNOWN, checking.quality)
        assertEquals("Checking lighting", checking.statusLabel)
        assertFalse(checking.allowsAssessment(200))
    }

    @Test
    fun `poor threshold boundaries and high contrast dim lighting`() {
        assertEquals(LightingQuality.INSUFFICIENT, FaceLightingTracker().update(0, sample(0, 24.9, 100.0)).quality)
        assertEquals(LightingQuality.INSUFFICIENT, FaceLightingTracker().update(0, sample(0, 44.9, 19.9)).quality)
        assertTrue(FaceLightingTracker().update(0, sample(0, 25.0, 20.0)).recovering)
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
    fun `all nonpoor readings count continuously toward recovery from darkness`() {
        val tracker = readyTracker()
        assertTrue(tracker.update(2_200, sample(2_200, 30.0, 22.0)).allowsAssessment(2_200))
        tracker.update(2_400, sample(2_400, 20.0, 0.0))
        tracker.update(2_600, sample(2_600))
        val recovering = tracker.update(2_800, sample(2_800, 30.0, 22.0))
        assertTrue(recovering.recovering)
        assertEquals(LightingQuality.INSUFFICIENT, recovering.quality)
        for (time in 3_000L..4_400L step 200L) assertFalse(tracker.update(time, sample(time)).allowsAssessment(time))
        assertTrue(tracker.update(4_600, sample(4_600)).allowsAssessment(4_600))
    }

    @Test
    fun `stale samples pause and only fresh good samples can restore recent usability`() {
        assertFalse(readyTracker().update(5_000, sample(5_000)).allowsAssessment(5_000))
        val tracker = readyTracker()
        val usable = tracker.update(2_000, sample(2_000))
        assertTrue(usable.allowsAssessment(3_000))
        assertFalse(usable.allowsAssessment(3_001))
        assertEquals(LightingQuality.UNKNOWN, tracker.update(3_001, sample(2_000)).quality)
        assertTrue(tracker.update(3_200, sample(3_200)).allowsAssessment(3_200))
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

    @Test fun `brief missing sample pauses without repeating two second recovery for unchanged good lighting`() {
        val tracker = readyTracker()
        assertFalse(tracker.update(2_200, null).allowsAssessment(2_200))
        assertFalse(tracker.update(2_400, sample(2_000)).allowsAssessment(2_400))
        assertTrue(tracker.update(2_600, sample(2_600)).allowsAssessment(2_600))
    }

    @Test fun `freshness gaps do not make a stable well lit face restart recovery forever`() {
        val tracker = readyTracker()
        for (time in listOf(3_200L, 4_400L, 5_600L, 6_800L)) {
            assertFalse(tracker.update(time - 100, null).allowsAssessment(time - 100))
            assertTrue(tracker.update(time, sample(time)).allowsAssessment(time))
        }
    }

    @Test fun `insufficient pixels after a sample gap cannot use the quick resume path`() {
        for ((luminance, contrast) in listOf(20.0 to 35.0, 40.0 to 10.0)) {
            val tracker = readyTracker()
            tracker.update(2_200, null)
            assertFalse(tracker.update(2_400, sample(2_400, luminance, contrast)).allowsAssessment(2_400))
            for (time in 2_600L..4_400L step 200) assertFalse(tracker.update(time, sample(time)).allowsAssessment(time))
            assertTrue(tracker.update(4_600, sample(4_600)).allowsAssessment(4_600))
        }
    }

    @Test fun `missing samples cannot bypass real darkness recovery or explicit target reset`() {
        val tracker = readyTracker()
        tracker.update(2_200, sample(2_200, 20.0, 0.0))
        tracker.update(2_400, null)
        assertFalse(tracker.update(2_600, sample(2_600)).allowsAssessment(2_600))
        tracker.reset()
        assertFalse(tracker.update(3_000, sample(3_000)).allowsAssessment(3_000))
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
