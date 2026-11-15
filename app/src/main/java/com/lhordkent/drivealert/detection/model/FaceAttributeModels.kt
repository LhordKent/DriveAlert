package com.lhordkent.drivealert.detection.model

data class FaceAttributeProbabilities(
    val leftEyeOpen: Float,
    val rightEyeOpen: Float,
    val eyeglasses: Float,
    val mask: Float,
    val sunglasses: Float,
)

/** Face attribute evidence; only fresh, high-confidence eye openness can veto EAR closure. */
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

/** A confident open eye contradicts bilateral closure, even when the other eye
 * is ambiguous. This does not claim that both eyes are open. */
internal fun FaceAttributeResult.contradictsBilateralClosureAt(timestampMs: Long): Boolean {
    val sampledAt = this.timestampMs ?: return false
    val values = probabilities ?: return false
    return available && eyeRegionObstructed != true && timestampMs - sampledAt in 0L..750L &&
        values.leftEyeOpen in 0f..1f && values.rightEyeOpen in 0f..1f &&
        (values.leftEyeOpen >= 0.90f || values.rightEyeOpen >= 0.90f)
}

data class RegionalVisibility(
    val lowerFaceObstructed: Boolean = false,
    val eyeRegionObstructed: Boolean = false,
)
