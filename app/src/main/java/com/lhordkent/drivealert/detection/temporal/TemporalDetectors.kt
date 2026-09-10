package com.lhordkent.drivealert.detection.temporal

import com.lhordkent.drivealert.detection.model.ConfirmedSignEvent
import com.lhordkent.drivealert.detection.model.SignType
import com.lhordkent.drivealert.detection.model.TemporalState
import java.util.ArrayDeque

class EyeClosureDetector(
    val threshold: Double,
    private val confirmationMs: Long = 2_000L,
    private val calibrationId: String = "unassigned",
    private val calibrationVersion: Int = 3,
) {
    init {
        require(threshold.isFinite() && threshold > 0.0)
        require(confirmationMs > 0)
    }

    var state: TemporalState = TemporalState.NORMAL
        private set
    private var candidateStartedAtMs: Long? = null
    private var lastTimestampMs: Long? = null

    fun update(timestampMs: Long, ear: Double?): ConfirmedSignEvent? {
        validateTimestamp(timestampMs)
        if (ear == null || !ear.isFinite()) {
            if (state == TemporalState.CANDIDATE) rearm()
            return null
        }
        if (ear >= threshold) {
            rearm()
            return null
        }
        if (state == TemporalState.NORMAL) {
            state = TemporalState.CANDIDATE
            candidateStartedAtMs = timestampMs
            return null
        }
        if (state == TemporalState.CONFIRMED) return null
        val duration = timestampMs - checkNotNull(candidateStartedAtMs)
        if (duration < confirmationMs) return null
        state = TemporalState.CONFIRMED
        return ConfirmedSignEvent(
            SignType.PROLONGED_EYE_CLOSURE,
            timestampMs,
            duration,
            ear,
            threshold,
            calibrationId,
            calibrationVersion,
        )
    }

    fun candidateDurationMs(): Long = if (state == TemporalState.CANDIDATE) {
        (lastTimestampMs ?: 0L) - (candidateStartedAtMs ?: 0L)
    } else 0L

    fun reset() = rearm()

    private fun rearm() {
        state = TemporalState.NORMAL
        candidateStartedAtMs = null
    }

    private fun validateTimestamp(timestampMs: Long) {
        require(lastTimestampMs == null || timestampMs >= checkNotNull(lastTimestampMs)) {
            "Detector timestamps must be monotonic."
        }
        lastTimestampMs = timestampMs
    }
}

class YawnDetector(
    val threshold: Double,
    private val confirmationMs: Long = 3_000L,
    private val calibrationId: String = "unassigned",
    private val calibrationVersion: Int = 3,
) {
    init {
        require(threshold.isFinite() && threshold > 0.0)
        require(confirmationMs > 0)
    }

    var state: TemporalState = TemporalState.NORMAL
        private set
    private var candidateStartedAtMs: Long? = null
    private var lastTimestampMs: Long? = null

    fun update(timestampMs: Long, mar: Double?): ConfirmedSignEvent? {
        validateTimestamp(timestampMs)
        if (mar == null || !mar.isFinite()) {
            if (state == TemporalState.CANDIDATE) rearm()
            return null
        }
        if (mar <= threshold) {
            rearm()
            return null
        }
        if (state == TemporalState.NORMAL) {
            state = TemporalState.CANDIDATE
            candidateStartedAtMs = timestampMs
            return null
        }
        if (state == TemporalState.CONFIRMED) return null
        val duration = timestampMs - checkNotNull(candidateStartedAtMs)
        if (duration < confirmationMs) return null
        state = TemporalState.CONFIRMED
        return ConfirmedSignEvent(
            SignType.YAWNING,
            timestampMs,
            duration,
            mar,
            threshold,
            calibrationId,
            calibrationVersion,
        )
    }

    fun candidateDurationMs(): Long = if (state == TemporalState.CANDIDATE) {
        (lastTimestampMs ?: 0L) - (candidateStartedAtMs ?: 0L)
    } else 0L

    fun reset() = rearm()

    private fun rearm() {
        state = TemporalState.NORMAL
        candidateStartedAtMs = null
    }

    private fun validateTimestamp(timestampMs: Long) {
        require(lastTimestampMs == null || timestampMs >= checkNotNull(lastTimestampMs)) {
            "Detector timestamps must be monotonic."
        }
        lastTimestampMs = timestampMs
    }
}

class HeadDownDetector(
    val thresholdDegrees: Double = 15.0,
    private val windowMs: Long = 3_000L,
    private val downwardRatioThreshold: Double = 0.80,
    private val calibrationId: String = "unassigned",
    private val calibrationVersion: Int = 3,
) {
    init {
        require(thresholdDegrees.isFinite() && thresholdDegrees > 0.0)
        require(windowMs > 0)
        require(downwardRatioThreshold > 0.0 && downwardRatioThreshold <= 1.0)
    }

    private data class Observation(val timestampMs: Long, val isDownward: Boolean)

    private val observations = ArrayDeque<Observation>()
    private var monitoringStartedAtMs: Long? = null
    private var lastTimestampMs: Long? = null
    var state: TemporalState = TemporalState.NORMAL
        private set

    val downwardRatio: Double?
        get() = if (observations.isEmpty()) null else observations.count { it.isDownward }.toDouble() / observations.size

    fun update(timestampMs: Long, relativeDownwardPitch: Double?): ConfirmedSignEvent? {
        validateTimestamp(timestampMs)
        expireOld(timestampMs)
        if (relativeDownwardPitch == null || !relativeDownwardPitch.isFinite()) return null

        if (monitoringStartedAtMs == null) monitoringStartedAtMs = timestampMs
        val isDownward = relativeDownwardPitch > thresholdDegrees
        observations.addLast(Observation(timestampMs, isDownward))
        val ratio = checkNotNull(downwardRatio)

        if (state == TemporalState.CONFIRMED) {
            if (!isDownward && ratio < downwardRatioThreshold) state = TemporalState.NORMAL
            return null
        }

        val windowIsMature = timestampMs - checkNotNull(monitoringStartedAtMs) >= windowMs && observations.size >= 2
        if (!windowIsMature || ratio < downwardRatioThreshold) {
            state = if (isDownward) TemporalState.CANDIDATE else TemporalState.NORMAL
            return null
        }

        state = TemporalState.CONFIRMED
        return ConfirmedSignEvent(
            type = SignType.HEAD_NODDING,
            timestampMs = timestampMs,
            qualifyingDurationMs = windowMs,
            measuredValue = relativeDownwardPitch,
            threshold = thresholdDegrees,
            calibrationId = calibrationId,
            calibrationVersion = calibrationVersion,
            temporalContext = mapOf(
                "downwardRatio" to ratio,
                "validObservationCount" to observations.size.toDouble(),
                "windowMs" to windowMs.toDouble(),
            ),
        )
    }

    fun reset() {
        state = TemporalState.NORMAL
        observations.clear()
        monitoringStartedAtMs = null
    }

    private fun expireOld(timestampMs: Long) {
        val cutoff = timestampMs - windowMs
        while (observations.isNotEmpty() && observations.first.timestampMs < cutoff) {
            observations.removeFirst()
        }
    }

    private fun validateTimestamp(timestampMs: Long) {
        require(lastTimestampMs == null || timestampMs >= checkNotNull(lastTimestampMs)) {
            "Detector timestamps must be monotonic."
        }
        lastTimestampMs = timestampMs
    }
}
