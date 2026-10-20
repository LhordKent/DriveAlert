package com.lhordkent.drivealert.detection.frame

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameLuminanceSamplerTest {
    @Test
    fun `black gray and white pixels map across luminance range`() {
        assertEquals(0.0, sample(0xFF000000.toInt()), 0.01)
        assertEquals(127.0, sample(0xFF7F7F7F.toInt()), 0.01)
        assertEquals(255.0, sample(0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun `trimmed average ignores sparse bright outliers`() {
        val pixels = IntArray(100) { 0xFF202020.toInt() }
        repeat(8) { pixels[it] = 0xFFFFFFFF.toInt() }

        assertEquals(32.0, FrameLuminanceSampler.sampleArgb(pixels, 10, 10), 0.01)
    }

    private fun sample(color: Int): Double =
        FrameLuminanceSampler.sampleArgb(IntArray(100) { color }, 10, 10)
}
