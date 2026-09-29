package com.lhordkent.drivealert.detection.face

import com.lhordkent.drivealert.detection.model.FaceAttributeProbabilities
import com.lhordkent.drivealert.detection.model.FaceAttributeResult
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal object FaceAttributeTensor {
    const val ATTRIBUTE_COUNT = 5

    fun dequantize(value: Byte, scale: Float, zeroPoint: Int): Float =
        ((value.toInt() and 0xFF) - zeroPoint) * scale

    fun probabilities(output: ByteArray, scale: Float, zeroPoint: Int): FaceAttributeProbabilities {
        require(output.size == ATTRIBUTE_COUNT) { "FaceAttribNet must return five attributes." }
        val values = output.map { dequantize(it, scale, zeroPoint).coerceIn(0f, 1f) }
        return FaceAttributeProbabilities(
            leftEyeOpen = values[0],
            rightEyeOpen = values[1],
            eyeglasses = values[2],
            mask = values[3],
            sunglasses = values[4],
        )
    }
}

internal class StableAttribute(
    private val activateThreshold: Float = 0.50f,
    private val clearThreshold: Float = activateThreshold,
    private val positivesToActivate: Int = 2,
    private val negativesToClear: Int = 3,
) {
    var value: Boolean = false
        private set
    private var positives = 0
    private var negatives = 0

    fun update(probability: Float): Boolean {
        if (!value) {
            negatives = 0
            positives = if (probability >= activateThreshold) positives + 1 else 0
            if (positives >= positivesToActivate) {
                value = true
                positives = 0
            }
        } else {
            positives = 0
            negatives = if (probability < clearThreshold) negatives + 1 else 0
            if (negatives >= negativesToClear) {
                value = false
                negatives = 0
            }
        }
        return value
    }

    fun reset() {
        value = false
        positives = 0
        negatives = 0
    }
}

internal class FaceAttributeShadowTracker(
    private val staleAfterMs: Long = 2_000L,
) {
    private val eyeglasses = StableAttribute()
    private val lowerFaceObstruction = StableAttribute(
        activateThreshold = 0.80f,
        clearThreshold = 0.50f,
    )
    private val eyeRegionObstruction = StableAttribute(
        activateThreshold = 0.80f,
        clearThreshold = 0.50f,
    )
    private var latest: FaceAttributeResult? = null
    private var disabledReason: String? = null

    fun accept(timestampMs: Long, probabilities: FaceAttributeProbabilities, inferenceDurationMs: Double): FaceAttributeResult {
        check(disabledReason == null) { "FaceAttribNet shadow tracker is disabled." }
        latest = FaceAttributeResult(
            timestampMs = timestampMs,
            ageMs = 0L,
            probabilities = probabilities,
            stableEyeglasses = eyeglasses.update(probabilities.eyeglasses),
            lowerFaceObstructed = lowerFaceObstruction.update(probabilities.mask),
            eyeRegionObstructed = eyeRegionObstruction.update(probabilities.sunglasses),
            inferenceDurationMs = inferenceDurationMs,
        )
        return checkNotNull(latest)
    }

    fun disable(reason: String): FaceAttributeResult {
        disabledReason = reason
        latest = null
        return unavailable(reason)
    }

    fun snapshot(timestampMs: Long): FaceAttributeResult {
        disabledReason?.let { return unavailable(it) }
        val result = latest ?: return unavailable("Waiting for a valid face crop.")
        val ageMs = (timestampMs - requireNotNull(result.timestampMs)).coerceAtLeast(0L)
        return if (ageMs > staleAfterMs) {
            latest = null
            eyeglasses.reset()
            lowerFaceObstruction.reset()
            eyeRegionObstruction.reset()
            unavailable("Face attribute result is stale.", ageMs)
        } else {
            result.copy(ageMs = ageMs)
        }
    }

    private fun unavailable(reason: String, ageMs: Long = 0L) = FaceAttributeResult(
        timestampMs = null,
        ageMs = ageMs,
        probabilities = null,
        stableEyeglasses = null,
        lowerFaceObstructed = null,
        eyeRegionObstructed = null,
        inferenceDurationMs = null,
        errorReason = reason,
    )
}

internal data class PixelCrop(val left: Int, val top: Int, val width: Int, val height: Int)

internal object FaceCropCalculator {
    fun calculate(
        landmarks: List<NormalizedLandmark>,
        frameWidth: Int,
        frameHeight: Int,
        paddingFraction: Double = 0.20,
        minimumSidePixels: Int = 16,
    ): PixelCrop? {
        if (frameWidth <= 0 || frameHeight <= 0 || paddingFraction < 0.0) return null
        val finite = landmarks.filter { it.x.isFinite() && it.y.isFinite() }
        if (finite.size < 3) return null

        val minX = finite.minOf { it.x * frameWidth }
        val maxX = finite.maxOf { it.x * frameWidth }
        val minY = finite.minOf { it.y * frameHeight }
        val maxY = finite.maxOf { it.y * frameHeight }
        val rawSide = max(maxX - minX, maxY - minY)
        if (!rawSide.isFinite() || rawSide < minimumSidePixels) return null

        val side = min(rawSide * (1.0 + 2.0 * paddingFraction), min(frameWidth, frameHeight).toDouble())
        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0
        val left = (centerX - side / 2.0).coerceIn(0.0, frameWidth - side)
        val top = (centerY - side / 2.0).coerceIn(0.0, frameHeight - side)
        val right = (left + side).coerceAtMost(frameWidth.toDouble())
        val bottom = (top + side).coerceAtMost(frameHeight.toDouble())

        val integerLeft = floor(left).toInt().coerceIn(0, frameWidth - 1)
        val integerTop = floor(top).toInt().coerceIn(0, frameHeight - 1)
        val integerRight = ceil(right).toInt().coerceIn(integerLeft + 1, frameWidth)
        val integerBottom = ceil(bottom).toInt().coerceIn(integerTop + 1, frameHeight)
        val integerSide = min(integerRight - integerLeft, integerBottom - integerTop)
        if (integerSide < minimumSidePixels) return null
        return PixelCrop(integerLeft, integerTop, integerSide, integerSide)
    }
}
