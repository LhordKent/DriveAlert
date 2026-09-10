package com.lhordkent.drivealert.data.repository

import android.os.SystemClock
import com.lhordkent.drivealert.data.local.dao.CalibrationDao
import com.lhordkent.drivealert.data.local.entity.CalibrationEntity
import com.lhordkent.drivealert.detection.model.CalibrationResult

interface CalibrationRepository {
    suspend fun active(driverUserId: String): CalibrationResult?
    suspend fun saveAndActivate(driverUserId: String, result: CalibrationResult, calibratedAtEpochMillis: Long)
}

class RoomCalibrationRepository(private val dao: CalibrationDao) : CalibrationRepository {
    override suspend fun active(driverUserId: String): CalibrationResult? =
        dao.getActive(driverUserId)?.toResult()

    override suspend fun saveAndActivate(
        driverUserId: String,
        result: CalibrationResult,
        calibratedAtEpochMillis: Long,
    ) {
        dao.insertAndActivate(
            CalibrationEntity(
                calibrationId = result.calibrationId,
                driverUserId = driverUserId,
                earThreshold = result.earThreshold,
                marThreshold = result.marThreshold,
                headPitchThresholdDegrees = 15.0,
                neutralHeadPitchDegrees = result.neutralHeadPitchDegrees,
                detectorSchemaVersion = result.schemaVersion,
                neutralEar = result.neutralEar,
                closedEyeEar = result.closedEyeEar,
                neutralMar = result.neutralMar,
                openMouthMar = result.openMouthMar,
                downwardPitchMultiplier = result.downwardPitchMultiplier,
                calibratedAtEpochMillis = calibratedAtEpochMillis,
                isActive = true,
            ),
        )
    }

    private fun CalibrationEntity.toResult(): CalibrationResult? {
        val values = listOf(
            earThreshold, marThreshold, neutralHeadPitchDegrees, neutralEar,
            closedEyeEar, neutralMar, openMouthMar, downwardPitchMultiplier,
        )
        if (detectorSchemaVersion != 3 || values.any { !it.isFinite() } ||
            earThreshold <= 0.0 || marThreshold <= 0.0 || downwardPitchMultiplier == 0.0
        ) return null
        return CalibrationResult(
            calibrationId = calibrationId,
            schemaVersion = detectorSchemaVersion,
            calibratedAtTimestampMs = SystemClock.elapsedRealtime(),
            neutralEar = neutralEar,
            closedEyeEar = closedEyeEar,
            earThreshold = earThreshold,
            neutralMar = neutralMar,
            openMouthMar = openMouthMar,
            marThreshold = marThreshold,
            neutralHeadPitchDegrees = neutralHeadPitchDegrees,
            downwardPitchMultiplier = downwardPitchMultiplier,
        )
    }
}
