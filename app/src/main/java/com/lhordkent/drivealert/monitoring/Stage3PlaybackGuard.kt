package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.postauth.WarningStage

/** Coalesce warning requests while the accepted mixed warning is still speaking.
 * Scoped to the physical camera, including across monitoring-session restarts. */
internal class Stage3PlaybackGuard {
    private val protectedUntilMs = mutableMapOf<String, Long>()

    fun accepted(cameraId: String, stage: WarningStage, nowMs: Long) {
        if (stage != WarningStage.STAGE_3) return
        protectedUntilMs.entries.removeAll { it.value <= nowMs }
        protectedUntilMs[cameraId] = nowMs + 10_300L
    }

    fun remainingMs(cameraId: String?, nowMs: Long): Long =
        (protectedUntilMs[cameraId]?.minus(nowMs) ?: 0L).coerceAtLeast(0L)
}
