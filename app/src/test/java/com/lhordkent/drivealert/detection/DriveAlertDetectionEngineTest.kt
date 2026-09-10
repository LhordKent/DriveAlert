package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.detection.model.FaceObservation
import com.lhordkent.drivealert.detection.model.SignType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DriveAlertDetectionEngineTest {
    @Test
    fun persistedCalibrationCanBeActivatedWithoutCollection() {
        val engine = DriveAlertDetectionEngine()
        val calibration = com.lhordkent.drivealert.detection.model.CalibrationResult(
            calibrationId = "stored",
            calibratedAtTimestampMs = 0,
            neutralEar = 0.25,
            closedEyeEar = 0.05,
            earThreshold = 0.18,
            neutralMar = 0.02,
            openMouthMar = 1.0,
            marThreshold = 0.5,
            neutralHeadPitchDegrees = 4.0,
            downwardPitchMultiplier = 1.0,
        )

        engine.activatePersistedCalibration(calibration)

        assertEquals("stored", engine.calibrationResult()?.calibrationId)
    }

    @Test
    fun `end to end calibration and eye event match prototype timings`() {
        val engine = DriveAlertDetectionEngine(calibrationDurationMs = 100)
        engine.startCalibration()
        collect(engine, 0, ear = 0.40, mar = 0.10, pitch = 10.0)
        collect(engine, 200, ear = 0.10, mar = 0.10, pitch = 10.0)
        collect(engine, 400, ear = 0.40, mar = 0.80, pitch = 10.0)
        collect(engine, 600, ear = 0.40, mar = 0.10, pitch = 30.0)

        val calibration = engine.calibrationResult()
        assertNotNull(calibration)
        assertEquals(0.30, calibration!!.earThreshold, 1e-9)
        assertEquals(0.40, calibration.marThreshold, 1e-9)

        engine.process(face(800, 0.20, 0.10, 10.0))
        val result = engine.process(face(2_800, 0.20, 0.10, 10.0))
        val event = result.events.single { it.type == SignType.PROLONGED_EYE_CLOSURE }
        assertEquals(calibration.calibrationId, event.calibrationId)
        assertEquals(calibration.schemaVersion, event.calibrationVersion)
    }

    @Test
    fun `face loss breaks calibration timing continuity`() {
        val engine = DriveAlertDetectionEngine(calibrationDurationMs = 100)
        engine.startCalibration()
        engine.beginCalibrationPhase()
        engine.process(face(0, 0.40, 0.10, 10.0))
        engine.process(50, null)
        val afterGap = engine.process(face(100, 0.40, 0.10, 10.0))
        assertEquals(0, afterGap.calibration.validDurationMs)
        val completed = engine.process(face(200, 0.40, 0.10, 10.0))
        assertEquals("EYES_CLOSED", completed.calibration.phase.name)
    }

    private fun collect(
        engine: DriveAlertDetectionEngine,
        startMs: Long,
        ear: Double,
        mar: Double,
        pitch: Double,
    ) {
        engine.beginCalibrationPhase()
        engine.process(face(startMs, ear, mar, pitch))
        engine.process(face(startMs + 100, ear, mar, pitch))
    }

    private fun face(timestampMs: Long, ear: Double, mar: Double, pitch: Double) = FaceObservation(
        timestampMs = timestampMs,
        frameWidth = 100,
        frameHeight = 100,
        landmarks = FacialMeasurementCalculatorTest.landmarksWithRatios(ear, mar),
        facialTransformationMatrix = FacialMeasurementCalculatorTest.pitchMatrix(pitch),
    )
}
