package com.lhordkent.drivealert.detection.model

data class FaceAttributeProbabilities(
    val leftEyeOpen: Float,
    val rightEyeOpen: Float,
    val eyeglasses: Float,
    val mask: Float,
    val sunglasses: Float,
)

/** Diagnostic-only output from the shadow FaceAttribNet pass. */
data class FaceAttributeResult(
    val timestampMs: Long?,
    val ageMs: Long,
    val probabilities: FaceAttributeProbabilities?,
    val stableEyeglasses: Boolean?,
    val lowerFaceObstructed: Boolean?,
    val eyeRegionObstructed: Boolean?,
    val inferenceDurationMs: Double?,
    val errorReason: String? = null,
) {
    val available: Boolean
        get() = probabilities != null && errorReason == null
}

data class RegionalVisibility(
    val lowerFaceObstructed: Boolean = false,
    val eyeRegionObstructed: Boolean = false,
)
