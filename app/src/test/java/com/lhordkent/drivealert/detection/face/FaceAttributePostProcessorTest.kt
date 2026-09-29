package com.lhordkent.drivealert.detection.face

import com.lhordkent.drivealert.detection.model.FaceAttributeProbabilities
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceAttributePostProcessorTest {
    @Test
    fun `uint8 output is dequantized and mapped in model order`() {
        val output = byteArrayOf(0, 64, 128.toByte(), 192.toByte(), 255.toByte())

        val result = FaceAttributeTensor.probabilities(output, scale = 1f / 256f, zeroPoint = 0)

        assertEquals(0f, result.leftEyeOpen, 1e-6f)
        assertEquals(0.25f, result.rightEyeOpen, 1e-6f)
        assertEquals(0.50f, result.eyeglasses, 1e-6f)
        assertEquals(0.75f, result.mask, 1e-6f)
        assertEquals(255f / 256f, result.sunglasses, 1e-6f)
    }

    @Test
    fun `attributes activate after two positives and clear after three negatives`() {
        val attribute = StableAttribute()

        assertFalse(attribute.update(0.9f))
        assertTrue(attribute.update(0.9f))
        assertTrue(attribute.update(0.1f))
        assertTrue(attribute.update(0.1f))
        assertFalse(attribute.update(0.1f))
    }

    @Test
    fun `lower face hysteresis ignores open-mouth scores between clear and activation thresholds`() {
        val obstruction = StableAttribute(activateThreshold = 0.80f, clearThreshold = 0.50f)

        assertFalse(obstruction.update(0.40f))
        assertFalse(obstruction.update(0.99f))
        assertTrue(obstruction.update(0.99f))
        assertTrue(obstruction.update(0.60f))
        assertTrue(obstruction.update(0.40f))
        assertTrue(obstruction.update(0.40f))
        assertFalse(obstruction.update(0.40f))
    }

    @Test
    fun `tracker keeps independent stable classifications and expires cached result`() {
        val tracker = FaceAttributeShadowTracker(staleAfterMs = 2_000L)
        val probabilities = FaceAttributeProbabilities(0.8f, 0.7f, 0.9f, 0.1f, 0.8f)

        tracker.accept(1_000L, probabilities, 10.0)
        val activated = tracker.accept(1_500L, probabilities, 9.0)

        assertTrue(activated.stableEyeglasses == true)
        assertFalse(activated.lowerFaceObstructed == true)
        assertTrue(activated.eyeRegionObstructed == true)
        assertNotNull(tracker.snapshot(3_500L).probabilities)
        assertNull(tracker.snapshot(3_501L).probabilities)
    }

    @Test
    fun `crop adds twenty percent per side and clamps to a square frame region`() {
        val landmarks = listOf(
            NormalizedLandmark(0.2, 0.2),
            NormalizedLandmark(0.8, 0.2),
            NormalizedLandmark(0.8, 0.8),
            NormalizedLandmark(0.2, 0.8),
        )

        val crop = FaceCropCalculator.calculate(landmarks, frameWidth = 100, frameHeight = 80)

        assertEquals(PixelCrop(left = 10, top = 0, width = 80, height = 80), crop)
    }

    @Test
    fun `invalid or tiny landmark bounds do not produce a crop`() {
        val tiny = listOf(
            NormalizedLandmark(0.50, 0.50),
            NormalizedLandmark(0.51, 0.50),
            NormalizedLandmark(0.50, 0.51),
        )

        assertNull(FaceCropCalculator.calculate(tiny, frameWidth = 100, frameHeight = 100))
    }
}
