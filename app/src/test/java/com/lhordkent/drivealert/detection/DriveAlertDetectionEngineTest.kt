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
    @Test fun `closed eye alert keeps its schedule through larger tracked head movements`() {
        val selector = com.lhordkent.drivealert.detection.face.DriverFaceSelector()
        val engine = calibratedEngine()
        fun selected(time: Long, ear: Double, shift: Double): com.lhordkent.drivealert.detection.face.DriverSelection {
            val observation = face(time, ear, 0.1, 4.0).let { original ->
                original.copy(landmarks = original.landmarks.map {
                    it.copy(x = 0.25 + it.x * 0.25 + shift, y = 0.30 + it.y * 0.25)
                })
            }
            return selector.select(time, listOf(observation), com.lhordkent.drivealert.detection.face.DriverRegion.DEFAULT, 100, 100)
        }
        for (time in 0L..1_000L step 200) selected(time, 0.25, 0.0)
        val events = mutableListOf<com.lhordkent.drivealert.detection.model.ConfirmedSignEvent>()
        for ((i, time) in (1_200L..3_200L step 200).withIndex()) {
            val selection = selected(time, 0.10, if (i % 2 == 0) 0.10 else 0.0)
            assertTrue(selection.tracking.locked)
            events += engine.process(checkNotNull(selection.observation)).events
        }
        assertEquals(SignType.PROLONGED_EYE_CLOSURE, events.single().type)
        assertEquals(3_200L, events.single().timestampMs)
    }

    @Test fun `temporary evidence pause preserves completed phase and excludes the paused interval`() {
        val engine = DriveAlertDetectionEngine(calibrationDurationMs = 1_000)
        engine.startCalibration()
        engine.beginCalibrationPhase()
        engine.process(face(0, 0.4, 0.1, 4.0))
        engine.process(face(1_000, 0.4, 0.1, 4.0))
        engine.beginCalibrationPhase()
        engine.process(face(1_200, 0.1, 0.1, 4.0))
        engine.process(face(1_600, 0.1, 0.1, 4.0))
        engine.invalidateTracking()
        assertEquals(com.lhordkent.drivealert.detection.model.CalibrationPhase.EYES_CLOSED, engine.calibrationProgress().phase)
        assertEquals(400L, engine.calibrationProgress().validDurationMs)
        engine.process(face(5_000, 0.1, 0.1, 4.0))
        assertEquals(400L, engine.calibrationProgress().validDurationMs)
        engine.process(face(5_600, 0.1, 0.1, 4.0))
        assertEquals(com.lhordkent.drivealert.detection.model.CalibrationPhase.MOUTH_OPEN, engine.calibrationProgress().phase)
        engine.invalidateDriverTarget()
        assertEquals(com.lhordkent.drivealert.detection.model.CalibrationPhase.NEUTRAL, engine.calibrationProgress().phase)
    }
    @Test fun `stationary closed eyes still alert while movement advisory becomes inactive`() {
        val engine = calibratedEngine()
        val movement = com.lhordkent.drivealert.monitoring.DriverMovementTracker()
        val events = mutableListOf<com.lhordkent.drivealert.detection.model.ConfirmedSignEvent>()
        for (t in 0L..25_000L step 200) {
            val observation = face(t, 0.10, 0.1, 4.0)
            movement.update(observation)
            events += engine.process(observation).events
        }
        assertEquals(SignType.PROLONGED_EYE_CLOSURE, events.single().type)
        assertEquals(2_000L, events.single().timestampMs)
        assertEquals(com.lhordkent.drivealert.monitoring.DriverMovementStatus.NO_MOVEMENT, movement.assessment.status)
        assertEquals("stored", engine.calibrationResult()?.calibrationId)
    }
    @Test fun `driver target loss discards all unfinished calibration phases`() {
        val engine = DriveAlertDetectionEngine(calibrationDurationMs = 100)
        engine.startCalibration()
        collect(engine, 0, ear = 0.40, mar = 0.10, pitch = 10.0)
        engine.beginCalibrationPhase()
        engine.process(face(200, 0.1, 0.1, 10.0))
        engine.invalidateDriverTarget()
        val result = engine.process(300, null)
        assertEquals(com.lhordkent.drivealert.detection.model.CalibrationPhase.NEUTRAL, result.calibration.phase)
        assertEquals(com.lhordkent.drivealert.detection.model.CalibrationStatus.READY, result.calibration.status)
        assertEquals(0, result.calibration.validSampleCount)
        assertEquals(0L, result.calibration.validDurationMs)
    }

    @Test fun `driver stepping out keeps finished calibration steps and repeats only the current one`() {
        val engine = DriveAlertDetectionEngine(calibrationDurationMs = 100)
        engine.startCalibration()
        collect(engine, 0, ear = 0.40, mar = 0.10, pitch = 10.0)
        engine.beginCalibrationPhase()
        engine.process(face(200, 0.1, 0.1, 10.0))
        engine.process(face(250, 0.1, 0.1, 10.0))
        val revisionBefore = engine.calibrationProgress().revision
        engine.driverTrackReacquired()
        val progress = engine.calibrationProgress()
        assertEquals(com.lhordkent.drivealert.detection.model.CalibrationPhase.EYES_CLOSED, progress.phase)
        assertEquals(com.lhordkent.drivealert.detection.model.CalibrationStatus.READY, progress.status)
        assertEquals(0L, progress.validDurationMs)
        assertEquals(0, progress.validSampleCount)
        assertTrue(progress.revision > revisionBefore)
        // The step can be redone and calibration continues from there.
        engine.beginCalibrationPhase()
        engine.process(face(1_000, 0.1, 0.1, 10.0))
        engine.process(face(1_100, 0.1, 0.1, 10.0))
        assertEquals(com.lhordkent.drivealert.detection.model.CalibrationPhase.MOUTH_OPEN, engine.calibrationProgress().phase)
    }

    @Test fun `driver target loss resets closure evidence but retains completed calibration`() {
        val engine = calibratedEngine()
        engine.process(face(0, 0.10, 0.1, 4.0))
        engine.process(face(1_900, 0.10, 0.1, 4.0))
        engine.invalidateDriverTarget()
        val result = engine.process(face(2_000, 0.10, 0.1, 4.0))
        assertTrue(result.events.isEmpty())
        assertEquals("stored", result.activeCalibration?.calibrationId)
        assertEquals(0L, result.eye.candidateDurationMs)
    }

    @Test
    fun `lighting gate preserves face and calibration but suppresses all signs`() {
        val engine = calibratedEngine()
        engine.process(face(0, ear = 0.10, mar = 0.90, pitch = 30.0))
        val blocked = engine.process(face(3_000, ear = 0.10, mar = 0.90, pitch = 30.0), assessmentAllowed = false)
        assertTrue(blocked.faceDetected)
        assertEquals("stored", blocked.activeCalibration?.calibrationId)
        assertTrue(blocked.events.isEmpty())
        assertEquals(TemporalState.UNAVAILABLE, blocked.eye.state)
        assertEquals(TemporalState.UNAVAILABLE, blocked.yawn.state)
        assertEquals(TemporalState.UNAVAILABLE, blocked.head.state)
        assertTrue(engine.process(face(3_100, ear = 0.10, mar = 0.10, pitch = 4.0)).events.isEmpty())
        assertTrue(engine.process(face(5_099, ear = 0.10, mar = 0.10, pitch = 4.0)).events.isEmpty())
        assertEquals(SignType.PROLONGED_EYE_CLOSURE,
            engine.process(face(5_100, ear = 0.10, mar = 0.10, pitch = 4.0)).events.single().type)
    }

    @Test
    fun `darkness and its recovery gap never count toward calibration valid time`() {
        val engine = DriveAlertDetectionEngine(calibrationDurationMs = 5_000)
        engine.startCalibration()
        engine.beginCalibrationPhase()
        engine.process(face(0, 0.25, 0.1, 4.0))
        assertEquals(1_000L, engine.process(face(1_000, 0.25, 0.1, 4.0)).calibration.validDurationMs)
        val blocked = engine.process(face(8_000, 0.25, 0.1, 4.0), assessmentAllowed = false)
        assertEquals(1_000L, blocked.calibration.validDurationMs)
        assertEquals(1_000L, engine.process(face(10_000, 0.25, 0.1, 4.0)).calibration.validDurationMs)
        assertEquals(2_000L, engine.process(face(11_000, 0.25, 0.1, 4.0)).calibration.validDurationMs)
    }

    @Test
    fun `expired lighting interrupts calibration even when no new frame arrives`() {
        val engine = DriveAlertDetectionEngine()
        engine.startCalibration()
        engine.beginCalibrationPhase()
        engine.process(face(0, 0.25, 0.1, 4.0))
        engine.process(face(1_000, 0.25, 0.1, 4.0))
        engine.invalidateTracking()
        assertEquals(1_000L, engine.process(face(20_000, 0.25, 0.1, 4.0)).calibration.validDurationMs)
    }

    @Test fun `lighting confirmation and recovery never contribute bad calibration samples`() {
        val engine = DriveAlertDetectionEngine(calibrationDurationMs = 5_000)
        val tracker = com.lhordkent.drivealert.monitoring.FaceLightingTracker()
        engine.startCalibration()
        engine.beginCalibrationPhase()
        fun process(time: Long, brightness: Double, contrast: Double) = engine.process(
            face(time, 0.25, 0.1, 4.0), assessmentAllowed = tracker.update(time,
                com.lhordkent.drivealert.monitoring.FaceLightingSample(time, brightness, contrast)).allowsAssessment(time))
        for (time in 0L..2_000L step 200) assertEquals(0L, process(time, 37.0, 15.0).calibration.validDurationMs)
        assertEquals(200L, process(2_200, 37.0, 15.0).calibration.validDurationMs)
        for (time in 2_400L..4_400L step 200) {
            val result = process(time, 10.0, 0.0)
            assertEquals(200L, result.calibration.validDurationMs)
            assertTrue(result.events.isEmpty())
        }
        for (time in 4_600L..6_400L step 200) assertEquals(200L, process(time, 37.0, 15.0).calibration.validDurationMs)
        assertEquals(200L, process(6_600, 37.0, 15.0).calibration.validDurationMs)
        assertEquals(400L, process(6_800, 37.0, 15.0).calibration.validDurationMs)
    }

    @Test fun `calibration collects fresh faces during brief lighting gaps but pauses on poor light`() {
        val engine = DriveAlertDetectionEngine(calibrationDurationMs = 5_000)
        val gate = CalibrationLightingGrace()
        engine.startCalibration()
        engine.beginCalibrationPhase()
        fun process(time: Long, lighting: com.lhordkent.drivealert.monitoring.FaceLightingAssessment) =
            engine.process(face(time, 0.25, 0.1, 4.0), assessmentAllowed = gate.allowsAssessment(time, time, lighting))
        val good = com.lhordkent.drivealert.monitoring.FaceLightingAssessment(0,
            com.lhordkent.drivealert.monitoring.LightingQuality.USABLE)
        val missing = com.lhordkent.drivealert.monitoring.FaceLightingAssessment()
        process(0, good)
        assertEquals(1_000L, process(1_000, missing).calibration.validDurationMs)
        assertEquals(2_000L, process(2_000, missing).calibration.validDurationMs)
        assertEquals(2_000L, process(2_001, missing).calibration.validDurationMs)
        assertEquals(2_000L, process(2_100, good.copy(timestampMs = 2_100)).calibration.validDurationMs)
        assertEquals(2_100L, process(2_200, missing).calibration.validDurationMs)
        val poor = good.copy(timestampMs = 2_300,
            quality = com.lhordkent.drivealert.monitoring.LightingQuality.UNKNOWN, poorSinceMs = 2_300)
        assertEquals(2_100L, process(2_300, poor).calibration.validDurationMs)
        assertEquals(2_100L, process(2_400, missing).calibration.validDurationMs)
    }

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

    @Test
    fun `stream interruption resets candidate evidence without removing calibration`() {
        val engine = calibratedEngine()
        engine.process(face(0, ear = 0.10, mar = 0.10, pitch = 4.0))
        engine.process(face(1_000, ear = 0.10, mar = 0.10, pitch = 4.0))

        engine.invalidateTracking()

        assertEquals("stored", engine.calibrationResult()?.calibrationId)
        assertTrue(engine.process(face(10_000, ear = 0.10, mar = 0.10, pitch = 4.0)).events.isEmpty())
        assertTrue(engine.process(face(11_999, ear = 0.10, mar = 0.10, pitch = 4.0)).events.isEmpty())
        assertEquals(
            SignType.PROLONGED_EYE_CLOSURE,
            engine.process(face(12_000, ear = 0.10, mar = 0.10, pitch = 4.0)).events.single().type,
        )
    }

    @Test fun `looking down with independently open eyes does not create closure events but real closure still fires`() {
        val engine = calibratedEngine()
        for (time in 0L..4_000L step 250) {
            // Replay the conflict seen on the phone: EAR below threshold while
            // the independent classifier reports both eyes strongly open.
            val result = engine.process(face(time, 0.14, 0.1, 4.0), faceAttributes = attributes(time, 0.98f, 0.96f))
            assertTrue(result.events.isEmpty())
            assertEquals(TemporalState.NORMAL, result.eye.state)
        }
        val events = (5_000L..7_000L step 250).flatMap { time ->
            engine.process(face(time, 0.10, 0.1, 4.0), faceAttributes = attributes(time, 0.02f, 0.03f)).events
        }
        assertEquals(listOf(SignType.PROLONGED_EYE_CLOSURE), events.map { it.type })
        assertEquals(7_000L, events.single().timestampMs)
    }

    @Test fun `stale or weak open eye evidence cannot veto a real closure`() {
        for (weak in listOf(false, true)) {
            val engine = calibratedEngine()
            val events = (2_000L..4_000L step 250).flatMap { time ->
                val evidence = if (weak) attributes(time, 0.89f, 0.4f) else attributes(0, 0.99f, 0.99f)
                engine.process(face(time, 0.10, 0.1, 4.0), faceAttributes = evidence).events
            }
            assertEquals(listOf(SignType.PROLONGED_EYE_CLOSURE), events.map { it.type })
        }
    }

    @Test fun `masked asymmetric open eye replay breaks closure timer then both closed still confirms`() {
        for (swapEyes in listOf(false, true)) {
            val engine = calibratedEngine()
            engine.activatePersistedCalibration(checkNotNull(engine.calibrationResult()).copy(earThreshold = 0.2838101952071067))
            // Modeled replay of the 22:04:29 sequence. The final event EAR uses
            // the last logged attribute scores; event-time scores were not logged.
            val samples = listOf(
                Triple(0L, 0.19415588462667893, 0.67578125f to 0.82421875f),
                Triple(609L, 0.19659955274353128, 0.359375f to 0.92578125f),
                Triple(1_169L, 0.18669566854792013, 0.88671875f to 0.7734375f),
                Triple(1_733L, 0.16655958725652753, 0.953125f to 0.640625f),
                Triple(2_202L, 0.1754953229826895, 0.953125f to 0.640625f),
            )
            for ((time, ear, open) in samples) {
                val (left, right) = if (swapEyes) open.second to open.first else open
                val result = engine.process(face(time, ear, 0.1, 4.0),
                    RegionalVisibility(lowerFaceObstructed = true),
                    faceAttributes = attributes(time, left, right).copy(lowerFaceObstructed = true))
                assertTrue(result.events.isEmpty())
                assertEquals(TemporalState.UNAVAILABLE, result.yawn.state)
            }
            engine.process(face(2_500, 0.17, 0.1, 4.0),
                RegionalVisibility(lowerFaceObstructed = true),
                faceAttributes = attributes(2_500, 0.95f, 0.5f))
            val events = (3_000L..5_000L step 250).flatMap { time ->
                engine.process(face(time, 0.10, 0.1, 4.0),
                    RegionalVisibility(lowerFaceObstructed = true),
                    faceAttributes = attributes(time, 0.02f, 0.03f)).events
            }
            assertEquals(listOf(SignType.PROLONGED_EYE_CLOSURE), events.map { it.type })
            assertEquals(5_000L, events.single().timestampMs)
        }
    }

    @Test fun `open eye cross check leaves yawning and head down detection active`() {
        val engine = calibratedEngine()
        val events = (0L..3_000L step 250).flatMap { time ->
            engine.process(face(time, 0.14, 0.9, 30.0), faceAttributes = attributes(time, 0.99f, 0.99f)).events
        }
        assertEquals(setOf(SignType.YAWNING, SignType.HEAD_NODDING), events.map { it.type }.toSet())
    }

    private fun attributes(time: Long, left: Float, right: Float) = com.lhordkent.drivealert.detection.model.FaceAttributeResult(
        timestampMs = time, ageMs = 0L,
        probabilities = com.lhordkent.drivealert.detection.model.FaceAttributeProbabilities(left, right, 0f, 0f, 0f),
        stableEyeglasses = false, lowerFaceObstructed = false, eyeRegionObstructed = false, inferenceDurationMs = 1.0,
    )

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
