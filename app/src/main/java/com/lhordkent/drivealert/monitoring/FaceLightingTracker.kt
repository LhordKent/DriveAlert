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
    val poorSinceMs: Long? = null,
) {
    val statusLabel: String get() = when {
        recovering -> "Checking lighting"
        quality == LightingQuality.USABLE -> "Usable"
        quality == LightingQuality.INSUFFICIENT -> "Insufficient lighting"
        timestampMs != null -> "Checking lighting"
        else -> "Waiting for lighting sample"
    }

    fun allowsAssessment(nowMs: Long): Boolean = quality == LightingQuality.USABLE &&
        timestampMs?.let { nowMs - it in 0L..FRESHNESS_MS } == true

    fun hasConfirmedPoorLighting(nowMs: Long): Boolean = quality == LightingQuality.INSUFFICIENT &&
        !recovering && poorSinceMs != null && timestampMs?.let { nowMs - it in 0L..FRESHNESS_MS } == true

    fun hasFreshGoodSample(nowMs: Long): Boolean = poorSinceMs == null &&
        (quality == LightingQuality.USABLE || recovering) &&
        timestampMs?.let { nowMs - it in 0L..FRESHNESS_MS } == true

    /** A delayed speech request cannot outlive the particular dark interval that qualified it. */
    fun matchesPoorEpisode(nowMs: Long, expectedSinceMs: Long?): Boolean =
        expectedSinceMs != null && poorSinceMs == expectedSinceMs && hasConfirmedPoorLighting(nowMs)

    companion object {
        const val FRESHNESS_MS = 1_000L
    }
}

/** Pixel-quality heuristic, not an ML confidence score. Thresholds need camera validation. */
class FaceLightingTracker(
    private val severeDarkThreshold: Double = 15.0,
    private val darkThreshold: Double = 35.0,
    // One binary threshold: all fresh samples not classified poor can establish
    // usability. Separate admission/release thresholds previously left a dead zone.
    private val poorContrastThreshold: Double = 20.0,
    private val recoveryMs: Long = 2_000L,
    private val resumeWindowMs: Long = 2_000L,
    private val poorConfirmationMs: Long = 2_000L,
) {
    private var latest = FaceLightingAssessment()
    private var usableSinceMs: Long? = null
    private var interrupted = false
    private var poorSinceMs: Long? = null
    private var usableBeforePoor = false
    private var confirmedDarkness = false

    init {
        require(severeDarkThreshold >= 0 && severeDarkThreshold <= darkThreshold && darkThreshold <= 255)
        require(poorContrastThreshold in 0.0..255.0)
        require(recoveryMs >= 0 && resumeWindowMs >= 0 && poorConfirmationMs >= 0)
    }

    fun update(nowMs: Long, sample: FaceLightingSample?): FaceLightingAssessment {
        if (sample == null || nowMs - sample.timestampMs !in 0L..FaceLightingAssessment.FRESHNESS_MS) {
            // Pause immediately, but do not mistake a brief sample gap for darkness.
            // Only a NEW, explicitly usable sample can restore a recent usable decision.
            interrupted = true
            usableSinceMs = null
            poorSinceMs = null
            usableBeforePoor = false
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
                val since = poorSinceMs ?: sample.timestampMs.also {
                    poorSinceMs = it
                    usableBeforePoor = quality == LightingQuality.USABLE
                }
                // A dark crop pauses assessment immediately. A sustained run is
                // needed before attributing the problem to facial lighting.
                quality = if (sample.timestampMs - since >= poorConfirmationMs) {
                    confirmedDarkness = true
                    usableBeforePoor = false
                    LightingQuality.INSUFFICIENT
                } else LightingQuality.UNKNOWN
                usableSinceMs = null
            }
            usable -> {
                if (usableBeforePoor && !confirmedDarkness) quality = LightingQuality.USABLE
                poorSinceMs = null
                usableBeforePoor = false
                val since = usableSinceMs ?: sample.timestampMs.also { usableSinceMs = it }
                quality = when {
                    quality == LightingQuality.USABLE || sample.timestampMs - since >= recoveryMs -> {
                        confirmedDarkness = false
                        LightingQuality.USABLE
                    }
                    confirmedDarkness -> LightingQuality.INSUFFICIENT
                    else -> LightingQuality.UNKNOWN
                }
            }
        }
        latest = FaceLightingAssessment(
            sample.timestampMs, quality, sample.luminance, sample.contrast,
            recovering = quality != LightingQuality.USABLE && usableSinceMs != null,
            poorSinceMs = poorSinceMs,
        )
        return latest
    }

    fun reset() {
        latest = FaceLightingAssessment()
        usableSinceMs = null
        interrupted = false
        poorSinceMs = null
        usableBeforePoor = false
        confirmedDarkness = false
    }
}

/** Without a selected face, ambient darkness cannot establish the cause of face loss. */
fun lightingVisibilityIssue(
    faceDetected: Boolean,
    lighting: FaceLightingAssessment,
): VisibilityIssue? = when {
    !faceDetected -> VisibilityIssue.FACE_UNAVAILABLE
    lighting.quality == LightingQuality.INSUFFICIENT && !lighting.recovering -> VisibilityIssue.INSUFFICIENT_LIGHTING
    else -> null
}

/** Resolve the current driver issue without guessing why the face disappeared. */
fun monitoringVisibilityIssue(
    faceDetected: Boolean,
    lighting: FaceLightingAssessment,
    tracking: DriverTrackingState,
    alignmentConfirmed: Boolean,
    regionalVisibility: RegionalVisibility,
): VisibilityIssue? {
    val lightingIssue = lightingVisibilityIssue(faceDetected, lighting)
    return when {
        !alignmentConfirmed -> VisibilityIssue.FACE_UNAVAILABLE
        !tracking.locked -> VisibilityIssue.FACE_UNAVAILABLE
        lightingIssue != null -> lightingIssue
        lighting.quality != LightingQuality.USABLE -> null
        regionalVisibility.lowerFaceObstructed && regionalVisibility.eyeRegionObstructed -> VisibilityIssue.BOTH_REGIONS_OBSTRUCTED
        regionalVisibility.eyeRegionObstructed -> VisibilityIssue.EYE_REGION_OBSTRUCTED
        regionalVisibility.lowerFaceObstructed -> VisibilityIssue.LOWER_FACE_OBSTRUCTED
        else -> null
    }
}
