package com.lhordkent.drivealert.detection.calibration

import com.lhordkent.drivealert.detection.model.CalibrationProgress
import com.lhordkent.drivealert.detection.model.CalibrationStatus

/** Only a measured forward transition completes a step; navigation and retries do not. */
internal fun completedCalibrationStep(previous: CalibrationProgress?, next: CalibrationProgress): Boolean =
    previous != null && previous.status == CalibrationStatus.COLLECTING &&
        previous.revision == next.revision && next.phase.ordinal == previous.phase.ordinal + 1 &&
        next.status in listOf(CalibrationStatus.READY, CalibrationStatus.COMPLETE)
