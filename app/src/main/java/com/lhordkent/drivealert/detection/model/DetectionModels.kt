package com.lhordkent.drivealert.detection.model

data class NormalizedLandmark(
    val x: Double,
    val y: Double,
)

data class FaceObservation(
    val timestampMs: Long,
    val frameWidth: Int,
    val frameHeight: Int,
    val landmarks: List<NormalizedLandmark>,
    /** MediaPipe facial transformation matrix in its documented column-major layout. */
    val facialTransformationMatrix: FloatArray? = null,
)

data class FacialMeasurements(
    val ear: Double?,
    val mar: Double?,
    val rawHeadPitchDegrees: Double?,
)

enum class CalibrationPhase {
    NEUTRAL,
    EYES_CLOSED,
    MOUTH_OPEN,
    HEAD_DOWN,
    COMPLETE,
}

enum class CalibrationStatus {
    READY,
    COLLECTING,
    COMPLETE,
}

data class CalibrationProgress(
    val status: CalibrationStatus,
    val phase: CalibrationPhase,
    val validDurationMs: Long,
    val requiredDurationMs: Long,
    val validSampleCount: Int,
)

data class CalibrationResult(
    val calibrationId: String,
    val schemaVersion: Int = 3,
    /** Monotonic source timestamp, not wall-clock time. */
    val calibratedAtTimestampMs: Long,
    val neutralEar: Double,
    val closedEyeEar: Double,
    val earThreshold: Double,
    val neutralMar: Double,
    val openMouthMar: Double,
    val marThreshold: Double,
    val neutralHeadPitchDegrees: Double,
    val downwardPitchMultiplier: Double,
)

enum class SignType {
    PROLONGED_EYE_CLOSURE,
    YAWNING,
    HEAD_NODDING,
}

data class ConfirmedSignEvent(
    val type: SignType,
    val timestampMs: Long,
    val qualifyingDurationMs: Long,
    val measuredValue: Double,
    val threshold: Double,
    val calibrationId: String,
    val calibrationVersion: Int,
    val temporalContext: Map<String, Double> = emptyMap(),
)

enum class TemporalState {
    NORMAL,
    CANDIDATE,
    CONFIRMED,
    UNAVAILABLE,
}

data class DetectionChannelStatus(
    val state: TemporalState,
    val value: Double?,
    val threshold: Double?,
    val candidateDurationMs: Long = 0,
)

data class MonitoringDetectionResult(
    val timestampMs: Long,
    val faceDetected: Boolean,
    val measurements: FacialMeasurements,
    val relativeHeadPitchDegrees: Double?,
    val eye: DetectionChannelStatus,
    val yawn: DetectionChannelStatus,
    val head: DetectionChannelStatus,
    val headDownwardRatio: Double?,
    val events: List<ConfirmedSignEvent>,
    val calibration: CalibrationProgress,
    val activeCalibration: CalibrationResult?,
)
