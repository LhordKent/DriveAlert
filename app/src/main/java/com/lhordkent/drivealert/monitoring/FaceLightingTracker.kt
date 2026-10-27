package com.lhordkent.drivealert.monitoring

enum class LightingQuality { UNKNOWN, USABLE, INSUFFICIENT }

data class FaceLightingSample(val timestampMs: Long, val luminance: Double, val contrast: Double)

data class FaceLightingAssessment(
    val timestampMs: Long? = null,
    val quality: LightingQuality = LightingQuality.UNKNOWN,
    val luminance: Double? = null,
    val contrast: Double? = null,
    val recovering: Boolean = false,
) {
    fun allowsAssessment(nowMs: Long): Boolean = quality == LightingQuality.USABLE &&
        timestampMs?.let { nowMs - it in 0L..FRESHNESS_MS } == true

    companion object {
        const val FRESHNESS_MS = 1_000L
    }
}

/** Pixel-quality heuristic, not an ML confidence score. Thresholds need camera validation. */
class FaceLightingTracker(
    private val severeDarkThreshold: Double = 25.0,
    private val darkThreshold: Double = 45.0,
    private val poorContrastThreshold: Double = 30.0,
    private val brightThreshold: Double = 55.0,
    private val usableDarkThreshold: Double = 35.0,
    private val usableContrastThreshold: Double = 40.0,
    private val recoveryMs: Long = 2_000L,
) {
    private var latest = FaceLightingAssessment()
    private var usableSinceMs: Long? = null

    fun update(nowMs: Long, sample: FaceLightingSample?): FaceLightingAssessment {
        if (sample == null || nowMs - sample.timestampMs !in 0L..FaceLightingAssessment.FRESHNESS_MS) {
            reset()
            return latest
        }
        require(sample.luminance.isFinite() && sample.luminance in 0.0..255.0)
        require(sample.contrast.isFinite() && sample.contrast in 0.0..255.0)
        val previousTimestamp = latest.timestampMs
        if (previousTimestamp != null && sample.timestampMs < previousTimestamp) return latest
        if (previousTimestamp != null && sample.timestampMs - previousTimestamp > FaceLightingAssessment.FRESHNESS_MS) reset()
        if (sample.timestampMs == latest.timestampMs) return latest

        val poor = sample.luminance < severeDarkThreshold ||
            sample.luminance < darkThreshold && sample.contrast < poorContrastThreshold
        val usable = sample.luminance >= brightThreshold ||
            sample.luminance >= usableDarkThreshold && sample.contrast >= usableContrastThreshold
        var quality = latest.quality
        when {
            poor -> {
                quality = LightingQuality.INSUFFICIENT
                usableSinceMs = null
            }
            usable -> {
                val since = usableSinceMs ?: sample.timestampMs.also { usableSinceMs = it }
                if (sample.timestampMs - since >= recoveryMs) quality = LightingQuality.USABLE
            }
            else -> usableSinceMs = null // Borderline pixels retain the decision but cannot establish recovery.
        }
        latest = FaceLightingAssessment(
            sample.timestampMs, quality, sample.luminance, sample.contrast,
            recovering = quality != LightingQuality.USABLE && usableSinceMs != null,
        )
        return latest
    }

    fun reset() {
        latest = FaceLightingAssessment()
        usableSinceMs = null
    }
}

/** Missing-face darkness is only a possible cause; it never enables assessment. */
fun lightingVisibilityIssue(
    faceDetected: Boolean,
    lighting: FaceLightingAssessment,
    sceneDark: Boolean,
): VisibilityIssue? = when {
    !faceDetected && sceneDark -> VisibilityIssue.INSUFFICIENT_LIGHTING
    !faceDetected -> VisibilityIssue.FACE_UNAVAILABLE
    lighting.quality == LightingQuality.INSUFFICIENT && !lighting.recovering -> VisibilityIssue.INSUFFICIENT_LIGHTING
    else -> null
}
