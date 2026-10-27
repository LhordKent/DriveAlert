package com.lhordkent.drivealert.detection.frame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
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

    @Test
    fun `face crop excludes bright background from lighting measurements`() {
        val landmarks = listOf(NormalizedLandmark(.4, .3), NormalizedLandmark(.6, .3), NormalizedLandmark(.5, .7))
        val region = FrameLuminanceSampler.faceRegion(landmarks, 100, 100)
        assertNotNull(region)
        val pixels = IntArray(10_000) { index ->
            val x = index % 100
            val y = index / 100
            if (x in 40..59 && y in 30..69) 0xFF141414.toInt() else 0xFFFFFFFF.toInt()
        }
        val face = FrameLuminanceSampler.statisticsArgb(pixels, 100, 100, region!!)
        assertEquals(20.0, face.luminance, 0.01)
        assertEquals(0.0, face.contrast, 0.01)
    }

    @Test
    fun `percentile contrast ignores isolated highlights`() {
        val pixels = IntArray(100) { if (it < 50) 0xFF202020.toInt() else 0xFF606060.toInt() }
        pixels[99] = 0xFFFFFFFF.toInt()
        assertEquals(64.0, FrameLuminanceSampler.statisticsArgb(pixels, 10, 10).contrast, 0.01)
    }

    @Test
    fun `invalid tiny or offscreen face crops cannot establish lighting quality`() {
        assertNull(FrameLuminanceSampler.faceRegion(emptyList(), 100, 100))
        assertNull(FrameLuminanceSampler.faceRegion(List(3) { NormalizedLandmark(2.0, 2.0) }, 100, 100))
        assertNull(FrameLuminanceSampler.faceRegion(List(3) { NormalizedLandmark(Double.NaN, .5) }, 100, 100))
    }

    @Test
    fun `internal eye and mouth landmark movement does not change pixel based quality`() {
        val outline = listOf(NormalizedLandmark(.2, .2), NormalizedLandmark(.8, .2), NormalizedLandmark(.5, .8))
        val open = outline + listOf(NormalizedLandmark(.4, .4), NormalizedLandmark(.6, .5))
        val closed = outline + listOf(NormalizedLandmark(.4, .41), NormalizedLandmark(.6, .49))
        val pixels = IntArray(10_000) { 0xFF606060.toInt() }
        val openStats = FrameLuminanceSampler.statisticsArgb(pixels, 100, 100, FrameLuminanceSampler.faceRegion(open, 100, 100)!!)
        val closedStats = FrameLuminanceSampler.statisticsArgb(pixels, 100, 100, FrameLuminanceSampler.faceRegion(closed, 100, 100)!!)
        assertEquals(openStats, closedStats)
    }
}
