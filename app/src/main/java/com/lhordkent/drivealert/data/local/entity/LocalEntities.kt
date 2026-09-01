package com.lhordkent.drivealert.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "driver_calibrations",
    indices = [Index("driverUserId"), Index(value = ["driverUserId", "isActive"])],
)
data class CalibrationEntity(
    @androidx.room.PrimaryKey val calibrationId: String,
    val driverUserId: String,
    val earThreshold: Double,
    val marThreshold: Double,
    val headPitchThresholdDegrees: Double,
    val neutralHeadPitchDegrees: Double,
    val calibratedAtEpochMillis: Long,
    val isActive: Boolean,
)

@Entity(
    tableName = "monitoring_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CalibrationEntity::class,
            parentColumns = ["calibrationId"],
            childColumns = ["calibrationId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("driverUserId"),
        Index("calibrationId"),
        Index("deviceId"),
        Index(value = ["driverUserId", "startedAtEpochMillis"]),
    ],
)
data class MonitoringSessionEntity(
    @androidx.room.PrimaryKey val sessionId: String,
    val driverUserId: String,
    val calibrationId: String,
    val deviceId: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val status: MonitoringSessionStatus,
    val highestWarningStage: StoredWarningStage?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    init {
        require(endedAtEpochMillis == null || endedAtEpochMillis >= startedAtEpochMillis) {
            "A monitoring session cannot end before it starts."
        }
    }
}

@Entity(
    tableName = "alerts",
    foreignKeys = [
        ForeignKey(
            entity = MonitoringSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("driverUserId"),
        Index(value = ["driverUserId", "detectedAtEpochMillis"]),
    ],
)
data class AlertEntity(
    @androidx.room.PrimaryKey val alertId: String,
    val sessionId: String,
    val driverUserId: String,
    val detectedAtEpochMillis: Long,
    val warningStageAtDetection: StoredWarningStage,
    val alarmTriggered: Boolean,
    val alarmTriggeredAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
) {
    init {
        require(alarmTriggered || alarmTriggeredAtEpochMillis == null) {
            "alarmTriggeredAtEpochMillis must be null when the onboard alarm was not triggered."
        }
    }
}

@Entity(
    tableName = "alert_signs",
    primaryKeys = ["alertId", "signType"],
    foreignKeys = [
        ForeignKey(
            entity = AlertEntity::class,
            parentColumns = ["alertId"],
            childColumns = ["alertId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("alertId")],
)
data class AlertSignEntity(
    val alertId: String,
    val signType: StoredVisibleSign,
)

@Entity(tableName = "driver_preferences")
data class DriverPreferenceEntity(
    @androidx.room.PrimaryKey val driverUserId: String,
    val selectedWarningSound: StoredWarningSound,
    val preferredWarningVolume: StoredPreferredVolume,
    val setupAndDeviceRemindersEnabled: Boolean,
    val contactRequestUpdatesEnabled: Boolean,
    val trustedRequestUpdatesEnabled: Boolean,
    val sharedStage3RecordsEnabled: Boolean,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "stage_sync_records",
    foreignKeys = [
        ForeignKey(
            entity = MonitoringSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("driverUserId"),
        Index(value = ["driverUserId", "syncStatus"]),
        Index(value = ["driverUserId", "periodStartedAtEpochMillis"]),
    ],
)
data class StageSyncRecordEntity(
    @androidx.room.PrimaryKey val stageSyncRecordId: String,
    val sessionId: String,
    val driverUserId: String,
    val recordType: StageSyncRecordType,
    val periodStartedAtEpochMillis: Long,
    val periodEndedAtEpochMillis: Long?,
    val eventCount: Int,
    val eligibility: StageSyncEligibility,
    val syncStatus: StageSyncStatus,
    val attemptCount: Int,
    val lastAttemptAtEpochMillis: Long?,
    val syncedAtEpochMillis: Long?,
    val syncErrorCode: String?,
    val syncErrorMessage: String?,
    val firestoreDocumentId: String,
    val createdAtEpochMillis: Long,
) {
    init {
        require(eventCount >= 1) { "A Stage 3 synchronization record must represent at least one confirmed event." }
        require(periodEndedAtEpochMillis == null || periodEndedAtEpochMillis >= periodStartedAtEpochMillis) {
            "A Stage 3 period cannot end before it starts."
        }
    }
}

@Entity(
    tableName = "stage_sync_record_signs",
    primaryKeys = ["stageSyncRecordId", "signType"],
    foreignKeys = [
        ForeignKey(
            entity = StageSyncRecordEntity::class,
            parentColumns = ["stageSyncRecordId"],
            childColumns = ["stageSyncRecordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("stageSyncRecordId")],
)
data class StageSyncRecordSignEntity(
    val stageSyncRecordId: String,
    val signType: StoredVisibleSign,
)

@Entity(
    tableName = "trusted_contact_connection_projection",
    indices = [
        Index("driverUserId"),
        Index("trustedContactUserId"),
        Index(value = ["driverUserId", "status"]),
    ],
)
data class TrustedContactConnectionProjectionEntity(
    @androidx.room.PrimaryKey val connectionId: String,
    val driverUserId: String,
    val trustedContactUserId: String,
    val requestedByUserId: String,
    val status: ConnectionStatus,
    val requestedAtEpochMillis: Long,
    val approvedAtEpochMillis: Long?,
    val declinedAtEpochMillis: Long?,
    val revokedAtEpochMillis: Long?,
    val lastRefreshedAtEpochMillis: Long,
)
