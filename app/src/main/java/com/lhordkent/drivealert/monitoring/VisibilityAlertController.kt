package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.postauth.PreferredVolume

enum class VisibilityIssue {
    LOWER_FACE_OBSTRUCTED,
    EYE_REGION_OBSTRUCTED,
    BOTH_REGIONS_OBSTRUCTED,
    FACE_UNAVAILABLE,
    INSUFFICIENT_LIGHTING,
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
    data class Notify(val issue: VisibilityIssue, val announce: Boolean = true) : VisibilityAlertEffect
    data object Clear : VisibilityAlertEffect
}

enum class VisibilitySpeechIntent { EYES, MOUTH, FACE, LIGHT, MOVEMENT }

data class VisibilityOutputCommand(
    val intent: VisibilitySpeechIntent,
    val volume: PreferredVolume,
) {
    constructor(issue: VisibilityIssue, volume: PreferredVolume) : this(
        when (issue) {
            VisibilityIssue.EYE_REGION_OBSTRUCTED -> VisibilitySpeechIntent.EYES
            VisibilityIssue.LOWER_FACE_OBSTRUCTED -> VisibilitySpeechIntent.MOUTH
            VisibilityIssue.BOTH_REGIONS_OBSTRUCTED, VisibilityIssue.FACE_UNAVAILABLE -> VisibilitySpeechIntent.FACE
            VisibilityIssue.INSUFFICIENT_LIGHTING -> VisibilitySpeechIntent.LIGHT
        }, volume,
    )
}

fun interface VisibilityOutputGateway {
    suspend fun activate(command: VisibilityOutputCommand): WarningDeliveryStatus
}

suspend fun VisibilityOutputGateway.activateSafely(command: VisibilityOutputCommand): WarningDeliveryStatus =
    runCatching { activate(command) }.getOrElse { WarningDeliveryStatus.FAILED }

/** Pure monotonic-time controller for sustained visibility episodes and session acknowledgements. */
class VisibilityAlertController(
    private val lowerFaceDelayMs: Long = 3_000L,
    private val faceUnavailableDelayMs: Long = 3_000L,
    private val recoveryDelayMs: Long = 2_000L,
    private val reminderIntervalMs: Long = 30_000L,
    private val lightingDelayMs: Long = 3_000L,
) {
    private var activeIssue: VisibilityIssue? = null
    private var candidateStartedAtMs: Long? = null
    private var recoveryStartedAtMs: Long? = null
    private var notified = false
    private var lastNotifiedAtMs: Long? = null
    private var lastReportedIssue: VisibilityIssue? = null
    private var acceptedLowerFace = false
    private var acceptedEyeRegion = false
    private var lightingEpisodeStartedAtMs: Long? = null

    val reportedIssue: VisibilityIssue?
        get() = lastReportedIssue

    init {
        require(lowerFaceDelayMs >= 0L)
        require(faceUnavailableDelayMs >= 0L)
        require(recoveryDelayMs >= 0L)
        require(reminderIntervalMs > 0L)
        require(lightingDelayMs >= 0L)
    }

    fun update(
        timestampMs: Long,
        issue: VisibilityIssue?,
        lighting: FaceLightingAssessment? = null,
    ): List<VisibilityAlertEffect> {
        if (issue == null) {
            if (activeIssue == VisibilityIssue.INSUFFICIENT_LIGHTING && lighting != null &&
                !lighting.hasFreshGoodSample(timestampMs)) {
                // Checking a new dark interval or waiting for samples is not
                // observed recovery from the previously reported episode.
                pauseLightingQualification()
                recoveryStartedAtMs = null
                return emptyList()
            }
            return recover(timestampMs)
        }
        if (issue == VisibilityIssue.INSUFFICIENT_LIGHTING && lighting != null &&
            !lighting.hasConfirmedPoorLighting(timestampMs)) return update(timestampMs, null, lighting)
        if (isAccepted(issue)) {
            val shouldClear = lastReportedIssue != null
            clearEpisode()
            return if (shouldClear) listOf(VisibilityAlertEffect.Clear) else emptyList()
        }
        recoveryStartedAtMs = null
        // Lighting qualifies from new facial samples, never elapsed missing-face
        // time or repeated cached samples. Face/obstruction delays stay unchanged.
        val evidenceAtMs = if (issue == VisibilityIssue.INSUFFICIENT_LIGHTING)
            lighting?.timestampMs ?: timestampMs else timestampMs
        val newLightingEpisode = issue == VisibilityIssue.INSUFFICIENT_LIGHTING &&
            lightingEpisodeStartedAtMs != lighting?.poorSinceMs
        if (issue != activeIssue || candidateStartedAtMs == null || newLightingEpisode) {
            activeIssue = issue
            candidateStartedAtMs = evidenceAtMs
            notified = false
            lightingEpisodeStartedAtMs = lighting?.poorSinceMs.takeIf { issue == VisibilityIssue.INSUFFICIENT_LIGHTING }
        }
        val requiredDelay = when (issue) {
            VisibilityIssue.LOWER_FACE_OBSTRUCTED -> lowerFaceDelayMs
            VisibilityIssue.EYE_REGION_OBSTRUCTED -> lowerFaceDelayMs
            VisibilityIssue.BOTH_REGIONS_OBSTRUCTED -> faceUnavailableDelayMs
            VisibilityIssue.FACE_UNAVAILABLE -> faceUnavailableDelayMs
            VisibilityIssue.INSUFFICIENT_LIGHTING -> lightingDelayMs
        }
        val startedAt = checkNotNull(candidateStartedAtMs)
        if (!notified && evidenceAtMs - startedAt >= requiredDelay) {
            notified = true
            lastReportedIssue = issue
            // A changed attribution is still the same uninterrupted visibility
            // problem. Update its UI, but do not restart spoken reminders.
            val announce = lastNotifiedAtMs?.let { evidenceAtMs - it >= reminderIntervalMs } ?: true
            if (announce) lastNotifiedAtMs = evidenceAtMs
            return listOf(VisibilityAlertEffect.Notify(issue, announce))
        }
        val lastNotified = lastNotifiedAtMs
        if (notified && lastNotified != null && evidenceAtMs - lastNotified >= reminderIntervalMs) {
            lastNotifiedAtMs = evidenceAtMs
            lastReportedIssue = issue
            return listOf(VisibilityAlertEffect.Notify(issue))
        }
        return emptyList()
    }

    fun reset() {
        clearEpisode()
        acceptedLowerFace = false
        acceptedEyeRegion = false
    }

    /** Forget a disconnected-camera episode while retaining session accessory acknowledgements. */
    fun interrupt() = clearEpisode()

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
            VisibilityIssue.FACE_UNAVAILABLE, VisibilityIssue.INSUFFICIENT_LIGHTING -> return
        }
        clearEpisode()
    }

    private fun isAccepted(issue: VisibilityIssue): Boolean = when (issue) {
        VisibilityIssue.LOWER_FACE_OBSTRUCTED -> acceptedLowerFace
        VisibilityIssue.EYE_REGION_OBSTRUCTED -> acceptedEyeRegion
        VisibilityIssue.BOTH_REGIONS_OBSTRUCTED -> acceptedLowerFace && acceptedEyeRegion
        VisibilityIssue.FACE_UNAVAILABLE, VisibilityIssue.INSUFFICIENT_LIGHTING -> false
    }

    private fun clearEpisode() {
        activeIssue = null
        candidateStartedAtMs = null
        recoveryStartedAtMs = null
        notified = false
        lastNotifiedAtMs = null
        lastReportedIssue = null
        lightingEpisodeStartedAtMs = null
    }

    private fun recover(timestampMs: Long): List<VisibilityAlertEffect> {
        if (activeIssue == null) return emptyList()
        if (activeIssue == VisibilityIssue.INSUFFICIENT_LIGHTING) {
            // Keep the reported episode during recovery, but a new dark interval
            // must qualify again even if the notification has not cleared yet.
            pauseLightingQualification()
        }
        if (lastReportedIssue == null) {
            clearEpisode()
            return emptyList()
        }
        val recoveryStart = recoveryStartedAtMs ?: timestampMs.also { recoveryStartedAtMs = it }
        if (timestampMs - recoveryStart < recoveryDelayMs) return emptyList()
        clearEpisode()
        return listOf(VisibilityAlertEffect.Clear)
    }

    private fun pauseLightingQualification() {
        candidateStartedAtMs = null
        lightingEpisodeStartedAtMs = null
        notified = false
    }
}
