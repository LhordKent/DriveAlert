package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.postauth.VisibleSign

/** Reminders are delivery requests, never new detection events. Updated only from valid observations. */
internal class PersistentWarningRepeater {
    private val nextReminderAtMs = mutableMapOf<VisibleSign, Long>()

    fun reset() = nextReminderAtMs.clear()

    fun update(nowMs: Long, confirmedSigns: Set<VisibleSign>): Set<VisibleSign> {
        nextReminderAtMs.keys.retainAll(confirmedSigns)
        return confirmedSigns.filterTo(linkedSetOf()) { sign ->
            val interval = intervalMs(sign)
            val due = nextReminderAtMs.getOrPut(sign) { nowMs + interval }
            if (nowMs < due) false else {
                // Skip missed slots instead of replaying a backlog after a delayed frame.
                nextReminderAtMs[sign] = due + ((nowMs - due) / interval + 1) * interval
                true
            }
        }
    }

    /** An initial/stage-change warning also satisfies any nearby reminder. */
    fun warningActivated(nowMs: Long) {
        nextReminderAtMs.keys.toList().forEach { nextReminderAtMs[it] = nowMs + intervalMs(it) }
    }

    private fun intervalMs(sign: VisibleSign): Long = when (sign) {
        VisibleSign.PROLONGED_EYE_CLOSURE -> 2_000L
        VisibleSign.YAWNING, VisibleSign.HEAD_NODDING -> 3_000L
    }
}
