package com.lhordkent.drivealert.detection.frame

import android.graphics.Bitmap
import kotlin.math.ceil
import kotlin.math.floor
import com.lhordkent.drivealert.detection.model.NormalizedLandmark
import com.lhordkent.drivealert.monitoring.FaceLightingSample

object FrameLuminanceSampler {
    private const val CENTER_FRACTION = 0.60
    private const val TRIM_FRACTION = 0.10
    private const val TARGET_SAMPLE_COUNT = 2_500

    fun sample(bitmap: Bitmap): Double {
        val cropWidth = (bitmap.width * CENTER_FRACTION).toInt().coerceAtLeast(1)
        val cropHeight = (bitmap.height * CENTER_FRACTION).toInt().coerceAtLeast(1)
        val left = (bitmap.width - cropWidth) / 2
        val top = (bitmap.height - cropHeight) / 2
        return statistics(cropWidth, cropHeight) { x, y -> bitmap.getPixel(left + x, top + y) }.luminance
    }

    fun sampleFace(bitmap: Bitmap, landmarks: List<NormalizedLandmark>, timestampMs: Long): FaceLightingSample? {
        val region = faceRegion(landmarks, bitmap.width, bitmap.height) ?: return null
        val stats = statistics(region.width, region.height) { x, y -> bitmap.getPixel(region.left + x, region.top + y) }
        return FaceLightingSample(timestampMs, stats.luminance, stats.contrast)
    }

    internal data class Region(val left: Int, val top: Int, val width: Int, val height: Int)
    internal data class Statistics(val luminance: Double, val contrast: Double)

    internal fun faceRegion(landmarks: List<NormalizedLandmark>, width: Int, height: Int): Region? {
        if (width <= 0 || height <= 0 || landmarks.size < 3 || landmarks.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val left = floor(landmarks.minOf { it.x }.coerceIn(0.0, 1.0) * width).toInt()
        val top = floor(landmarks.minOf { it.y }.coerceIn(0.0, 1.0) * height).toInt()
        val right = ceil(landmarks.maxOf { it.x }.coerceIn(0.0, 1.0) * width).toInt()
        val bottom = ceil(landmarks.maxOf { it.y }.coerceIn(0.0, 1.0) * height).toInt()
        return if (right - left < 16 || bottom - top < 16) null else Region(left, top, right - left, bottom - top)
    }

    internal fun sampleArgb(pixels: IntArray, width: Int, height: Int): Double =
        statisticsArgb(pixels, width, height).luminance

    internal fun statisticsArgb(pixels: IntArray, width: Int, height: Int, region: Region = Region(0, 0, width, height)): Statistics {
        require(width > 0 && height > 0 && pixels.size >= width * height)
        require(region.left >= 0 && region.top >= 0 && region.left + region.width <= width && region.top + region.height <= height)
        return statistics(region.width, region.height) { x, y -> pixels[(region.top + y) * width + region.left + x] }
    }

    private inline fun statistics(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Statistics {
        require(width > 0 && height > 0)
        val step = ceil(kotlin.math.sqrt((width * height).toDouble() / TARGET_SAMPLE_COUNT)).toInt().coerceAtLeast(1)
        val values = ArrayList<Int>((width / step + 1) * (height / step + 1))
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = pixelAt(x, y)
                val red = color shr 16 and 0xff
                val green = color shr 8 and 0xff
                val blue = color and 0xff
                values += (77 * red + 150 * green + 29 * blue) shr 8
                x += step
            }
            y += step
        }
        values.sort()
        val trim = (values.size * TRIM_FRACTION).toInt()
        val from = trim.coerceAtMost(values.lastIndex)
        val until = (values.size - trim).coerceAtLeast(from + 1)
        var sum = 0L
        for (index in from until until) sum += values[index]
        val p10 = values[((values.size - 1) * 0.10).toInt()]
        val p90 = values[((values.size - 1) * 0.90).toInt()]
        return Statistics(sum.toDouble() / (until - from), (p90 - p10).toDouble())
    }
}
