package com.lhordkent.drivealert.detection.frame

/**
 * A source-neutral decoded-frame boundary. The production implementation receives the
 * classic ESP32-CAM stream; numeric/test sources can implement it without entering the engine.
 */
interface DriverFrameSource<T> {
    fun start(onFrame: (FramePacket<T>) -> Unit)
    fun stop()
}

/**
 * Production-source contract for decoded frames received from the classic ESP32-CAM.
 * The transport and decoder are intentionally unspecified until the firmware protocol exists.
 */
interface Esp32VideoFrameSource<T> : DriverFrameSource<T>

data class FramePacket<T>(
    val payload: T,
    val width: Int,
    val height: Int,
    /** Monotonic capture timestamp. */
    val timestampMs: Long,
    val release: () -> Unit = {},
)

/**
 * Accepts at most one monotonic timestamp per interval. This keeps preview and
 * inference work bounded without queueing old camera frames.
 */
class MonotonicFrameGate(private val minimumIntervalMs: Long) {
    init {
        require(minimumIntervalMs >= 0) { "Frame interval must not be negative." }
    }

    private var lastAcceptedTimestampMs: Long? = null

    @Synchronized
    fun tryAcquire(timestampMs: Long): Boolean {
        val previous = lastAcceptedTimestampMs
        if (previous != null && timestampMs >= previous && timestampMs - previous < minimumIntervalMs) {
            return false
        }
        lastAcceptedTimestampMs = timestampMs
        return true
    }

    @Synchronized
    fun reset() {
        lastAcceptedTimestampMs = null
    }
}
