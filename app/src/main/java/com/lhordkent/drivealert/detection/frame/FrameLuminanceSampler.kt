package com.lhordkent.drivealert.detection.frame

import android.graphics.Bitmap
import kotlin.math.ceil

object FrameLuminanceSampler {
    private const val CENTER_FRACTION = 0.60
    private const val TRIM_FRACTION = 0.10
    private const val TARGET_SAMPLE_COUNT = 2_500

    fun sample(bitmap: Bitmap): Double {
        val cropWidth = (bitmap.width * CENTER_FRACTION).toInt().coerceAtLeast(1)
        val cropHeight = (bitmap.height * CENTER_FRACTION).toInt().coerceAtLeast(1)
        val left = (bitmap.width - cropWidth) / 2
        val top = (bitmap.height - cropHeight) / 2
        val pixels = IntArray(cropWidth * cropHeight)
        bitmap.getPixels(pixels, 0, cropWidth, left, top, cropWidth, cropHeight)
        return sampleArgb(pixels, cropWidth, cropHeight)
    }

    internal fun sampleArgb(pixels: IntArray, width: Int, height: Int): Double {
        require(width > 0 && height > 0 && pixels.size >= width * height)
        val step = ceil(kotlin.math.sqrt((width * height).toDouble() / TARGET_SAMPLE_COUNT)).toInt().coerceAtLeast(1)
        val values = ArrayList<Int>((width / step + 1) * (height / step + 1))
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = pixels[y * width + x]
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
        return sum.toDouble() / (until - from)
    }
}
