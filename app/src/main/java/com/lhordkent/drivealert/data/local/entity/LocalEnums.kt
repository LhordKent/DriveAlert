package com.lhordkent.drivealert.data.local.entity

enum class MonitoringSessionStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    INTERRUPTED,
}

enum class StoredWarningStage(val level: Int) {
    STAGE_1(1),
    STAGE_2(2),
    STAGE_3(3),
}

enum class StoredVisibleSign {
    PROLONGED_EYE_CLOSURE,
    YAWNING,
    HEAD_NODDING,
}

enum class StoredWarningSound {
    ROOSTER_CALL,
    ALARM_CLOCK,
    DIGITAL_BEEP,
    SIREN_PULSE,
    BELL_CHIME,
}

enum class StoredPreferredVolume {
    MINIMUM,
    MEDIUM,
    HIGH,
}

enum class StageSyncRecordType {
    STAGE3_TRANSITION,
    STAGE3_PERSISTENCE,
}

enum class StageSyncEligibility {
    PENDING_EVALUATION,
    ELIGIBLE,
    NO_APPROVED_CONTACT,
}

enum class StageSyncStatus {
    NOT_QUEUED,
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
}

enum class ConnectionStatus {
    PENDING,
    APPROVED,
    DECLINED,
    REVOKED,
}
