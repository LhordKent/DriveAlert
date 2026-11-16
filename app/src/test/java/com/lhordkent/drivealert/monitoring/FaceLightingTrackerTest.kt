package com.lhordkent.drivealert.monitoring

import org.junit.Assert.*
import org.junit.Test

class FaceLightingTrackerTest {
    @Test
    fun `missing driver uses the existing face issue without guessing lighting`() {
        val lost = com.lhordkent.drivealert.detection.face.DriverTrackingState(
            status = com.lhordkent.drivealert.detection.face.DriverTrackingStatus.LOST, detectedFaceCount = 0)
        assertEquals(VisibilityIssue.FACE_UNAVAILABLE, monitoringVisibilityIssue(false, FaceLightingAssessment(),
            lost, true, com.lhordkent.drivealert.detection.model.RegionalVisibility()))
    }

    @Test
    fun `an out of area face or ambiguity cannot be mislabeled darkness`() {
        for (count in listOf(1, 4)) {
            val tracking = com.lhordkent.drivealert.detection.face.DriverTrackingState(
                status = com.lhordkent.drivealert.detection.face.DriverTrackingStatus.LOST, detectedFaceCount = count)
            assertEquals(VisibilityIssue.FACE_UNAVAILABLE, monitoringVisibilityIssue(false, FaceLightingAssessment(),
                tracking, true, com.lhordkent.drivealert.detection.model.RegionalVisibility()))
        }
    }

    @Test
    fun `dim scene and absent face do not fabricate a setup lighting failure`() {
        val state = com.lhordkent.drivealert.detection.DriverVisionUiState(
            streamState = com.lhordkent.drivealert.detection.frame.StreamConnectionState.CONNECTED,
            lowLight = true,
            driverTracking = com.lhordkent.drivealert.detection.face.DriverTrackingState(
                status = com.lhordkent.drivealert.detection.face.DriverTrackingStatus.LOST, detectedFaceCount = 0))
        assertFalse(state.insufficientLighting)
        assertFalse(state.copy(streamState = com.lhordkent.drivealert.detection.frame.StreamConnectionState.RECONNECTING).insufficientLighting)
        assertFalse(state.copy(lowLight = false).insufficientLighting)
    }

    @Test
    fun `every fresh sample resolves to usable or insufficient with no threshold gap`() {
        for (luminance in listOf(0.0, 14.9, 15.0, 18.0, 30.0, 34.9, 35.0, 100.0)) {
            for (contrast in listOf(0.0, 19.9, 20.0, 24.0, 255.0)) {
                val tracker = FaceLightingTracker()
                var result = FaceLightingAssessment()
                for (time in 0L..2_000L step 500) result = tracker.update(time, sample(time, luminance, contrast))
                val expected = if (luminance < 15.0 || luminance < 35.0 && contrast < 20.0)
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
    fun `poor crop pauses immediately but a single sample is not confirmed darkness`() {
        val tracker = FaceLightingTracker()
        for (time in 0L..2_000L step 200) tracker.update(time, sample(time, 48.0, 33.0))
        val dark = tracker.update(2_200, sample(2_200, 20.0, 15.0))
        assertEquals(LightingQuality.UNKNOWN, dark.quality)
        assertFalse(dark.allowsAssessment(2_200))
        assertEquals("Checking lighting", dark.statusLabel)
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
        assertFalse(FaceLightingTracker().update(0, sample(0, 14.9, 100.0)).allowsAssessment(0))
        assertFalse(FaceLightingTracker().update(0, sample(0, 34.9, 19.9)).allowsAssessment(0))
        assertTrue(FaceLightingTracker().update(0, sample(0, 15.0, 20.0)).recovering)
        val tracker = FaceLightingTracker()
        for (time in 0L..2_000L step 200L) tracker.update(time, sample(time, 35.0, 40.0))
        assertTrue(tracker.update(2_000, sample(2_000, 35.0, 40.0)).allowsAssessment(2_000))
    }

    @Test
    fun `poor pixels gate immediately and recovery needs two continuous seconds`() {
        val tracker = readyTracker()
        var poor = FaceLightingAssessment()
        for (time in 2_200L..4_200L step 200L) {
            poor = tracker.update(time, sample(time, 20.0, 10.0))
            assertFalse(poor.allowsAssessment(time))
        }
        assertEquals(LightingQuality.INSUFFICIENT, poor.quality)
        for (time in 4_400L..6_200L step 200L) {
            val recovering = tracker.update(time, sample(time, 55.0, 0.0))
            assertTrue(recovering.recovering)
            assertFalse(recovering.allowsAssessment(time))
        }
        assertTrue(tracker.update(6_400, sample(6_400, 55.0, 0.0)).allowsAssessment(6_400))
    }

    @Test
    fun `all nonpoor readings count continuously toward recovery from darkness`() {
        val tracker = readyTracker()
        assertTrue(tracker.update(2_200, sample(2_200, 30.0, 22.0)).allowsAssessment(2_200))
        for (time in 2_400L..4_400L step 200) tracker.update(time, sample(time, 20.0, 0.0))
        tracker.update(4_600, sample(4_600))
        val recovering = tracker.update(4_800, sample(4_800, 30.0, 22.0))
        assertTrue(recovering.recovering)
        assertEquals(LightingQuality.INSUFFICIENT, recovering.quality)
        for (time in 5_000L..6_400L step 200L) assertFalse(tracker.update(time, sample(time)).allowsAssessment(time))
        assertTrue(tracker.update(6_600, sample(6_600)).allowsAssessment(6_600))
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
        for ((luminance, contrast) in listOf(10.0 to 35.0, 30.0 to 10.0)) {
            val tracker = readyTracker()
            tracker.update(2_200, null)
            assertFalse(tracker.update(2_400, sample(2_400, luminance, contrast)).allowsAssessment(2_400))
            for (time in 2_600L..4_400L step 200) assertFalse(tracker.update(time, sample(time)).allowsAssessment(time))
            assertTrue(tracker.update(4_600, sample(4_600)).allowsAssessment(4_600))
        }
    }

    @Test fun `missing samples cannot bypass real darkness recovery or explicit target reset`() {
        val tracker = readyTracker()
        for (time in 2_200L..4_200L step 200) tracker.update(time, sample(time, 20.0, 0.0))
        tracker.update(4_400, null)
        assertFalse(tracker.update(4_600, sample(4_600)).allowsAssessment(4_600))
        tracker.reset()
        assertFalse(tracker.update(5_000, sample(5_000)).allowsAssessment(5_000))
    }

    @Test
    fun `missing face wins even over previously confirmed facial darkness`() {
        val tracker = FaceLightingTracker()
        var poor = FaceLightingAssessment()
        for (time in 0L..2_000L step 200) poor = tracker.update(time, sample(time, 20.0, 0.0))
        assertEquals(VisibilityIssue.FACE_UNAVAILABLE, lightingVisibilityIssue(false, poor))
        assertEquals(VisibilityIssue.INSUFFICIENT_LIGHTING, lightingVisibilityIssue(true, poor))
        assertNull(lightingVisibilityIssue(true, readyTracker().update(2_000, sample(2_000))))
    }

    @Test
    fun `lighting speech starts after five continuous seconds and clears with recovery`() {
        val tracker = readyTracker()
        val controller = VisibilityAlertController()
        for (time in 2_200L..7_000L step 200L) {
            val lighting = tracker.update(time, sample(time, 20.0, 0.0))
            assertTrue(controller.update(time, lightingVisibilityIssue(true, lighting), lighting).isEmpty())
        }
        val poor = tracker.update(7_200, sample(7_200, 20.0, 0.0))
        assertEquals(listOf(VisibilityAlertEffect.Notify(VisibilityIssue.INSUFFICIENT_LIGHTING)),
            controller.update(7_200, lightingVisibilityIssue(true, poor), poor))
        for (time in 7_400L..9_200L step 200L) {
            val lighting = tracker.update(time, sample(time))
            assertFalse(lighting.allowsAssessment(time))
            assertTrue(controller.update(time, lightingVisibilityIssue(true, lighting), lighting).isEmpty())
        }
        val restored = tracker.update(9_400, sample(9_400))
        assertTrue(restored.allowsAssessment(9_400))
        assertEquals(listOf(VisibilityAlertEffect.Clear), controller.update(9_400, lightingVisibilityIssue(true, restored), restored))
    }

    @Test fun `relaxed taxi-like brightness admits a dim face after initial confirmation`() {
        for ((luminance, contrast) in listOf(18.0 to 30.0, 37.0 to 15.0, 15.0 to 20.0, 35.0 to 0.0)) {
            val tracker = FaceLightingTracker()
            for (time in 0L..1_800L step 200) {
                assertFalse(tracker.update(time, sample(time, luminance, contrast)).allowsAssessment(time))
            }
            assertTrue(tracker.update(2_000, sample(2_000, luminance, contrast)).allowsAssessment(2_000))
        }
    }

    @Test fun `brief dark dip pauses without speech then a new good sample resumes immediately`() {
        val tracker = readyTracker()
        val controller = VisibilityAlertController()
        for (time in 2_200L..4_000L step 200) {
            val lighting = tracker.update(time, sample(time, 10.0, 0.0))
            assertEquals(LightingQuality.UNKNOWN, lighting.quality)
            assertFalse(lighting.allowsAssessment(time))
            assertTrue(controller.update(time, lightingVisibilityIssue(true, lighting), lighting).isEmpty())
        }
        val good = tracker.update(4_200, sample(4_200))
        assertTrue(good.allowsAssessment(4_200))
        assertNull(good.poorSinceMs)
        assertTrue(controller.update(4_200, lightingVisibilityIssue(true, good), good).isEmpty())
    }

    @Test fun `unconfirmed dark startup cannot use the previously usable quick return`() {
        val tracker = FaceLightingTracker()
        for (time in 0L..1_800L step 200) tracker.update(time, sample(time, 10.0, 0.0))
        for (time in 2_000L..3_800L step 200) assertFalse(tracker.update(time, sample(time)).allowsAssessment(time))
        assertTrue(tracker.update(4_000, sample(4_000)).allowsAssessment(4_000))
    }

    @Test fun `missing stale and future samples break poor confirmation`() {
        for (invalid in listOf(null, sample(800, 10.0, 0.0), sample(2_200, 10.0, 0.0))) {
            val tracker = readyTracker()
            for (time in 2_200L..4_000L step 200) tracker.update(time, sample(time, 10.0, 0.0))
            // The supplied timestamps are stale here; the future case is checked below.
            assertEquals(LightingQuality.UNKNOWN, tracker.update(4_200, invalid).quality)
            for (time in 4_400L..6_200L step 200) {
                assertEquals(LightingQuality.UNKNOWN, tracker.update(time, sample(time, 10.0, 0.0)).quality)
            }
            assertEquals(LightingQuality.INSUFFICIENT, tracker.update(6_400, sample(6_400, 10.0, 0.0)).quality)
        }
        val tracker = readyTracker()
        tracker.update(2_200, sample(2_200, 10.0, 0.0))
        assertEquals(LightingQuality.UNKNOWN, tracker.update(2_400, sample(2_600, 10.0, 0.0)).quality)
        assertFalse(tracker.update(2_600, sample(2_600)).allowsAssessment(2_600))
    }

    @Test fun `sample gaps over freshness do not bridge a pending dark interval`() {
        val tracker = readyTracker()
        tracker.update(2_200, sample(2_200, 10.0, 0.0))
        tracker.update(3_000, sample(3_000, 10.0, 0.0))
        val afterGap = tracker.update(4_200, sample(4_200, 10.0, 0.0))
        assertEquals(LightingQuality.UNKNOWN, afterGap.quality)
        assertEquals(4_200L, afterGap.poorSinceMs)
        assertFalse(tracker.update(4_400, sample(4_400)).allowsAssessment(4_400))
    }

    @Test fun `duplicate and out of order samples cannot advance poor confirmation`() {
        val tracker = readyTracker()
        tracker.update(2_200, sample(2_200, 10.0, 0.0))
        tracker.update(3_000, sample(3_000, 10.0, 0.0))
        assertEquals(3_000L, tracker.update(3_100, sample(2_800, 10.0, 0.0)).timestampMs)
        assertEquals(LightingQuality.UNKNOWN, tracker.update(3_900, sample(3_000, 10.0, 0.0)).quality)
        assertEquals(LightingQuality.UNKNOWN, tracker.update(4_000, sample(4_000, 10.0, 0.0)).quality)
        assertEquals(LightingQuality.INSUFFICIENT, tracker.update(4_200, sample(4_200, 10.0, 0.0)).quality)
    }

    @Test fun `good sample just before warning cancels it and separated darkness must qualify again`() {
        val tracker = readyTracker()
        val controller = VisibilityAlertController()
        for (time in 2_200L..7_000L step 200) {
            val lighting = tracker.update(time, sample(time, 10.0, 0.0))
            assertTrue(controller.update(time, lightingVisibilityIssue(true, lighting), lighting).isEmpty())
        }
        val good = tracker.update(7_100, sample(7_100))
        assertTrue(good.recovering)
        assertTrue(controller.update(7_100, lightingVisibilityIssue(true, good), good).isEmpty())
        for (time in 7_200L..12_000L step 200) {
            val lighting = tracker.update(time, sample(time, 10.0, 0.0))
            assertTrue(controller.update(time, lightingVisibilityIssue(true, lighting), lighting).isEmpty())
        }
        val poor = tracker.update(12_200, sample(12_200, 10.0, 0.0))
        assertEquals(listOf(VisibilityAlertEffect.Notify(VisibilityIssue.INSUFFICIENT_LIGHTING)),
            controller.update(12_200, lightingVisibilityIssue(true, poor), poor))
    }

    @Test fun `new darkness during reported recovery cannot resume old announcement timing`() {
        val tracker = readyTracker()
        val controller = VisibilityAlertController()
        for (time in 2_200L..7_200L step 200) {
            val lighting = tracker.update(time, sample(time, 10.0, 0.0))
            controller.update(time, lightingVisibilityIssue(true, lighting), lighting)
        }
        val good = tracker.update(7_400, sample(7_400))
        controller.update(7_400, null, good)
        for (time in 7_600L..12_400L step 200) {
            val lighting = tracker.update(time, sample(time, 10.0, 0.0))
            assertTrue(controller.update(time, lightingVisibilityIssue(true, lighting), lighting).isEmpty())
        }
        val poor = tracker.update(12_600, sample(12_600, 10.0, 0.0))
        assertEquals(listOf(VisibilityAlertEffect.Notify(VisibilityIssue.INSUFFICIENT_LIGHTING, announce = false)),
            controller.update(12_600, lightingVisibilityIssue(true, poor), poor))
    }

    @Test fun `lighting controller waits for new sample evidence instead of current frame time`() {
        val controller = VisibilityAlertController()
        fun poor(time: Long) = FaceLightingAssessment(time, LightingQuality.INSUFFICIENT, 10.0, 0.0, poorSinceMs = 0)
        controller.update(2_000, VisibilityIssue.INSUFFICIENT_LIGHTING, poor(2_000))
        assertTrue(controller.update(5_000, VisibilityIssue.INSUFFICIENT_LIGHTING, poor(4_800)).isEmpty())
        assertEquals(listOf(VisibilityAlertEffect.Notify(VisibilityIssue.INSUFFICIENT_LIGHTING)),
            controller.update(5_200, VisibilityIssue.INSUFFICIENT_LIGHTING, poor(5_000)))
    }

    @Test fun `face loss then poor selected face has independent lighting qualification`() {
        val tracker = FaceLightingTracker()
        val controller = VisibilityAlertController()
        controller.update(0, VisibilityIssue.FACE_UNAVAILABLE)
        controller.update(3_000, VisibilityIssue.FACE_UNAVAILABLE)
        for (time in 3_200L..8_000L step 200) {
            val lighting = tracker.update(time, sample(time, 10.0, 0.0))
            assertTrue(controller.update(time, lightingVisibilityIssue(true, lighting), lighting).isEmpty())
        }
        val poor = tracker.update(8_200, sample(8_200, 10.0, 0.0))
        assertEquals(listOf(VisibilityAlertEffect.Notify(VisibilityIssue.INSUFFICIENT_LIGHTING, announce = false)),
            controller.update(8_200, lightingVisibilityIssue(true, poor), poor))
    }

    @Test fun `lighting speech expires on good light a missing face or a different dark interval`() {
        val tracking = com.lhordkent.drivealert.detection.face.DriverTrackingState(
            status = com.lhordkent.drivealert.detection.face.DriverTrackingStatus.LOCKED, detectedFaceCount = 1)
        val state = com.lhordkent.drivealert.detection.DriverVisionUiState(
            streamState = com.lhordkent.drivealert.detection.frame.StreamConnectionState.CONNECTED,
            alignmentConfirmed = true, faceDetected = true, driverTracking = tracking,
            monitoring = ActiveMonitoringState(isActive = true),
            lighting = FaceLightingAssessment(5_000, LightingQuality.INSUFFICIENT, 10.0, 0.0, poorSinceMs = 0))
        assertTrue(state.insufficientLighting)
        assertFalse(state.copy(faceDetected = false).insufficientLighting)
        assertTrue(state.lightingSpeechAllowed(5_000, 0))
        assertFalse(state.lightingSpeechAllowed(6_001, 0))
        assertFalse(state.lightingSpeechAllowed(4_999, 0))
        assertFalse(state.copy(faceDetected = false).lightingSpeechAllowed(5_000, 0))
        assertFalse(state.copy(alignmentConfirmed = false).lightingSpeechAllowed(5_000, 0))
        assertFalse(state.copy(monitoring = ActiveMonitoringState(isActive = false)).lightingSpeechAllowed(5_000, 0))
        assertFalse(state.copy(streamState = com.lhordkent.drivealert.detection.frame.StreamConnectionState.RECONNECTING)
            .lightingSpeechAllowed(5_000, 0))
        assertFalse(state.copy(driverTracking = tracking.copy(status = com.lhordkent.drivealert.detection.face.DriverTrackingStatus.LOST))
            .lightingSpeechAllowed(5_000, 0))
        assertFalse(state.copy(lighting = state.lighting.copy(recovering = true, poorSinceMs = null))
            .lightingSpeechAllowed(5_000, 0))
        assertFalse(state.copy(lighting = state.lighting.copy(poorSinceMs = 3_000))
            .lightingSpeechAllowed(5_000, 0))
        assertFalse(state.lightingSpeechAllowed(5_000, null))
    }

    @Test fun `reset discards both established usability and pending darkness`() {
        val tracker = readyTracker()
        tracker.update(2_200, sample(2_200, 10.0, 0.0))
        tracker.reset()
        val first = tracker.update(2_400, sample(2_400))
        assertFalse(first.allowsAssessment(2_400))
        assertNull(first.poorSinceMs)
    }

    private fun readyTracker() = FaceLightingTracker().apply {
        for (time in 0L..2_000L step 200L) update(time, sample(time))
    }

    private fun sample(time: Long, luminance: Double = 100.0, contrast: Double = 60.0) =
        FaceLightingSample(time, luminance, contrast)
}
