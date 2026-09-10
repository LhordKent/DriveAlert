package com.lhordkent.drivealert.detection.measurement

import com.lhordkent.drivealert.detection.model.FaceObservation
import com.lhordkent.drivealert.detection.model.FacialMeasurements
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import kotlin.math.atan2
import kotlin.math.hypot

object FacialMeasurementCalculator {
    private val leftEye = intArrayOf(362, 385, 387, 263, 373, 380)
    private val rightEye = intArrayOf(33, 160, 158, 133, 153, 144)
    private const val mouthLeft = 78
    private const val mouthRight = 308
    private val mouthVerticalPairs = arrayOf(81 to 178, 13 to 14, 311 to 402)

    fun calculate(observation: FaceObservation): FacialMeasurements = FacialMeasurements(
        ear = eyeAspectRatio(observation.landmarks, observation.frameWidth, observation.frameHeight),
        mar = mouthAspectRatio(observation.landmarks, observation.frameWidth, observation.frameHeight),
        rawHeadPitchDegrees = headPitchDegrees(observation.facialTransformationMatrix),
    )

    fun eyeAspectRatio(
        landmarks: List<NormalizedLandmark>,
        frameWidth: Int,
        frameHeight: Int,
    ): Double? {
        if (!hasLandmarks(landmarks, leftEye + rightEye) || frameWidth <= 0 || frameHeight <= 0) {
            return null
        }
        val left = singleEyeAspectRatio(landmarks, leftEye, frameWidth, frameHeight) ?: return null
        val right = singleEyeAspectRatio(landmarks, rightEye, frameWidth, frameHeight) ?: return null
        return ((left + right) / 2.0).takeIf(Double::isFinite)
    }

    fun mouthAspectRatio(
        landmarks: List<NormalizedLandmark>,
        frameWidth: Int,
        frameHeight: Int,
    ): Double? {
        val required = intArrayOf(
            mouthLeft,
            mouthRight,
            *mouthVerticalPairs.flatMap { listOf(it.first, it.second) }.toIntArray(),
        )
        if (!hasLandmarks(landmarks, required) || frameWidth <= 0 || frameHeight <= 0) return null

        val horizontal = distance(landmarks[mouthLeft], landmarks[mouthRight], frameWidth, frameHeight)
        if (!horizontal.isFinite() || horizontal <= 0.0) return null
        val vertical = mouthVerticalPairs.sumOf { (upper, lower) ->
            distance(landmarks[upper], landmarks[lower], frameWidth, frameHeight)
        }
        return (vertical / (2.0 * horizontal)).takeIf(Double::isFinite)
    }

    /**
     * Mirrors numpy atan2(rotation[2, 1], rotation[2, 2]). MediaPipe documents the
     * flattened facial transformation matrix as column-major, so those cells are 6 and 10.
     */
    fun headPitchDegrees(columnMajorMatrix: FloatArray?): Double? {
        if (columnMajorMatrix == null || columnMajorMatrix.size < 16) return null
        val r21 = columnMajorMatrix[6].toDouble()
        val r22 = columnMajorMatrix[10].toDouble()
        if (!r21.isFinite() || !r22.isFinite()) return null
        return Math.toDegrees(atan2(r21, r22)).takeIf(Double::isFinite)
    }

    fun relativePitchDegrees(rawPitch: Double, neutralPitch: Double): Double? {
        if (!rawPitch.isFinite() || !neutralPitch.isFinite()) return null
        var wrapped = (rawPitch - neutralPitch + 180.0) % 360.0
        if (wrapped < 0.0) wrapped += 360.0
        return wrapped - 180.0
    }

    private fun singleEyeAspectRatio(
        landmarks: List<NormalizedLandmark>,
        indices: IntArray,
        width: Int,
        height: Int,
    ): Double? {
        val horizontal = distance(landmarks[indices[0]], landmarks[indices[3]], width, height)
        if (!horizontal.isFinite() || horizontal <= 0.0) return null
        val verticalOne = distance(landmarks[indices[1]], landmarks[indices[5]], width, height)
        val verticalTwo = distance(landmarks[indices[2]], landmarks[indices[4]], width, height)
        return ((verticalOne + verticalTwo) / (2.0 * horizontal)).takeIf(Double::isFinite)
    }

    private fun distance(
        first: NormalizedLandmark,
        second: NormalizedLandmark,
        width: Int,
        height: Int,
    ): Double = hypot(
        (first.x - second.x) * width,
        (first.y - second.y) * height,
    )

    private fun hasLandmarks(landmarks: List<NormalizedLandmark>, required: IntArray): Boolean {
        if (required.any { it !in landmarks.indices }) return false
        return required.all { index -> landmarks[index].x.isFinite() && landmarks[index].y.isFinite() }
    }
}
