package com.lhordkent.drivealert.detection.calibration

import com.lhordkent.drivealert.detection.measurement.FacialMeasurementCalculator
import com.lhordkent.drivealert.detection.model.CalibrationPhase
import com.lhordkent.drivealert.detection.model.CalibrationProgress
import com.lhordkent.drivealert.detection.model.CalibrationResult
import com.lhordkent.drivealert.detection.model.CalibrationStatus
import com.lhordkent.drivealert.detection.model.FacialMeasurements

class CalibrationSession(
    private val requiredDurationMs: Long = 5_000L,
) {
    init {
        require(requiredDurationMs > 0) { "Calibration duration must be positive." }
    }

    private val phaseOrder = listOf(
        CalibrationPhase.NEUTRAL,
        CalibrationPhase.EYES_CLOSED,
        CalibrationPhase.MOUTH_OPEN,
        CalibrationPhase.HEAD_DOWN,
    )
    private val samples = mutableMapOf<CalibrationPhase, PhaseSamples>()
    private var validElapsedMs = 0L
    private var lastValidTimestampMs: Long? = null
    private var neutralPitch: Double? = null
    private var downwardPitchMultiplier: Double? = null

    var status: CalibrationStatus = CalibrationStatus.READY
        private set
    var phase: CalibrationPhase = CalibrationPhase.NEUTRAL
        private set

    fun start() {
        samples.clear()
        neutralPitch = null
        downwardPitchMultiplier = null
        phase = CalibrationPhase.NEUTRAL
        status = CalibrationStatus.READY
        resetPhaseProgress()
    }

    fun beginPhase() {
        if (status != CalibrationStatus.READY || phase == CalibrationPhase.COMPLETE) return
        samples[phase] = PhaseSamples()
        status = CalibrationStatus.COLLECTING
        resetPhaseProgress()
    }

    fun repeatPhase() {
        if (phase == CalibrationPhase.COMPLETE) return
        samples.remove(phase)
        when (phase) {
            CalibrationPhase.NEUTRAL -> {
                neutralPitch = null
                downwardPitchMultiplier = null
            }
            CalibrationPhase.HEAD_DOWN -> downwardPitchMultiplier = null
            else -> Unit
        }
        status = CalibrationStatus.READY
        resetPhaseProgress()
    }

    /** Invalid input breaks timing continuity and is never substituted with zero. */
    fun update(timestampMs: Long, measurements: FacialMeasurements): Boolean {
        if (status != CalibrationStatus.COLLECTING) return false
        val required = requiredValues(measurements) ?: run {
            lastValidTimestampMs = null
            return false
        }

        lastValidTimestampMs?.let { previous ->
            validElapsedMs += (timestampMs - previous).coerceAtLeast(0L)
        }
        lastValidTimestampMs = timestampMs
        val phaseSamples = samples.getValue(phase)
        required.ear?.let(phaseSamples.ear::add)
        required.mar?.let(phaseSamples.mar::add)
        required.pitch?.let(phaseSamples.pitch::add)

        if (validElapsedMs >= requiredDurationMs) completePhase()
        return true
    }

    fun progress(): CalibrationProgress {
        val currentSamples = samples[phase]
        val counts = listOfNotNull(
            currentSamples?.ear?.takeIf { it.isNotEmpty() }?.size,
            currentSamples?.mar?.takeIf { it.isNotEmpty() }?.size,
            currentSamples?.pitch?.takeIf { it.isNotEmpty() }?.size,
        )
        return CalibrationProgress(
            status = status,
            phase = phase,
            validDurationMs = validElapsedMs,
            requiredDurationMs = requiredDurationMs,
            validSampleCount = counts.minOrNull() ?: 0,
        )
    }

    fun buildResult(calibratedAtTimestampMs: Long): CalibrationResult {
        check(status == CalibrationStatus.COMPLETE) { "Calibration is not complete." }
        val neutral = samples.getValue(CalibrationPhase.NEUTRAL)
        val closed = samples.getValue(CalibrationPhase.EYES_CLOSED)
        val open = samples.getValue(CalibrationPhase.MOUTH_OPEN)
        return CalibrationResult(
            calibrationId = "android-calibration-$calibratedAtTimestampMs",
            calibratedAtTimestampMs = calibratedAtTimestampMs,
            neutralEar = neutral.ear.average(),
            closedEyeEar = closed.ear.average(),
            earThreshold = neutral.ear.average() * 0.75,
            neutralMar = neutral.mar.average(),
            openMouthMar = open.mar.average(),
            // The stable prototype derives MAR only from the guided open-mouth phase.
            marThreshold = open.mar.average() * 0.50,
            neutralHeadPitchDegrees = checkNotNull(neutralPitch),
            downwardPitchMultiplier = checkNotNull(downwardPitchMultiplier),
        )
    }

    private fun completePhase() {
        if (phase == CalibrationPhase.NEUTRAL) {
            neutralPitch = median(samples.getValue(phase).pitch)
        }
        if (phase == CalibrationPhase.HEAD_DOWN) {
            val baseline = checkNotNull(neutralPitch)
            val rawDeltas = samples.getValue(phase).pitch.map {
                checkNotNull(FacialMeasurementCalculator.relativePitchDegrees(it, baseline))
            }
            val medianDelta = median(rawDeltas)
            if (medianDelta == 0.0) {
                status = CalibrationStatus.READY
                resetPhaseProgress()
                return
            }
            downwardPitchMultiplier = if (medianDelta > 0.0) 1.0 else -1.0
        }

        val index = phaseOrder.indexOf(phase)
        if (index == phaseOrder.lastIndex) {
            phase = CalibrationPhase.COMPLETE
            status = CalibrationStatus.COMPLETE
        } else {
            phase = phaseOrder[index + 1]
            status = CalibrationStatus.READY
        }
        resetPhaseProgress()
    }

    private fun requiredValues(measurements: FacialMeasurements): RequiredValues? {
        return when (phase) {
            CalibrationPhase.NEUTRAL -> RequiredValues(
                ear = measurements.ear.validOrNull() ?: return null,
                mar = measurements.mar.validOrNull() ?: return null,
                pitch = measurements.rawHeadPitchDegrees.validOrNull() ?: return null,
            )
            CalibrationPhase.EYES_CLOSED -> RequiredValues(
                ear = measurements.ear.validOrNull() ?: return null,
            )
            CalibrationPhase.MOUTH_OPEN -> RequiredValues(
                mar = measurements.mar.validOrNull() ?: return null,
            )
            CalibrationPhase.HEAD_DOWN -> RequiredValues(
                pitch = measurements.rawHeadPitchDegrees.validOrNull() ?: return null,
            )
            CalibrationPhase.COMPLETE -> null
        }
    }

    private fun resetPhaseProgress() {
        validElapsedMs = 0L
        lastValidTimestampMs = null
    }

    private data class RequiredValues(
        val ear: Double? = null,
        val mar: Double? = null,
        val pitch: Double? = null,
    )

    private data class PhaseSamples(
        val ear: MutableList<Double> = mutableListOf(),
        val mar: MutableList<Double> = mutableListOf(),
        val pitch: MutableList<Double> = mutableListOf(),
    )

    private fun Double?.validOrNull(): Double? = this?.takeIf(Double::isFinite)

    private fun median(values: List<Double>): Double {
        require(values.isNotEmpty()) { "Cannot calculate a median without samples." }
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}
