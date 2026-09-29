package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.postauth.PreferredVolume

enum class VisibilityIssue {
    LOWER_FACE_OBSTRUCTED,
    EYE_REGION_OBSTRUCTED,
    BOTH_REGIONS_OBSTRUCTED,
    FACE_UNAVAILABLE,
}

enum class DriverAccessoryMode(
    val label: String,
    val monitoringSummary: String,
    internal val declaresLowerFaceObstruction: Boolean,
    internal val declaresEyeRegionObstruction: Boolean,
) {
    NONE("No obstruction", "Eye, yawning, and head monitoring are expected to remain available.", false, false),
    EYEGLASSES("Ordinary eyeglasses", "Full monitoring remains enabled unless the camera reports unreliable visibility.", false, false),
    MASK("Face mask", "Yawning monitoring starts unavailable; eye and head monitoring remain active.", true, false),
    SUNGLASSES("Sunglasses", "Eye-closure monitoring starts unavailable; yawning and head monitoring remain active.", false, true),
    MASK_AND_SUNGLASSES("Mask and sunglasses", "Monitoring pauses because fewer than two reliable signs remain.", true, true),
}

internal fun DriverAccessoryMode.declaredIssue(): VisibilityIssue? = when {
    declaresLowerFaceObstruction && declaresEyeRegionObstruction -> VisibilityIssue.BOTH_REGIONS_OBSTRUCTED
    declaresLowerFaceObstruction -> VisibilityIssue.LOWER_FACE_OBSTRUCTED
    declaresEyeRegionObstruction -> VisibilityIssue.EYE_REGION_OBSTRUCTED
    else -> null
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

/** Pure monotonic-time controller for sustained visibility episodes and session acknowledgements. */
class VisibilityAlertController(
    private val lowerFaceDelayMs: Long = 2_000L,
    private val faceUnavailableDelayMs: Long = 1_000L,
    private val recoveryDelayMs: Long = 1_000L,
    private val reminderIntervalMs: Long = 30 * 60_000L,
) {
    private var activeIssue: VisibilityIssue? = null
    private var candidateStartedAtMs: Long? = null
    private var recoveryStartedAtMs: Long? = null
    private var notified = false
    private var lastNotifiedAtMs: Long? = null
    private var acceptedLowerFace = false
    private var acceptedEyeRegion = false

    init {
        require(lowerFaceDelayMs >= 0L)
        require(faceUnavailableDelayMs >= 0L)
        require(recoveryDelayMs >= 0L)
        require(reminderIntervalMs > 0L)
    }

    fun update(timestampMs: Long, issue: VisibilityIssue?): List<VisibilityAlertEffect> {
        if (issue == null) return recover(timestampMs)
        if (isAccepted(issue)) {
            val shouldClear = notified
            clearEpisode()
            return if (shouldClear) listOf(VisibilityAlertEffect.Clear) else emptyList()
        }
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
        clearEpisode()
        acceptedLowerFace = false
        acceptedEyeRegion = false
    }

    fun beginSession(accessoryMode: DriverAccessoryMode) {
        reset()
        acceptedLowerFace = accessoryMode.declaresLowerFaceObstruction
        acceptedEyeRegion = accessoryMode.declaresEyeRegionObstruction
    }

    fun acknowledge(issue: VisibilityIssue) {
        when (issue) {
            VisibilityIssue.LOWER_FACE_OBSTRUCTED -> acceptedLowerFace = true
            VisibilityIssue.EYE_REGION_OBSTRUCTED -> acceptedEyeRegion = true
            VisibilityIssue.BOTH_REGIONS_OBSTRUCTED -> {
                acceptedLowerFace = true
                acceptedEyeRegion = true
            }
            VisibilityIssue.FACE_UNAVAILABLE -> return
        }
        clearEpisode()
    }

    private fun isAccepted(issue: VisibilityIssue): Boolean = when (issue) {
        VisibilityIssue.LOWER_FACE_OBSTRUCTED -> acceptedLowerFace
        VisibilityIssue.EYE_REGION_OBSTRUCTED -> acceptedEyeRegion
        VisibilityIssue.BOTH_REGIONS_OBSTRUCTED -> acceptedLowerFace && acceptedEyeRegion
        VisibilityIssue.FACE_UNAVAILABLE -> false
    }

    private fun clearEpisode() {
        activeIssue = null
        candidateStartedAtMs = null
        recoveryStartedAtMs = null
        notified = false
        lastNotifiedAtMs = null
    }

    private fun recover(timestampMs: Long): List<VisibilityAlertEffect> {
        if (activeIssue == null) return emptyList()
        if (!notified) {
            clearEpisode()
            return emptyList()
        }
        val recoveryStart = recoveryStartedAtMs ?: timestampMs.also { recoveryStartedAtMs = it }
        if (timestampMs - recoveryStart < recoveryDelayMs) return emptyList()
        clearEpisode()
        return listOf(VisibilityAlertEffect.Clear)
    }
}
