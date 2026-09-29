package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.detection.calibration.CalibrationSession
import com.lhordkent.drivealert.detection.measurement.FacialMeasurementCalculator
import com.lhordkent.drivealert.detection.model.CalibrationResult
import com.lhordkent.drivealert.detection.model.CalibrationStatus
import com.lhordkent.drivealert.detection.model.ConfirmedSignEvent
import com.lhordkent.drivealert.detection.model.DetectionChannelStatus
import com.lhordkent.drivealert.detection.model.FaceObservation
import com.lhordkent.drivealert.detection.model.FacialMeasurements
import com.lhordkent.drivealert.detection.model.MonitoringDetectionResult
import com.lhordkent.drivealert.detection.model.RegionalVisibility
import com.lhordkent.drivealert.detection.model.TemporalState
import com.lhordkent.drivealert.detection.temporal.EyeClosureDetector
import com.lhordkent.drivealert.detection.temporal.HeadDownDetector
import com.lhordkent.drivealert.detection.temporal.YawnDetector

/** Pure, synchronized detection state machine. It does not own a camera or perform I/O. */
class DriveAlertDetectionEngine(
    calibrationDurationMs: Long = 5_000L,
) {
    private val calibration = CalibrationSession(calibrationDurationMs)
    private var activeCalibration: CalibrationResult? = null
    private var eyeDetector: EyeClosureDetector? = null
    private var yawnDetector: YawnDetector? = null
    private var headDetector: HeadDownDetector? = null
    private var lastTimestampMs: Long? = null

    @Synchronized
    fun startCalibration() {
        calibration.start()
        activeCalibration = null
        clearDetectors()
    }

    @Synchronized
    fun beginCalibrationPhase() = calibration.beginPhase()

    @Synchronized
    fun repeatCalibrationPhase() = calibration.repeatPhase()

    @Synchronized
    fun calibrationResult(): CalibrationResult? = activeCalibration

    @Synchronized
    fun activatePersistedCalibration(result: CalibrationResult) {
        require(result.schemaVersion == 3) { "Unsupported calibration schema version." }
        val values = listOf(
            result.neutralEar, result.closedEyeEar, result.earThreshold,
            result.neutralMar, result.openMouthMar, result.marThreshold,
            result.neutralHeadPitchDegrees, result.downwardPitchMultiplier,
        )
        require(values.all(Double::isFinite)) { "Calibration values must be finite." }
        require(result.earThreshold > 0.0 && result.marThreshold > 0.0) { "Calibration thresholds must be positive." }
        require(result.downwardPitchMultiplier != 0.0) { "Downward pitch direction must be non-zero." }
        activateCalibration(result)
        lastTimestampMs = null
    }

    @Synchronized
    fun process(
        observation: FaceObservation?,
        regionalVisibility: RegionalVisibility = RegionalVisibility(),
    ): MonitoringDetectionResult {
        val timestampMs = observation?.timestampMs
            ?: requireNotNull(lastTimestampMs) { "A timestamp is required before reporting a missing face." }
        return process(timestampMs, observation, regionalVisibility)
    }

    @Synchronized
    fun process(
        timestampMs: Long,
        observation: FaceObservation?,
        regionalVisibility: RegionalVisibility = RegionalVisibility(),
    ): MonitoringDetectionResult {
        require(lastTimestampMs == null || timestampMs >= checkNotNull(lastTimestampMs)) {
            "Frame timestamps must be monotonic."
        }
        lastTimestampMs = timestampMs

        if (observation == null) {
            // Match Python: face loss is an invalid calibration observation and
            // breaks valid-time continuity instead of counting the missing gap.
            calibration.update(timestampMs, FacialMeasurements(null, null, null))
            clearDetectorEvidence()
            return result(
                timestampMs = timestampMs,
                faceDetected = false,
                measurements = FacialMeasurements(null, null, null),
                relativePitch = null,
                events = emptyList(),
            )
        }
        require(observation.timestampMs == timestampMs) { "Observation and frame timestamps must match." }

        val calculatedMeasurements = FacialMeasurementCalculator.calculate(observation)
        val regionFilteredMeasurements = calculatedMeasurements.copy(
            ear = if (regionalVisibility.eyeRegionObstructed) null else calculatedMeasurements.ear,
            mar = if (regionalVisibility.lowerFaceObstructed) null else calculatedMeasurements.mar,
        )
        val availableSignCount = listOf(
            regionFilteredMeasurements.ear,
            regionFilteredMeasurements.mar,
            regionFilteredMeasurements.rawHeadPitchDegrees,
        ).count { it != null }
        val hasSufficientSigns = availableSignCount >= MINIMUM_AVAILABLE_SIGNS
        if (!hasSufficientSigns) clearDetectorEvidence()
        val measurements = if (hasSufficientSigns) {
            regionFilteredMeasurements
        } else {
            FacialMeasurements(ear = null, mar = null, rawHeadPitchDegrees = null)
        }
        calibration.update(timestampMs, measurements)
        if (calibration.status == CalibrationStatus.COMPLETE && activeCalibration == null) {
            activateCalibration(calibration.buildResult(timestampMs))
        }

        val configured = activeCalibration
        if (configured == null) {
            return result(timestampMs, true, measurements, null, emptyList())
        }

        val relativePitch = measurements.rawHeadPitchDegrees?.let { raw ->
            FacialMeasurementCalculator.relativePitchDegrees(raw, configured.neutralHeadPitchDegrees)
                ?.times(configured.downwardPitchMultiplier)
        }
        val events = buildList {
            eyeDetector?.update(timestampMs, measurements.ear)?.let(::add)
            yawnDetector?.update(timestampMs, measurements.mar)?.let(::add)
            headDetector?.update(timestampMs, relativePitch)?.let(::add)
        }
        return result(timestampMs, true, measurements, relativePitch, events)
    }

    private fun activateCalibration(result: CalibrationResult) {
        activeCalibration = result
        eyeDetector = EyeClosureDetector(
            threshold = result.earThreshold,
            calibrationId = result.calibrationId,
            calibrationVersion = result.schemaVersion,
        )
        yawnDetector = YawnDetector(
            threshold = result.marThreshold,
            calibrationId = result.calibrationId,
            calibrationVersion = result.schemaVersion,
        )
        headDetector = HeadDownDetector(
            calibrationId = result.calibrationId,
            calibrationVersion = result.schemaVersion,
        )
    }

    private fun result(
        timestampMs: Long,
        faceDetected: Boolean,
        measurements: FacialMeasurements,
        relativePitch: Double?,
        events: List<ConfirmedSignEvent>,
    ): MonitoringDetectionResult = MonitoringDetectionResult(
        timestampMs = timestampMs,
        faceDetected = faceDetected,
        measurements = measurements,
        relativeHeadPitchDegrees = relativePitch,
        eye = channelStatus(
            value = measurements.ear,
            threshold = eyeDetector?.threshold,
            state = eyeDetector?.state,
            durationMs = eyeDetector?.candidateDurationMs() ?: 0L,
        ),
        yawn = channelStatus(
            value = measurements.mar,
            threshold = yawnDetector?.threshold,
            state = yawnDetector?.state,
            durationMs = yawnDetector?.candidateDurationMs() ?: 0L,
        ),
        head = channelStatus(
            value = relativePitch,
            threshold = headDetector?.thresholdDegrees,
            state = headDetector?.state,
            durationMs = 0L,
        ),
        headDownwardRatio = headDetector?.downwardRatio,
        events = events,
        calibration = calibration.progress(),
        activeCalibration = activeCalibration,
    )

    private fun channelStatus(
        value: Double?,
        threshold: Double?,
        state: TemporalState?,
        durationMs: Long,
    ) = DetectionChannelStatus(
        state = if (value == null || state == null) TemporalState.UNAVAILABLE else state,
        value = value,
        threshold = threshold,
        candidateDurationMs = durationMs,
    )

    private fun clearDetectorEvidence() {
        eyeDetector?.reset()
        yawnDetector?.reset()
        headDetector?.reset()
    }

    private fun clearDetectors() {
        eyeDetector = null
        yawnDetector = null
        headDetector = null
    }

    companion object {
        private const val MINIMUM_AVAILABLE_SIGNS = 2
    }
}
