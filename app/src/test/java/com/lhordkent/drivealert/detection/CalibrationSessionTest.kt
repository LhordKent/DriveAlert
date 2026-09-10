package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.detection.calibration.CalibrationSession
import com.lhordkent.drivealert.detection.model.CalibrationPhase
import com.lhordkent.drivealert.detection.model.CalibrationStatus
import com.lhordkent.drivealert.detection.model.FacialMeasurements
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationSessionTest {
    @Test
    fun `calibration derives stable prototype thresholds from correct guided phases`() {
        val session = CalibrationSession(requiredDurationMs = 100)
        session.start()
        collectPhase(session, 0, FacialMeasurements(0.24, 0.10, 10.0))
        assertEquals(CalibrationPhase.EYES_CLOSED, session.phase)
        collectPhase(session, 200, FacialMeasurements(0.10, null, null))
        collectPhase(session, 400, FacialMeasurements(null, 1.20, null))
        collectPhase(session, 600, FacialMeasurements(null, null, 30.0))

        assertEquals(CalibrationStatus.COMPLETE, session.status)
        val result = session.buildResult(700)
        assertEquals(0.18, result.earThreshold, 1e-9)
        assertEquals(0.60, result.marThreshold, 1e-9)
        assertEquals(10.0, result.neutralHeadPitchDegrees, 1e-9)
        assertEquals(1.0, result.downwardPitchMultiplier, 0.0)
    }

    @Test
    fun `invalid observations break continuity without becoming zero samples`() {
        val session = CalibrationSession(requiredDurationMs = 100)
        session.start()
        session.beginPhase()
        session.update(0, FacialMeasurements(0.4, 0.1, 10.0))
        session.update(50, FacialMeasurements(null, null, null))
        session.update(100, FacialMeasurements(0.4, 0.1, 10.0))
        assertEquals(0, session.progress().validDurationMs)
        session.update(200, FacialMeasurements(0.4, 0.1, 10.0))
        assertEquals(CalibrationPhase.EYES_CLOSED, session.phase)
    }

    @Test
    fun `guided head direction makes calibrated downward pitch positive`() {
        val session = CalibrationSession(requiredDurationMs = 100)
        session.start()
        collectPhase(session, 0, FacialMeasurements(0.4, 0.1, 10.0))
        collectPhase(session, 200, FacialMeasurements(0.1, null, null))
        collectPhase(session, 400, FacialMeasurements(null, 0.8, null))
        collectPhase(session, 600, FacialMeasurements(null, null, -10.0))
        assertEquals(-1.0, session.buildResult(700).downwardPitchMultiplier, 0.0)
    }

    private fun collectPhase(session: CalibrationSession, startMs: Long, value: FacialMeasurements) {
        session.beginPhase()
        session.update(startMs, value)
        session.update(startMs + 100, value)
    }
}
