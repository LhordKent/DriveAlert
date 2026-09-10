package com.lhordkent.drivealert.detection.frame

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream

class MjpegParser(
    input: InputStream,
    private val boundary: String,
    private val maxFrameBytes: Int = 2 * 1024 * 1024,
) {
    private val input = if (input is BufferedInputStream) input else BufferedInputStream(input, 32 * 1024)

    fun nextJpeg(): ByteArray {
        var line: String
        do {
            line = readLine()
        } while (!line.startsWith("--$boundary"))

        var contentLength: Int? = null
        while (true) {
            line = readLine()
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            if (line.substring(0, separator).trim().equals("Content-Length", ignoreCase = true)) {
                contentLength = line.substring(separator + 1).trim().toIntOrNull()
            }
        }

        return contentLength?.let(::readSizedJpeg) ?: readMarkerDelimitedJpeg()
    }

    private fun readSizedJpeg(size: Int): ByteArray {
        require(size in 4..maxFrameBytes) { "MJPEG frame size is invalid: $size" }
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(bytes, offset, size - offset)
            if (count < 0) throw EOFException("MJPEG frame ended early.")
            offset += count
        }
        require(bytes[0] == JPEG_START_1 && bytes[1] == JPEG_START_2 &&
            bytes[size - 2] == JPEG_END_1 && bytes[size - 1] == JPEG_END_2
        ) { "MJPEG part is not a complete JPEG image." }
        return bytes
    }

    private fun readMarkerDelimitedJpeg(): ByteArray {
        val output = java.io.ByteArrayOutputStream(64 * 1024)
        var previous = -1
        var started = false
        while (output.size() <= maxFrameBytes) {
            val current = input.read()
            if (current < 0) throw EOFException("MJPEG stream ended before a JPEG completed.")
            if (!started) {
                if (previous == 0xFF && current == 0xD8) {
                    output.write(0xFF)
                    output.write(0xD8)
                    started = true
                }
            } else {
                output.write(current)
                if (previous == 0xFF && current == 0xD9) return output.toByteArray()
            }
            previous = current
        }
        throw IllegalArgumentException("MJPEG frame exceeded $maxFrameBytes bytes.")
    }

    private fun readLine(): String {
        val bytes = java.io.ByteArrayOutputStream(128)
        while (bytes.size() <= MAX_HEADER_LINE_BYTES) {
            val value = input.read()
            if (value < 0) throw EOFException("MJPEG stream ended while reading headers.")
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.write(value)
        }
        require(bytes.size() <= MAX_HEADER_LINE_BYTES) { "MJPEG header line is too large." }
        return bytes.toString(Charsets.US_ASCII.name())
    }

    companion object {
        private const val MAX_HEADER_LINE_BYTES = 8 * 1024
        private const val JPEG_START_1: Byte = -1
        private const val JPEG_START_2: Byte = -40
        private const val JPEG_END_1: Byte = -1
        private const val JPEG_END_2: Byte = -39

        fun boundaryFrom(contentType: String?): String? {
            val value = contentType?.substringAfter("boundary=", missingDelimiterValue = "")
                ?.substringBefore(';')?.trim()?.trim('"')?.removePrefix("--")
            return value?.takeIf(String::isNotBlank)
        }
    }
}
