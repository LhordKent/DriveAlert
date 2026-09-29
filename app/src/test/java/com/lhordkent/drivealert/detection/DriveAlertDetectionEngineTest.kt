package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.detection.model.FaceObservation
import com.lhordkent.drivealert.detection.model.RegionalVisibility
import com.lhordkent.drivealert.detection.model.SignType
import com.lhordkent.drivealert.detection.model.TemporalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `lower face obstruction disables only yawning measurements`() {
        val engine = DriveAlertDetectionEngine()
        engine.activatePersistedCalibration(
            com.lhordkent.drivealert.detection.model.CalibrationResult(
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
            ),
        )

        val result = engine.process(
            timestampMs = 100,
            observation = face(100, ear = 0.25, mar = 0.90, pitch = 4.0),
            regionalVisibility = RegionalVisibility(lowerFaceObstructed = true),
        )

        assertEquals(TemporalState.UNAVAILABLE, result.yawn.state)
        assertEquals(null, result.measurements.mar)
        assertEquals(TemporalState.NORMAL, result.eye.state)
        assertEquals(TemporalState.NORMAL, result.head.state)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `sunglasses obstruction disables only eye measurements`() {
        val engine = calibratedEngine()

        val result = engine.process(
            timestampMs = 100,
            observation = face(100, ear = 0.10, mar = 0.10, pitch = 4.0),
            regionalVisibility = RegionalVisibility(eyeRegionObstructed = true),
        )

        assertEquals(TemporalState.UNAVAILABLE, result.eye.state)
        assertEquals(null, result.measurements.ear)
        assertEquals(TemporalState.NORMAL, result.yawn.state)
        assertEquals(TemporalState.NORMAL, result.head.state)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `both obstructed regions pause eye yawn and head measurements`() {
        val engine = calibratedEngine()

        val result = engine.process(
            timestampMs = 100,
            observation = face(100, ear = 0.10, mar = 0.90, pitch = 30.0),
            regionalVisibility = RegionalVisibility(
                lowerFaceObstructed = true,
                eyeRegionObstructed = true,
            ),
        )

        assertEquals(TemporalState.UNAVAILABLE, result.eye.state)
        assertEquals(TemporalState.UNAVAILABLE, result.yawn.state)
        assertEquals(TemporalState.UNAVAILABLE, result.head.state)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `missing head and obstructed mouth pause every sign`() {
        val engine = calibratedEngine()

        val result = engine.process(
            timestampMs = 100,
            observation = face(100, ear = 0.10, mar = 0.90, pitch = 30.0).copy(
                facialTransformationMatrix = null,
            ),
            regionalVisibility = RegionalVisibility(lowerFaceObstructed = true),
        )

        assertEquals(TemporalState.UNAVAILABLE, result.eye.state)
        assertEquals(TemporalState.UNAVAILABLE, result.yawn.state)
        assertEquals(TemporalState.UNAVAILABLE, result.head.state)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `missing head and obstructed eyes pause every sign`() {
        val engine = calibratedEngine()

        val result = engine.process(
            timestampMs = 100,
            observation = face(100, ear = 0.10, mar = 0.90, pitch = 30.0).copy(
                facialTransformationMatrix = null,
            ),
            regionalVisibility = RegionalVisibility(eyeRegionObstructed = true),
        )

        assertEquals(TemporalState.UNAVAILABLE, result.eye.state)
        assertEquals(TemporalState.UNAVAILABLE, result.yawn.state)
        assertEquals(TemporalState.UNAVAILABLE, result.head.state)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `eye closure evidence restarts after insufficient visibility`() {
        val engine = calibratedEngine()

        engine.process(face(0, ear = 0.10, mar = 0.10, pitch = 4.0))
        engine.process(face(1_000, ear = 0.10, mar = 0.10, pitch = 4.0))
        engine.process(
            timestampMs = 1_500,
            observation = face(1_500, ear = 0.10, mar = 0.10, pitch = 4.0),
            regionalVisibility = RegionalVisibility(lowerFaceObstructed = true, eyeRegionObstructed = true),
        )

        assertTrue(engine.process(face(1_600, ear = 0.10, mar = 0.10, pitch = 4.0)).events.isEmpty())
        assertTrue(engine.process(face(3_500, ear = 0.10, mar = 0.10, pitch = 4.0)).events.isEmpty())
        assertEquals(
            SignType.PROLONGED_EYE_CLOSURE,
            engine.process(face(3_600, ear = 0.10, mar = 0.10, pitch = 4.0)).events.single().type,
        )
    }

    @Test
    fun `yawn evidence restarts after insufficient visibility`() {
        val engine = calibratedEngine()

        engine.process(face(0, ear = 0.25, mar = 0.90, pitch = 4.0))
        engine.process(face(1_500, ear = 0.25, mar = 0.90, pitch = 4.0))
        engine.process(
            timestampMs = 2_000,
            observation = face(2_000, ear = 0.25, mar = 0.90, pitch = 4.0),
            regionalVisibility = RegionalVisibility(lowerFaceObstructed = true, eyeRegionObstructed = true),
        )

        assertTrue(engine.process(face(2_100, ear = 0.25, mar = 0.90, pitch = 4.0)).events.isEmpty())
        assertTrue(engine.process(face(5_000, ear = 0.25, mar = 0.90, pitch = 4.0)).events.isEmpty())
        assertEquals(
            SignType.YAWNING,
            engine.process(face(5_100, ear = 0.25, mar = 0.90, pitch = 4.0)).events.single().type,
        )
    }

    @Test
    fun `head pose window restarts after insufficient visibility`() {
        val engine = calibratedEngine()

        engine.process(face(0, ear = 0.25, mar = 0.10, pitch = 30.0))
        engine.process(face(1_000, ear = 0.25, mar = 0.10, pitch = 30.0))
        engine.process(face(2_000, ear = 0.25, mar = 0.10, pitch = 30.0))
        engine.process(
            timestampMs = 2_500,
            observation = face(2_500, ear = 0.25, mar = 0.10, pitch = 30.0),
            regionalVisibility = RegionalVisibility(lowerFaceObstructed = true, eyeRegionObstructed = true),
        )

        assertTrue(engine.process(face(2_600, ear = 0.25, mar = 0.10, pitch = 30.0)).events.isEmpty())
        assertTrue(engine.process(face(5_500, ear = 0.25, mar = 0.10, pitch = 30.0)).events.isEmpty())
        assertEquals(
            SignType.HEAD_NODDING,
            engine.process(face(5_600, ear = 0.25, mar = 0.10, pitch = 30.0)).events.single().type,
        )
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

    private fun calibratedEngine() = DriveAlertDetectionEngine().apply {
        activatePersistedCalibration(
            com.lhordkent.drivealert.detection.model.CalibrationResult(
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
            ),
        )
    }

    private fun face(timestampMs: Long, ear: Double, mar: Double, pitch: Double) = FaceObservation(
        timestampMs = timestampMs,
        frameWidth = 100,
        frameHeight = 100,
        landmarks = FacialMeasurementCalculatorTest.landmarksWithRatios(ear, mar),
        facialTransformationMatrix = FacialMeasurementCalculatorTest.pitchMatrix(pitch),
    )
}
