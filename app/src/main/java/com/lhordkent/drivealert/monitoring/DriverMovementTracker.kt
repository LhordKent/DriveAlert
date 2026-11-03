package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.detection.model.FaceObservation
import kotlin.math.hypot

enum class DriverMovementStatus { UNAVAILABLE, OBSERVING, RECENT_MOVEMENT, NO_MOVEMENT }

data class DriverMovementAssessment(
    val status: DriverMovementStatus = DriverMovementStatus.UNAVAILABLE,
    val inactivityMs: Long = 0,
    val speechDelivery: WarningDeliveryStatus = WarningDeliveryStatus.NOT_REQUESTED,
)

/** Provisional engineering settings, not validated liveness thresholds. */
data class DriverMovementConfig(
    val sampleIntervalMs: Long = 200,
    val displacementFraction: Double = 0.02,
    val confirmationMs: Long = 200,
    val statusDelayMs: Long = 10_000,
    val speechDelayMs: Long = 25_000,
    val maxFrameGapMs: Long = 1_000,
) {
    init {
        require(sampleIntervalMs > 0 && confirmationMs >= sampleIntervalMs)
        require(displacementFraction.isFinite() && displacementFraction > 0)
        require(statusDelayMs > 0 && speechDelayMs >= statusDelayMs)
        require(maxFrameGapMs >= sampleIntervalMs)
    }
}

/** Numeric landmarks only. No authority over calibration or drowsiness assessment. */
class DriverMovementTracker(private val config: DriverMovementConfig = DriverMovementConfig()) {
    private data class Point(val x: Double, val y: Double)
    private data class Sample(val points: List<Point>, val width: Double)
    private val recent = ArrayDeque<Sample>()
    private var reference: Sample? = null
    private var lastFrameMs: Long? = null
    private var lastSampleMs: Long? = null
    private var lastMovementMs: Long? = null
    private var candidateMs: Long? = null
    private var movementSeen = false
    private var speechAttempted = false
    private var revision = 0L
    var assessment = DriverMovementAssessment()
        private set
    val speechDue: Boolean
        get() = assessment.status == DriverMovementStatus.NO_MOVEMENT &&
            assessment.inactivityMs >= config.speechDelayMs && !speechAttempted

    fun reset() {
        revision++
        recent.clear()
        reference = null
        lastFrameMs = null
        lastSampleMs = null
        lastMovementMs = null
        candidateMs = null
        movementSeen = false
        speechAttempted = false
        assessment = DriverMovementAssessment()
    }

    fun update(observation: FaceObservation?): DriverMovementAssessment {
        if (observation == null || !valid(observation)) {
            reset()
            return assessment
        }
        val now = observation.timestampMs
        val previous = lastFrameMs
        if (previous != null && (now <= previous || now - previous > config.maxFrameGapMs)) reset()
        lastFrameMs = now
        if (lastSampleMs?.let { now - it < config.sampleIntervalMs } == true) return publish(now)
        val sample = sample(observation) ?: run {
            reset()
            return assessment
        }
        lastSampleMs = now
        if (lastMovementMs == null) lastMovementMs = now
        recent.addLast(sample)
        if (recent.size > REFERENCE_SAMPLES) recent.removeFirst()
        val baseline = reference
        if (baseline == null) {
            if (recent.size == REFERENCE_SAMPLES) reference = medianSample()
        } else {
            val changed = GROUP_OFFSETS.any { range ->
                median(range.map { index ->
                    hypot(sample.points[index].x - baseline.points[index].x,
                        sample.points[index].y - baseline.points[index].y)
                }) / baseline.width >= config.displacementFraction
            }
            if (!changed) candidateMs = null
            else {
                val started = candidateMs
                if (started == null) candidateMs = now
                else if (now - started >= config.confirmationMs) {
                    revision++
                    lastMovementMs = now
                    movementSeen = true
                    speechAttempted = false
                    assessment = assessment.copy(speechDelivery = WarningDeliveryStatus.NOT_REQUESTED)
                    reference = medianSample()
                    candidateMs = null
                }
            }
        }
        return publish(now)
    }

    /** Claim before suspending for transport; suppressed requests remain eligible. */
    fun claimSpeech(drowsinessActive: Boolean, outputBusy: Boolean, visibilityIssueActive: Boolean): Long? {
        if (!speechDue || drowsinessActive || outputBusy || visibilityIssueActive) return null
        speechAttempted = true
        assessment = assessment.copy(speechDelivery = WarningDeliveryStatus.HARDWARE_PENDING)
        return revision
    }

    /** Ignore completion from an earlier session or inactivity episode. */
    fun completeSpeech(token: Long, delivery: WarningDeliveryStatus) {
        if (token == revision && speechAttempted) assessment = assessment.copy(speechDelivery = delivery)
    }

    private fun publish(now: Long): DriverMovementAssessment {
        val inactivity = lastMovementMs?.let { (now - it).coerceAtLeast(0) } ?: return assessment
        assessment = assessment.copy(
            status = when {
                inactivity >= config.statusDelayMs -> DriverMovementStatus.NO_MOVEMENT
                movementSeen -> DriverMovementStatus.RECENT_MOVEMENT
                else -> DriverMovementStatus.OBSERVING
            },
            inactivityMs = inactivity,
        )
        return assessment
    }

    private fun sample(face: FaceObservation): Sample? {
        val width = (face.landmarks.maxOf { it.x } - face.landmarks.minOf { it.x }) * face.frameWidth
        if (!width.isFinite() || width <= 0) return null
        return Sample(INDICES.map { index ->
            val point = face.landmarks[index]
            Point(point.x * face.frameWidth, point.y * face.frameHeight)
        }, width)
    }

    private fun valid(face: FaceObservation): Boolean = face.frameWidth > 0 && face.frameHeight > 0 &&
        INDICES.all { it in face.landmarks.indices } && face.landmarks.all { it.x.isFinite() && it.y.isFinite() }

    private fun medianSample() = Sample(
        INDICES.indices.map { index -> Point(median(recent.map { it.points[index].x }), median(recent.map { it.points[index].y })) },
        median(recent.map { it.width }),
    )

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
    }

    companion object {
        private const val REFERENCE_SAMPLES = 3
        private val INDICES = intArrayOf(362, 385, 387, 263, 373, 380, 33, 160, 158, 133, 153, 144,
            78, 308, 81, 178, 13, 14, 311, 402)
        private val GROUP_OFFSETS = listOf(0..5, 6..11, 12..19)
    }
}
