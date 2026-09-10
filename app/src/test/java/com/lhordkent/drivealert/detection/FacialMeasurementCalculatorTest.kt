package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.detection.measurement.FacialMeasurementCalculator
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacialMeasurementCalculatorTest {
    @Test
    fun `EAR uses pixel-scaled six-point eye geometry`() {
        val landmarks = landmarksWithRatios(ear = 0.20, mar = 0.30)
        assertEquals(0.20, FacialMeasurementCalculator.eyeAspectRatio(landmarks, 100, 100)!!, 1e-9)
    }

    @Test
    fun `MAR uses three vertical distances over twice mouth width`() {
        val landmarks = landmarksWithRatios(ear = 0.20, mar = 0.30)
        assertEquals(0.30, FacialMeasurementCalculator.mouthAspectRatio(landmarks, 100, 100)!!, 1e-9)
    }

    @Test
    fun `head pitch reads MediaPipe column-major matrix`() {
        assertEquals(20.0, FacialMeasurementCalculator.headPitchDegrees(pitchMatrix(20.0))!!, 1e-5)
    }

    @Test
    fun `relative pitch wraps across 180 degrees`() {
        assertEquals(2.0, FacialMeasurementCalculator.relativePitchDegrees(-179.0, 179.0)!!, 1e-9)
        assertEquals(-2.0, FacialMeasurementCalculator.relativePitchDegrees(179.0, -179.0)!!, 1e-9)
    }

    @Test
    fun `missing required landmarks are invalid not zero`() {
        assertNull(FacialMeasurementCalculator.eyeAspectRatio(emptyList(), 100, 100))
        assertNull(FacialMeasurementCalculator.mouthAspectRatio(emptyList(), 100, 100))
    }

    companion object {
        fun landmarksWithRatios(ear: Double, mar: Double): List<NormalizedLandmark> {
            val points = MutableList(478) { NormalizedLandmark(0.0, 0.0) }
            fun eye(indices: IntArray) {
                val halfVertical = ear * 0.10
                points[indices[0]] = NormalizedLandmark(0.20, 0.50)
                points[indices[3]] = NormalizedLandmark(0.40, 0.50)
                points[indices[1]] = NormalizedLandmark(0.25, 0.50 - halfVertical)
                points[indices[5]] = NormalizedLandmark(0.25, 0.50 + halfVertical)
                points[indices[2]] = NormalizedLandmark(0.35, 0.50 - halfVertical)
                points[indices[4]] = NormalizedLandmark(0.35, 0.50 + halfVertical)
            }
            eye(intArrayOf(362, 385, 387, 263, 373, 380))
            eye(intArrayOf(33, 160, 158, 133, 153, 144))

            points[78] = NormalizedLandmark(0.30, 0.70)
            points[308] = NormalizedLandmark(0.70, 0.70)
            val halfMouthVertical = (0.80 * mar / 3.0) / 2.0
            listOf(81 to 178, 13 to 14, 311 to 402).forEachIndexed { index, (upper, lower) ->
                val x = 0.40 + index * 0.10
                points[upper] = NormalizedLandmark(x, 0.70 - halfMouthVertical)
                points[lower] = NormalizedLandmark(x, 0.70 + halfMouthVertical)
            }
            return points
        }

        fun pitchMatrix(degrees: Double): FloatArray {
            val radians = Math.toRadians(degrees)
            return FloatArray(16).also {
                it[0] = 1f
                it[5] = cos(radians).toFloat()
                it[6] = sin(radians).toFloat()
                it[9] = -sin(radians).toFloat()
                it[10] = cos(radians).toFloat()
                it[15] = 1f
            }
        }
    }
}
