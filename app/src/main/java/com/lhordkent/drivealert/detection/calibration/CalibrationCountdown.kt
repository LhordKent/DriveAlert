package com.lhordkent.drivealert.detection.calibration

/** Matches the owner's 2.5-second DFPlayer track 17. */
internal object CalibrationCountdown {
    const val DURATION_MS = 2_500L
    val steps = listOf(3 to 834L, 2 to 833L, 1 to 833L)
}
