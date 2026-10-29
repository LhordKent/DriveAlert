package com.lhordkent.drivealert.detection.face

import com.lhordkent.drivealert.detection.model.FaceObservation
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import org.junit.Assert.*
import org.junit.Test

class DriverFaceSelectorTest {
    private val region = DriverRegion(0.1, 0.05, 0.6, 0.95)
    private fun face(t: Long, x: Double = 0.3, y: Double = 0.4, size: Double = 0.2) = FaceObservation(
        t, 640, 480, listOf(NormalizedLandmark(x - size / 2, y - size / 2),
            NormalizedLandmark(x + size / 2, y - size / 2), NormalizedLandmark(x, y + size / 2)),
        floatArrayOf(x.toFloat()),
    )
    private fun select(s: DriverFaceSelector, t: Long, vararg faces: FaceObservation) = s.select(t, faces.toList(), region, 640, 480)
    private fun lock(s: DriverFaceSelector): DriverSelection {
        var result = select(s, 0, face(0))
        for (t in 100L..1_000L step 100) result = select(s, t, face(t))
        assertTrue(result.tracking.locked)
        return result
    }

    @Test fun `waits for stability and retains driver when larger passenger and result order change`() {
        val s = DriverFaceSelector()
        assertNull(select(s, 0, face(0)).observation)
        for (t in 100L..1_000L step 100) select(s, t, face(t))
        val driver = face(1_100)
        val passenger = face(1_100, 0.82, size = 0.3)
        assertSame(driver, select(s, 1_100, passenger, driver).observation)
        val next = face(1_200, 0.31)
        assertSame(next, select(s, 1_200, next, face(1_200, 0.82)).observation)
        assertEquals(0.31f, next.facialTransformationMatrix!![0])
    }

    @Test fun `overlap outside region and multiple eligible faces pause immediately`() {
        val s = DriverFaceSelector()
        val old = lock(s)
        val ambiguous = select(s, 1_100, face(1_100), face(1_100, 0.45))
        assertEquals(DriverTrackingStatus.AMBIGUOUS, ambiguous.tracking.status)
        assertTrue(ambiguous.tracking.generation > old.tracking.generation)
        assertNull(ambiguous.observation)
        val nearBoundary = DriverFaceSelector()
        val overlapping = nearBoundary.select(0, listOf(face(0, 0.52), face(0, 0.62)), region, 640, 480)
        assertEquals(DriverTrackingStatus.AMBIGUOUS, overlapping.tracking.status)
    }

    @Test fun `passenger outside region cannot replace missing driver`() {
        val s = DriverFaceSelector()
        lock(s)
        for (t in 1_100L..3_000L step 100) {
            val result = select(s, t, face(t, 0.8))
            assertEquals(DriverTrackingStatus.LOST, result.tracking.status)
            assertNull(result.observation)
        }
        assertFalse(select(s, 3_100, face(3_100)).tracking.locked)
        for (t in 3_200L..4_000L step 100) assertFalse(select(s, t, face(t)).tracking.locked)
        assertTrue(select(s, 4_100, face(4_100)).tracking.locked)
    }

    @Test fun `jump and frame gap cannot transfer a lock`() {
        val s = DriverFaceSelector()
        lock(s)
        assertEquals(DriverTrackingStatus.LOST, select(s, 1_100, face(1_100, 0.49)).tracking.status)
        assertFalse(select(s, 1_200, face(1_200, 0.49)).tracking.locked)
        val other = DriverFaceSelector()
        lock(other)
        assertFalse(select(other, 2_000, face(2_000)).tracking.locked)
    }

    @Test fun `small head movements remain tracked`() {
        val s = DriverFaceSelector()
        lock(s)
        for (i in 1..8) assertTrue(select(s, 1_000 + i * 100L, face(1_000 + i * 100L, 0.3 + i * 0.005, 0.4 + i * 0.015)).tracking.locked)
    }

    @Test fun `capacity saturation malformed detections and missing configuration fail closed`() {
        val s = DriverFaceSelector()
        assertEquals(DriverTrackingStatus.AMBIGUOUS, select(s, 0, face(0), face(0, 0.7), face(0, 0.8), face(0, 0.9)).tracking.status)
        assertEquals(DriverTrackingStatus.AMBIGUOUS, select(s, 100, face(100).copy(landmarks = emptyList())).tracking.status)
        assertEquals(DriverTrackingStatus.UNCONFIGURED, s.select(200, listOf(face(200)), null, 640, 480).tracking.status)
    }

    @Test fun `dimension region and explicit resets require fresh acquisition`() {
        val s = DriverFaceSelector()
        val first = lock(s)
        val changed = s.select(1_100, listOf(face(1_100)), region, 480, 640)
        assertFalse(changed.tracking.locked)
        assertTrue(changed.tracking.generation > first.tracking.generation)
        s.reset()
        assertFalse(select(s, 1_200, face(1_200)).tracking.locked)
        assertFalse(s.select(1_300, listOf(face(1_300)), DriverRegion.DEFAULT, 640, 480).tracking.locked)
    }
}
