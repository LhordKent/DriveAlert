package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.detection.calibration.CalibrationSession
import com.lhordkent.drivealert.detection.calibration.completedCalibrationStep
import com.lhordkent.drivealert.detection.model.*
import org.junit.Assert.*
import org.junit.Test

class CalibrationCompletionCueTest {
    @Test fun `countdown matches the 2500 ms recording and displays three two one`() {
        val countdown = com.lhordkent.drivealert.detection.calibration.CalibrationCountdown
        assertEquals(listOf(3, 2, 1), countdown.steps.map { it.first })
        assertEquals(2_500L, countdown.steps.sumOf { it.second })
        assertEquals(countdown.DURATION_MS, countdown.steps.sumOf { it.second })
    }
    @Test fun `all four measured steps emit exactly one completion edge`() {
        val session = CalibrationSession(100)
        session.start()
        val samples = listOf(FacialMeasurements(.24, .1, 10.0), FacialMeasurements(.1, null, null),
            FacialMeasurements(null, 1.2, null), FacialMeasurements(null, null, 30.0))
        samples.forEachIndexed { index, sample ->
            val ready = session.progress()
            session.beginPhase()
            assertFalse(completedCalibrationStep(ready, session.progress()))
            session.update(index * 200L, sample)
            val before = session.progress()
            assertFalse(completedCalibrationStep(before, before))
            session.update(index * 200L + 100, sample)
            val after = session.progress()
            assertTrue("step $index", completedCalibrationStep(before, after))
            assertFalse(completedCalibrationStep(after, after))
        }
        assertEquals(CalibrationStatus.COMPLETE, session.status)
    }

    @Test fun `invalid visibility paused collection navigation and restart never complete a step`() {
        val session = CalibrationSession(100)
        session.start(); session.beginPhase()
        session.update(0, FacialMeasurements(.24, .1, 10.0))
        val before = session.progress()
        session.update(500, FacialMeasurements(null, null, null))
        assertFalse(completedCalibrationStep(before, session.progress()))
        session.start()
        assertFalse(completedCalibrationStep(before, session.progress()))
        assertFalse(completedCalibrationStep(null, session.progress()))
        assertFalse(completedCalibrationStep(before, before.copy(phase = CalibrationPhase.EYES_CLOSED, status = CalibrationStatus.READY, revision = before.revision + 1)))
        assertFalse(completedCalibrationStep(before.copy(phase = CalibrationPhase.MOUTH_OPEN), before.copy(phase = CalibrationPhase.EYES_CLOSED, status = CalibrationStatus.READY)))
    }
}
