package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.detection.model.FaceObservation
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import org.junit.Assert.*
import org.junit.Test

class DriverMovementTrackerTest {
    private fun face(t: Long, shift: Double = 0.0, eyeShift: Double = 0.0, mouthShift: Double = 0.0) = FaceObservation(
        t, 640, 480, List(468) { index ->
            // Fixed extremes give a 320-pixel face width. Shift is a fraction of image width.
            val local = when (index) {
                in listOf(362, 385, 387, 263, 373, 380) -> eyeShift
                in listOf(78, 308, 81, 178, 13, 14, 311, 402) -> mouthShift
                else -> 0.0
            }
            NormalizedLandmark((if (index == 0) 0.25 else if (index == 1) 0.75 else 0.5) + shift, 0.5 + local)
        },
    )

    private fun feed(tracker: DriverMovementTracker, from: Long = 0, until: Long = 25_000) {
        for (t in from..until step 200) tracker.update(face(t))
    }

    @Test fun `status changes at ten seconds and speech at twenty five total`() {
        val tracker = DriverMovementTracker()
        feed(tracker, until = 9_800)
        assertEquals(DriverMovementStatus.OBSERVING, tracker.assessment.status)
        tracker.update(face(9_999))
        assertEquals(DriverMovementStatus.OBSERVING, tracker.assessment.status)
        tracker.update(face(10_000))
        assertEquals(DriverMovementStatus.NO_MOVEMENT, tracker.assessment.status)
        assertNull(tracker.claimSpeech(false, false, false))
        feed(tracker, 10_200, 24_800)
        tracker.update(face(24_999))
        assertNull(tracker.claimSpeech(false, false, false))
        tracker.update(face(25_000))
        assertNotNull(tracker.claimSpeech(false, false, false))
        assertNull(tracker.claimSpeech(false, false, false))
    }

    @Test fun `tiny jitter and a single bad landmark do not refresh inactivity`() {
        val tracker = DriverMovementTracker()
        feed(tracker, until = 400)
        for (t in 600L..10_000L step 200) {
            val base = face(t, shift = if (t % 400 == 0L) 0.002 else -0.002)
            val points = base.landmarks.toMutableList()
            points[385] = NormalizedLandmark(0.9, 0.9)
            tracker.update(base.copy(landmarks = points))
        }
        assertEquals(10_000L, tracker.assessment.inactivityMs)
        assertEquals(DriverMovementStatus.NO_MOVEMENT, tracker.assessment.status)
    }

    @Test fun `single frame jump and faster than sampled changes do not qualify`() {
        val tracker = DriverMovementTracker()
        feed(tracker, until = 400)
        tracker.update(face(600, 0.1))
        tracker.update(face(700, 0.1))
        tracker.update(face(800))
        assertEquals(DriverMovementStatus.OBSERVING, tracker.assessment.status)
        assertEquals(800L, tracker.assessment.inactivityMs)
    }

    @Test fun `translation and local eye or mouth motion all qualify`() {
        for (kind in 0..2) {
            val tracker = DriverMovementTracker()
            feed(tracker, until = 400)
            for (t in listOf(600L, 800L)) tracker.update(when (kind) {
                0 -> face(t, shift = 0.03)
                1 -> face(t, eyeShift = 0.03)
                else -> face(t, mouthShift = 0.03)
            })
            assertEquals(DriverMovementStatus.RECENT_MOVEMENT, tracker.assessment.status)
            assertEquals(0L, tracker.assessment.inactivityMs)
        }
    }

    @Test fun `gradual movement accumulates against retained reference`() {
        val tracker = DriverMovementTracker()
        feed(tracker, until = 400)
        for (i in 1..8) tracker.update(face(400L + i * 200, shift = i * 0.002))
        assertEquals(DriverMovementStatus.RECENT_MOVEMENT, tracker.assessment.status)
    }

    @Test fun `rotation and scale changes are deliberately retained as movement`() {
        val eye = setOf(362, 385, 387, 263, 373, 380)
        fun shaped(t: Long, rotation: Boolean, changed: Boolean): FaceObservation {
            val source = face(t).landmarks.mapIndexed { index, point ->
                if (index in eye) NormalizedLandmark(0.4, 0.35) else point
            }
            val points = if (!changed) source else source.map {
                val x = it.x - 0.5
                val y = it.y - 0.5
                if (rotation) NormalizedLandmark(0.5 + x * 0.94 - y * 0.342, 0.5 + x * 0.342 + y * 0.94)
                else NormalizedLandmark(0.5 + x * 1.3, 0.5 + y * 1.3)
            }
            return face(t).copy(landmarks = points)
        }
        for (rotation in listOf(true, false)) {
            val tracker = DriverMovementTracker()
            for (t in listOf(0L, 200L, 400L)) tracker.update(shaped(t, rotation, false))
            tracker.update(shaped(600, rotation, true))
            tracker.update(shaped(800, rotation, true))
            assertEquals(DriverMovementStatus.RECENT_MOVEMENT, tracker.assessment.status)
        }
    }

    @Test fun `speech waits for priority output and active signs then attempts once even if failed`() {
        val tracker = DriverMovementTracker()
        feed(tracker)
        assertNull(tracker.claimSpeech(true, false, false))
        assertNull(tracker.claimSpeech(false, true, false))
        assertNull(tracker.claimSpeech(false, false, true))
        val token = tracker.claimSpeech(false, false, false)!!
        tracker.completeSpeech(token, WarningDeliveryStatus.FAILED)
        feed(tracker, 25_200, 30_000)
        assertEquals(WarningDeliveryStatus.FAILED, tracker.assessment.speechDelivery)
        assertNull(tracker.claimSpeech(false, false, false))
    }

    @Test fun `movement clears warning and rearms speech for next episode`() {
        val tracker = DriverMovementTracker()
        feed(tracker)
        val first = tracker.claimSpeech(false, false, false)!!
        tracker.update(face(25_200, 0.03))
        tracker.update(face(25_400, 0.03))
        tracker.completeSpeech(first, WarningDeliveryStatus.DELIVERED)
        assertEquals(WarningDeliveryStatus.NOT_REQUESTED, tracker.assessment.speechDelivery)
        assertEquals(DriverMovementStatus.RECENT_MOVEMENT, tracker.assessment.status)
        for (t in 25_600L..50_400L step 200) tracker.update(face(t, 0.03))
        assertNotNull(tracker.claimSpeech(false, false, false))
    }

    @Test fun `missing invalid stale and explicit resets clear evidence and ignore late delivery`() {
        for (reason in 0..4) {
            val tracker = DriverMovementTracker()
            feed(tracker)
            val token = tracker.claimSpeech(false, false, false)!!
            when (reason) {
                0 -> tracker.update(null)
                1 -> tracker.update(face(25_100).copy(landmarks = emptyList()))
                2 -> tracker.update(face(27_000))
                3 -> tracker.reset()
                else -> tracker.update(face(25_200).copy(landmarks = List(468) { NormalizedLandmark(Double.NaN, 0.0) }))
            }
            tracker.completeSpeech(token, WarningDeliveryStatus.DELIVERED)
            tracker.update(face(27_200))
            assertEquals(DriverMovementStatus.OBSERVING, tracker.assessment.status)
            assertTrue(tracker.assessment.inactivityMs < 1_000)
            assertEquals(WarningDeliveryStatus.NOT_REQUESTED, tracker.assessment.speechDelivery)
            assertNull(tracker.claimSpeech(false, false, false))
        }
    }
}
