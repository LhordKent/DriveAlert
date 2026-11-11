package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.detection.face.DriverTrackingState
import com.lhordkent.drivealert.detection.model.RegionalVisibility

enum class LightingQuality { UNKNOWN, USABLE, INSUFFICIENT }

data class FaceLightingSample(val timestampMs: Long, val luminance: Double, val contrast: Double)

data class FaceLightingAssessment(
    val timestampMs: Long? = null,
    val quality: LightingQuality = LightingQuality.UNKNOWN,
    val luminance: Double? = null,
    val contrast: Double? = null,
    val recovering: Boolean = false,
) {
    val statusLabel: String get() = when {
        recovering -> "Checking lighting"
        quality == LightingQuality.USABLE -> "Usable"
        quality == LightingQuality.INSUFFICIENT -> "Insufficient lighting"
        else -> "Waiting for lighting sample"
    }

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
    // One binary threshold: all fresh samples not classified poor can establish
    // usability. Separate admission/release thresholds previously left a dead zone.
    private val poorContrastThreshold: Double = 20.0,
    private val recoveryMs: Long = 2_000L,
    private val resumeWindowMs: Long = 2_000L,
) {
    private var latest = FaceLightingAssessment()
    private var usableSinceMs: Long? = null
    private var interrupted = false

    fun update(nowMs: Long, sample: FaceLightingSample?): FaceLightingAssessment {
        if (sample == null || nowMs - sample.timestampMs !in 0L..FaceLightingAssessment.FRESHNESS_MS) {
            // Pause immediately, but do not mistake a brief sample gap for darkness.
            // Only a NEW, explicitly usable sample can restore a recent usable decision.
            interrupted = true
            usableSinceMs = null
            return FaceLightingAssessment()
        }
        require(sample.luminance.isFinite() && sample.luminance in 0.0..255.0)
        require(sample.contrast.isFinite() && sample.contrast in 0.0..255.0)
        val previousTimestamp = latest.timestampMs
        if (previousTimestamp != null && sample.timestampMs <= previousTimestamp) {
            return if (interrupted) FaceLightingAssessment() else latest
        }

        val poor = sample.luminance < severeDarkThreshold ||
            sample.luminance < darkThreshold && sample.contrast < poorContrastThreshold
        val usable = !poor
        val gap = previousTimestamp?.let { sample.timestampMs - it }
        if (interrupted || gap != null && gap > FaceLightingAssessment.FRESHNESS_MS) {
            val canResume = latest.quality == LightingQuality.USABLE && usable && gap != null && gap <= resumeWindowMs
            if (!canResume) reset()
            usableSinceMs = null
        }
        interrupted = false
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
        interrupted = false
    }
}

/** Missing-face darkness is only a possible cause; it never enables assessment. */
fun lightingVisibilityIssue(
    faceDetected: Boolean,
    lighting: FaceLightingAssessment,
    sceneDark: Boolean,
    detectedFaceCount: Int = if (faceDetected) 1 else 0,
): VisibilityIssue? = when {
    !faceDetected && detectedFaceCount == 0 && sceneDark -> VisibilityIssue.INSUFFICIENT_LIGHTING
    !faceDetected -> VisibilityIssue.FACE_UNAVAILABLE
    lighting.quality == LightingQuality.INSUFFICIENT && !lighting.recovering -> VisibilityIssue.INSUFFICIENT_LIGHTING
    else -> null
}

/** Resolve one issue for a frame; darkness can explain a missing driver, but a
 * passenger/out-of-area face must not turn association loss into a light claim. */
fun monitoringVisibilityIssue(
    faceDetected: Boolean,
    lighting: FaceLightingAssessment,
    sceneDark: Boolean,
    tracking: DriverTrackingState,
    alignmentConfirmed: Boolean,
    regionalVisibility: RegionalVisibility,
): VisibilityIssue? {
    val lightingIssue = lightingVisibilityIssue(faceDetected, lighting, sceneDark, tracking.detectedFaceCount)
    return when {
        !alignmentConfirmed -> VisibilityIssue.FACE_UNAVAILABLE
        lightingIssue == VisibilityIssue.INSUFFICIENT_LIGHTING -> lightingIssue
        !tracking.locked -> VisibilityIssue.FACE_UNAVAILABLE
        lightingIssue != null -> lightingIssue
        lighting.quality != LightingQuality.USABLE -> null
        regionalVisibility.lowerFaceObstructed && regionalVisibility.eyeRegionObstructed -> VisibilityIssue.BOTH_REGIONS_OBSTRUCTED
        regionalVisibility.eyeRegionObstructed -> VisibilityIssue.EYE_REGION_OBSTRUCTED
        regionalVisibility.lowerFaceObstructed -> VisibilityIssue.LOWER_FACE_OBSTRUCTED
        else -> null
    }
}
