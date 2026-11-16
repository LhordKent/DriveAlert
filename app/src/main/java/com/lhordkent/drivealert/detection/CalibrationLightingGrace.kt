package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.monitoring.FaceLightingAssessment

/** Bridges only absent lighting samples, never missing face measurements or measured darkness. */
internal class CalibrationLightingGrace {
    private var lastUsableSampleMs: Long? = null

    fun allowsAssessment(nowMs: Long, faceTimestampMs: Long?, lighting: FaceLightingAssessment): Boolean {
        if (faceTimestampMs == null || nowMs - faceTimestampMs !in 0L..FaceLightingAssessment.FRESHNESS_MS) {
            reset()
            return false
        }
        if (lighting.allowsAssessment(nowMs)) {
            lastUsableSampleMs = lighting.timestampMs
            return true
        }
        if (lighting.timestampMs != null || lighting.poorSinceMs != null || lighting.recovering) {
            reset()
            return false
        }
        return lastUsableSampleMs?.let { nowMs - it in 0L..2_000L } == true
    }

    fun reset() { lastUsableSampleMs = null }
}
