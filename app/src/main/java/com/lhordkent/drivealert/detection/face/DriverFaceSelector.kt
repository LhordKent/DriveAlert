package com.lhordkent.drivealert.detection.face

import com.lhordkent.drivealert.detection.model.FaceObservation
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Coordinates refer to the decoded camera bitmap, not a cropped preview. */
data class DriverRegion(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0.0..1.0 })
        require(right > left && bottom > top)
    }
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val area get() = width * height
    fun intersection(other: DriverRegion): Double =
        max(0.0, min(right, other.right) - max(left, other.left)) *
            max(0.0, min(bottom, other.bottom) - max(top, other.top))
    fun containsCenter(other: DriverRegion) = other.centerX in left..right && other.centerY in top..bottom
    companion object {
        val DEFAULT = DriverRegion(0.2, 0.08, 0.8, 0.92)
        fun bounds(face: FaceObservation): DriverRegion? {
            val points = face.landmarks
            if (points.size < 3 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
            val left = points.minOf { it.x }.coerceIn(0.0, 1.0)
            val right = points.maxOf { it.x }.coerceIn(0.0, 1.0)
            val top = points.minOf { it.y }.coerceIn(0.0, 1.0)
            val bottom = points.maxOf { it.y }.coerceIn(0.0, 1.0)
            return if (right - left >= 0.025 && bottom - top >= 0.025) DriverRegion(left, top, right, bottom) else null
        }
    }
}

enum class DriverTrackingStatus { UNCONFIGURED, ACQUIRING, LOCKED, LOST, AMBIGUOUS }

data class DriverTrackingState(
    val status: DriverTrackingStatus = DriverTrackingStatus.UNCONFIGURED,
    val bounds: DriverRegion? = null,
    val generation: Long = 0,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val detectedFaceCount: Int = 0,
) {
    val locked get() = status == DriverTrackingStatus.LOCKED
    val label get() = when (status) {
        DriverTrackingStatus.UNCONFIGURED -> "Set driver area"
        DriverTrackingStatus.ACQUIRING -> "Finding driver"
        DriverTrackingStatus.LOCKED -> "Driver tracked"
        else -> "Driver not clearly visible"
    }
    val guidance get() = when (status) {
        DriverTrackingStatus.UNCONFIGURED -> "Confirm the driver area in Camera Alignment before monitoring."
        DriverTrackingStatus.ACQUIRING -> "Keep one face steady inside the driver area."
        DriverTrackingStatus.LOCKED -> "The selected face is inside the driver area."
        DriverTrackingStatus.LOST -> "Check camera position when safe. Assessment resumes after stable driver tracking returns."
        DriverTrackingStatus.AMBIGUOUS -> "Keep the driver's face clear. Assessment is paused while faces compete for the driver area."
    }
}

data class DriverSelection(val tracking: DriverTrackingState, val observation: FaceObservation?)

/** No identity or result-index tracking. Uncertain associations always break evidence. */
class DriverFaceSelector(private val acquisitionMs: Long = 1_000, private val capacity: Int = 4) {
    init { require(acquisitionMs > 0 && capacity >= 2) }
    private var previous: DriverRegion? = null
    private var startedAt: Long? = null
    private var lastTimestamp: Long? = null
    private var generation = 0L
    private var locked = false
    private var region: DriverRegion? = null
    private var dimensions: Pair<Int, Int>? = null

    fun reset() {
        previous = null
        startedAt = null
        lastTimestamp = null
        locked = false
        generation++
    }

    fun select(timestamp: Long, faces: List<FaceObservation>, driverRegion: DriverRegion?, width: Int, height: Int): DriverSelection {
        if (region != driverRegion || dimensions != (width to height) ||
            lastTimestamp?.let { timestamp <= it || timestamp - it > MAX_FRAME_GAP_MS } == true) reset()
        region = driverRegion
        dimensions = width to height
        lastTimestamp = timestamp
        fun unavailable(status: DriverTrackingStatus): DriverSelection {
            if (previous != null || locked) reset()
            lastTimestamp = timestamp
            return DriverSelection(DriverTrackingState(status, generation = generation, frameWidth = width, frameHeight = height, detectedFaceCount = faces.size), null)
        }
        if (driverRegion == null) return unavailable(DriverTrackingStatus.UNCONFIGURED)
        if (faces.size >= capacity) return unavailable(DriverTrackingStatus.AMBIGUOUS)
        val candidates = faces.mapNotNull { face -> DriverRegion.bounds(face)?.let { face to it } }
        // A malformed detection cannot safely be dismissed as a non-competing face.
        if (candidates.size != faces.size) return unavailable(DriverTrackingStatus.AMBIGUOUS)
        val eligible = candidates.filter { (_, box) -> driverRegion.containsCenter(box) && driverRegion.intersection(box) / box.area >= 0.8 }
        if (eligible.isEmpty()) return unavailable(DriverTrackingStatus.LOST)
        if (eligible.size != 1) return unavailable(DriverTrackingStatus.AMBIGUOUS)
        val (face, box) = eligible.single()
        if (candidates.any { (other, otherBox) -> other !== face && box.intersection(otherBox) / min(box.area, otherBox.area) > 0.15 }) {
            return unavailable(DriverTrackingStatus.AMBIGUOUS)
        }
        val old = previous
        if (old != null && !continuous(old, box)) return unavailable(DriverTrackingStatus.LOST)
        previous = box
        if (startedAt == null) startedAt = timestamp
        locked = timestamp - checkNotNull(startedAt) >= acquisitionMs
        val state = DriverTrackingState(
            if (locked) DriverTrackingStatus.LOCKED else DriverTrackingStatus.ACQUIRING,
            if (locked) box else null, generation, width, height, faces.size,
        )
        return DriverSelection(state, face.takeIf { locked })
    }

    private fun continuous(old: DriverRegion, next: DriverRegion): Boolean {
        val overlap = old.intersection(next) / (old.area + next.area - old.intersection(next))
        val displacement = hypot(old.centerX - next.centerX, old.centerY - next.centerY)
        return overlap >= 0.25 && displacement <= max(old.width, old.height) * 0.45 && next.area / old.area in 0.5..2.0
    }

    companion object { const val MAX_FRAME_GAP_MS = 500L }
}
