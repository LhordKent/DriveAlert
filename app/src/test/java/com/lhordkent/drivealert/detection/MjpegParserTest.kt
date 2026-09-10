package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.detection.frame.MjpegParser
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MjpegParserTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun parsesContentLengthFrameAcrossFragmentedReads() {
        val payload = multipart("Content-Length: ${jpeg.size}\r\n", jpeg)
        val fragmented = object : FilterInputStream(ByteArrayInputStream(payload)) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = super.read(bytes, offset, minOf(2, length))
        }
        assertArrayEquals(jpeg, MjpegParser(fragmented, "frame").nextJpeg())
    }

    @Test
    fun parsesMarkerDelimitedFrameWithoutContentLength() {
        assertArrayEquals(jpeg, MjpegParser(ByteArrayInputStream(multipart("", jpeg)), "frame").nextJpeg())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedFrame() {
        MjpegParser(ByteArrayInputStream(multipart("Content-Length: ${jpeg.size}\r\n", jpeg)), "frame", 4).nextJpeg()
    }

    @Test
    fun extractsQuotedAndUnquotedBoundaries() {
        assertEquals("frame", MjpegParser.boundaryFrom("multipart/x-mixed-replace;boundary=frame"))
        assertEquals("frame", MjpegParser.boundaryFrom("multipart/x-mixed-replace; boundary=\"--frame\""))
        assertNull(MjpegParser.boundaryFrom("image/jpeg"))
    }

    private fun multipart(headers: String, frame: ByteArray): ByteArray =
        "--frame\r\nContent-Type: image/jpeg\r\n$headers\r\n".toByteArray() + frame + "\r\n".toByteArray()
}
