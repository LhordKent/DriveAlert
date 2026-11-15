package com.lhordkent.drivealert.detection

/** Source age gates individual results; delivery silence gates a stalled pipeline.
 * Adding inference latency to the silence timer prematurely erased valid evidence. */
internal class CameraResultWatchdog(private val timeoutMs: Long) {
    private var lastCompletedAtMs: Long? = null

    fun accept(capturedAtMs: Long, completedAtMs: Long): Boolean {
        if (completedAtMs - capturedAtMs !in 0L..timeoutMs) return false
        lastCompletedAtMs = completedAtMs
        return true
    }

    fun expired(nowMs: Long): Boolean = lastCompletedAtMs?.let { nowMs - it > timeoutMs } == true

    fun reset() { lastCompletedAtMs = null }
}
