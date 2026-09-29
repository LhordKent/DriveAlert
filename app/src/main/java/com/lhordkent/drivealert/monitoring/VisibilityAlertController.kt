package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.postauth.PreferredVolume

enum class VisibilityIssue {
    LOWER_FACE_OBSTRUCTED,
    EYE_REGION_OBSTRUCTED,
    BOTH_REGIONS_OBSTRUCTED,
    FACE_UNAVAILABLE,
}

sealed interface VisibilityAlertEffect {
    data class Notify(val issue: VisibilityIssue) : VisibilityAlertEffect
    data object Clear : VisibilityAlertEffect
}

data class VisibilityOutputCommand(
    val issue: VisibilityIssue,
    val volume: PreferredVolume,
)

fun interface VisibilityOutputGateway {
    suspend fun activate(command: VisibilityOutputCommand): WarningDeliveryStatus
}

suspend fun VisibilityOutputGateway.activateSafely(command: VisibilityOutputCommand): WarningDeliveryStatus =
    runCatching { activate(command) }.getOrElse { WarningDeliveryStatus.FAILED }

/** Pure monotonic-time controller for one alert per sustained visibility episode. */
class VisibilityAlertController(
    private val lowerFaceDelayMs: Long = 2_000L,
    private val faceUnavailableDelayMs: Long = 1_000L,
    private val recoveryDelayMs: Long = 1_000L,
    private val reminderIntervalMs: Long = 30_000L,
) {
    private var activeIssue: VisibilityIssue? = null
    private var candidateStartedAtMs: Long? = null
    private var recoveryStartedAtMs: Long? = null
    private var notified = false
    private var lastNotifiedAtMs: Long? = null

    init {
        require(lowerFaceDelayMs >= 0L)
        require(faceUnavailableDelayMs >= 0L)
        require(recoveryDelayMs >= 0L)
        require(reminderIntervalMs > 0L)
    }

    fun update(timestampMs: Long, issue: VisibilityIssue?): List<VisibilityAlertEffect> {
        if (issue == null) return recover(timestampMs)
        recoveryStartedAtMs = null
        if (issue != activeIssue) {
            activeIssue = issue
            candidateStartedAtMs = timestampMs
            notified = false
            lastNotifiedAtMs = null
        }
        val requiredDelay = when (issue) {
            VisibilityIssue.LOWER_FACE_OBSTRUCTED -> lowerFaceDelayMs
            VisibilityIssue.EYE_REGION_OBSTRUCTED -> lowerFaceDelayMs
            VisibilityIssue.BOTH_REGIONS_OBSTRUCTED -> faceUnavailableDelayMs
            VisibilityIssue.FACE_UNAVAILABLE -> faceUnavailableDelayMs
        }
        val startedAt = checkNotNull(candidateStartedAtMs)
        if (!notified && timestampMs - startedAt >= requiredDelay) {
            notified = true
            lastNotifiedAtMs = timestampMs
            return listOf(VisibilityAlertEffect.Notify(issue))
        }
        val lastNotified = lastNotifiedAtMs
        if (notified && lastNotified != null && timestampMs - lastNotified >= reminderIntervalMs) {
            lastNotifiedAtMs = timestampMs
            return listOf(VisibilityAlertEffect.Notify(issue))
        }
        return emptyList()
    }

    fun reset() {
        activeIssue = null
        candidateStartedAtMs = null
        recoveryStartedAtMs = null
        notified = false
        lastNotifiedAtMs = null
    }

    private fun recover(timestampMs: Long): List<VisibilityAlertEffect> {
        if (activeIssue == null) return emptyList()
        if (!notified) {
            reset()
            return emptyList()
        }
        val recoveryStart = recoveryStartedAtMs ?: timestampMs.also { recoveryStartedAtMs = it }
        if (timestampMs - recoveryStart < recoveryDelayMs) return emptyList()
        reset()
        return listOf(VisibilityAlertEffect.Clear)
    }
}
