package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.detection.model.SignType
import com.lhordkent.drivealert.detection.model.TemporalState
import com.lhordkent.drivealert.detection.temporal.EyeClosureDetector
import com.lhordkent.drivealert.detection.temporal.HeadDownDetector
import com.lhordkent.drivealert.detection.temporal.YawnDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TemporalDetectorsTest {
    @Test
    fun `eye closure confirms at two seconds once and rearms after recovery`() {
        val detector = EyeClosureDetector(threshold = 0.30)
        assertNull(detector.update(0, 0.20))
        assertNull(detector.update(1_999, 0.20))
        assertEquals(SignType.PROLONGED_EYE_CLOSURE, detector.update(2_000, 0.20)?.type)
        assertNull(detector.update(2_500, 0.20))
        detector.update(2_600, 0.30)
        detector.update(3_000, 0.20)
        assertNotNull(detector.update(5_000, 0.20))
    }

    @Test
    fun `invalid EAR breaks a candidate sequence`() {
        val detector = EyeClosureDetector(threshold = 0.30)
        detector.update(0, 0.20)
        detector.update(1_000, null)
        detector.update(2_000, 0.20)
        assertNull(detector.update(3_999, 0.20))
        assertNotNull(detector.update(4_000, 0.20))
    }

    @Test
    fun `yawn uses strict MAR comparison and three seconds`() {
        val detector = YawnDetector(threshold = 0.40)
        detector.update(0, 0.40)
        assertEquals(TemporalState.NORMAL, detector.state)
        detector.update(100, 0.41)
        assertNull(detector.update(3_099, 0.41))
        assertNotNull(detector.update(3_100, 0.41))
    }

    @Test
    fun `head down boundary is strictly greater than fifteen degrees`() {
        val below = HeadDownDetector().also { it.update(0, 14.99) }
        val equal = HeadDownDetector().also { it.update(0, 15.00) }
        val above = HeadDownDetector().also { it.update(0, 15.01) }
        assertEquals(0.0, below.downwardRatio!!, 0.0)
        assertEquals(0.0, equal.downwardRatio!!, 0.0)
        assertEquals(1.0, above.downwardRatio!!, 0.0)
    }

    @Test
    fun `head confirms at eighty percent of valid observations over full window`() {
        val detector = HeadDownDetector()
        detector.update(0, 16.0)
        detector.update(750, 16.0)
        detector.update(1_500, 16.0)
        detector.update(2_250, 16.0)
        val event = detector.update(3_000, 10.0)
        assertNotNull(event)
        assertEquals(SignType.HEAD_NODDING, event?.type)
        assertEquals(0.80, detector.downwardRatio!!, 1e-9)
    }

    @Test
    fun `head does not confirm below eighty percent`() {
        val detector = HeadDownDetector()
        detector.update(0, 16.0)
        detector.update(750, 16.0)
        detector.update(1_500, 16.0)
        detector.update(2_250, 10.0)
        assertNull(detector.update(3_000, 10.0))
        assertEquals(0.60, detector.downwardRatio!!, 1e-9)
    }

    @Test
    fun `invalid head observations are excluded from rolling ratio`() {
        val detector = HeadDownDetector()
        detector.update(0, 16.0)
        detector.update(1_000, null)
        detector.update(2_000, 16.0)
        assertNotNull(detector.update(3_000, 16.0))
        assertEquals(1.0, detector.downwardRatio!!, 0.0)
    }
}
