package com.lhordkent.drivealert.detection.frame

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SharedBitmapFrame private constructor(val bitmap: Bitmap) {
    private val references = AtomicInteger(1)
    fun retain(): SharedBitmapFrame {
        return tryRetain() ?: error("Frame has already been released.")
    }
    fun tryRetain(): SharedBitmapFrame? {
        while (true) {
            val current = references.get()
            if (current <= 0) return null
            if (references.compareAndSet(current, current + 1)) return this
        }
    }
    fun release() {
        val remaining = references.decrementAndGet()
        check(remaining >= 0) { "Frame released more than once." }
        // Do not call Bitmap.recycle(). Compose records the bitmap into an
        // asynchronous render display list that can outlive composition-level
        // ownership. Once references reach zero, normal Android GC safely reclaims
        // the pixels after Compose/RenderThread no longer reference the bitmap.
    }
    companion object { fun create(bitmap: Bitmap) = SharedBitmapFrame(bitmap) }
}

enum class StreamConnectionState { SETUP_REQUIRED, READY, CONNECTING, CONNECTED, RECONNECTING, UNAVAILABLE }

class Esp32MjpegFrameSource(
    private val streamUrl: String,
    private val onState: (StreamConnectionState, String?) -> Unit,
    private val onDisconnected: (Throwable?) -> Unit,
    previewFramesPerSecond: Int = DEFAULT_PREVIEW_FRAMES_PER_SECOND,
) : Esp32VideoFrameSource<SharedBitmapFrame> {
    private val decodeGate = MonotonicFrameGate(1_000L / previewFramesPerSecond.coerceIn(1, 30))
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "drivealert-mjpeg") }
    private val stopped = AtomicBoolean(true)
    @Volatile private var connection: HttpURLConnection? = null

    override fun start(onFrame: (FramePacket<SharedBitmapFrame>) -> Unit) {
        if (!stopped.compareAndSet(true, false)) return
        executor.execute {
            var failure: Throwable? = null
            try {
                onState(StreamConnectionState.CONNECTING, null)
                val active = (URL(streamUrl).openConnection() as HttpURLConnection).also {
                    it.connectTimeout = 4_000
                    it.readTimeout = 10_000
                    it.useCaches = false
                    it.setRequestProperty("Accept", "multipart/x-mixed-replace")
                }
                connection = active
                active.connect()
                check(active.responseCode in 200..299) { "Camera returned HTTP ${active.responseCode}." }
                val boundary = MjpegParser.boundaryFrom(active.contentType)
                    ?: error("Camera response did not include an MJPEG boundary.")
                val parser = MjpegParser(active.inputStream, boundary)
                onState(StreamConnectionState.CONNECTED, null)
                while (!stopped.get()) {
                    val jpeg = parser.nextJpeg()
                    val timestampMs = SystemClock.elapsedRealtime()
                    if (!decodeGate.tryAcquire(timestampMs)) continue
                    val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                        ?: throw IllegalArgumentException("Camera sent an undecodable JPEG frame.")
                    val shared = SharedBitmapFrame.create(bitmap)
                    onFrame(
                        FramePacket(
                            payload = shared,
                            width = bitmap.width,
                            height = bitmap.height,
                            timestampMs = timestampMs,
                            release = shared::release,
                        ),
                    )
                }
            } catch (throwable: Throwable) {
                if (!stopped.get()) failure = throwable
            } finally {
                connection?.disconnect()
                connection = null
                if (!stopped.get()) onDisconnected(failure)
                executor.shutdown()
            }
        }
    }

    override fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        connection?.disconnect()
        executor.shutdownNow()
    }

    companion object {
        const val DEFAULT_PREVIEW_FRAMES_PER_SECOND = 15
    }
}
