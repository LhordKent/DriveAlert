package com.lhordkent.drivealert.monitoring

data class SceneBrightness(
    val timestampMs: Long,
    val raw: Double,
    val smoothed: Double,
    val lowLight: Boolean,
)

/** Image brightness is a visibility hint, not proof that face tracking is unavailable. */
class SceneBrightnessTracker(
    private val darkThreshold: Double = 45.0,
    private val clearThreshold: Double = 90.0,
    private val requiredSamples: Int = 3,
    private val smoothingAlpha: Double = 0.25,
    private val staleAfterMs: Long = 3_000L,
) {
    private var latest: SceneBrightness? = null
    private var darkSamples = 0
    private var brightSamples = 0

    init {
        require(darkThreshold >= 0.0 && clearThreshold <= 255.0 && darkThreshold < clearThreshold)
        require(requiredSamples > 0 && smoothingAlpha > 0.0 && smoothingAlpha <= 1.0)
        require(staleAfterMs > 0)
    }

    fun update(timestampMs: Long, luminance: Double): SceneBrightness {
        require(luminance.isFinite() && luminance in 0.0..255.0)
        require(latest == null || timestampMs >= checkNotNull(latest).timestampMs)
        if (latest?.let { timestampMs - it.timestampMs > staleAfterMs } == true) reset()
        val previous = latest
        val smoothed = previous?.let { it.smoothed + smoothingAlpha * (luminance - it.smoothed) } ?: luminance
        var lowLight = previous?.lowLight ?: false
        darkSamples = if (!lowLight && smoothed < darkThreshold) darkSamples + 1 else 0
        brightSamples = if (lowLight && smoothed > clearThreshold) brightSamples + 1 else 0
        if (darkSamples >= requiredSamples) {
            lowLight = true
            darkSamples = 0
        }
        if (brightSamples >= requiredSamples) {
            lowLight = false
            brightSamples = 0
        }
        return SceneBrightness(timestampMs, luminance, smoothed, lowLight).also { latest = it }
    }

    fun reset() {
        latest = null
        darkSamples = 0
        brightSamples = 0
    }
}
