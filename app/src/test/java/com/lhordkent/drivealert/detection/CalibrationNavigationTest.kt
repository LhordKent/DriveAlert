package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.detection.calibration.CalibrationSession
import com.lhordkent.drivealert.detection.model.CalibrationPhase
import com.lhordkent.drivealert.detection.model.CalibrationStatus
import com.lhordkent.drivealert.detection.model.FacialMeasurements
import org.junit.Assert.*
import org.junit.Test

class CalibrationNavigationTest {
    private fun collect(session: CalibrationSession, time: Long, ear: Double, mar: Double, pitch: Double) {
        session.beginPhase()
        session.update(time, FacialMeasurements(ear, mar, pitch))
        session.update(time + 100, FacialMeasurements(ear, mar, pitch))
    }

    @Test fun `going back clears the previous and later steps and replaces their measurements`() {
        val session = CalibrationSession(100)
        session.start()
        collect(session, 0, 0.4, 0.1, 4.0)
        collect(session, 200, 0.1, 0.1, 4.0)
        session.beginPhase()
        session.update(400, FacialMeasurements(0.4, 0.9, 4.0))
        val oldRevision = session.progress().revision
        session.previousPhase()
        assertEquals(CalibrationPhase.EYES_CLOSED, session.phase)
        assertEquals(CalibrationStatus.READY, session.status)
        assertEquals(0, session.progress().validSampleCount)
        assertEquals(0L, session.progress().validDurationMs)
        assertTrue(session.progress().revision > oldRevision)
        collect(session, 600, 0.2, 0.1, 4.0)
        collect(session, 800, 0.4, 0.8, 4.0)
        collect(session, 1000, 0.4, 0.1, 24.0)
        val result = session.buildResult(1100)
        assertEquals(0.4, result.neutralEar, 1e-9)
        assertEquals(0.2, result.closedEyeEar, 1e-9)
        assertEquals(0.8, result.openMouthMar, 1e-9)
        assertEquals(0.4, result.marThreshold, 1e-9)
    }

    @Test fun `returning to normal posture replaces the head reference too`() {
        val session = CalibrationSession(100)
        session.start()
        collect(session, 0, 0.4, 0.1, 4.0)
        session.previousPhase()
        assertEquals(CalibrationPhase.NEUTRAL, session.phase)
        collect(session, 200, 0.3, 0.1, 20.0)
        collect(session, 400, 0.1, 0.1, 20.0)
        collect(session, 600, 0.3, 0.8, 20.0)
        collect(session, 800, 0.3, 0.1, 10.0)
        val result = session.buildResult(900)
        assertEquals(20.0, result.neutralHeadPitchDegrees, 1e-9)
        assertEquals(-1.0, result.downwardPitchMultiplier, 1e-9)
    }

    @Test fun `redo all resets progress and waits for explicit phase start`() {
        val session = CalibrationSession(100)
        session.start()
        collect(session, 0, 0.4, 0.1, 4.0)
        val revision = session.progress().revision
        session.start()
        assertEquals(CalibrationPhase.NEUTRAL, session.phase)
        assertEquals(CalibrationStatus.READY, session.status)
        assertTrue(session.progress().revision > revision)
        assertFalse(session.update(1000, FacialMeasurements(0.3, 0.1, 0.0)))
        assertEquals(0L, session.progress().validDurationMs)
        assertEquals(0, session.progress().validSampleCount)
        session.previousPhase()
        assertEquals(CalibrationPhase.NEUTRAL, session.phase)
    }

    @Test fun `head down retry renews acknowledgment when no pitch change was measured`() {
        val session = CalibrationSession(100)
        session.start()
        collect(session, 0, 0.4, 0.1, 4.0)
        collect(session, 200, 0.1, 0.1, 4.0)
        collect(session, 400, 0.4, 0.8, 4.0)
        val revision = session.progress().revision
        collect(session, 600, 0.4, 0.1, 4.0)
        assertEquals(CalibrationPhase.HEAD_DOWN, session.phase)
        assertEquals(CalibrationStatus.READY, session.status)
        assertTrue(session.progress().revision > revision)
        assertEquals(0L, session.progress().validDurationMs)
    }
}
