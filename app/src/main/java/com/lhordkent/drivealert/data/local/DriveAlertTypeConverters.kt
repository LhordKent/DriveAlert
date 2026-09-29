package com.lhordkent.drivealert.data.local

import androidx.room.TypeConverter
import com.lhordkent.drivealert.data.local.entity.ConnectionStatus
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionStatus
import com.lhordkent.drivealert.data.local.entity.NotificationDispatchStatus
import com.lhordkent.drivealert.data.local.entity.StageSyncEligibility
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordType
import com.lhordkent.drivealert.data.local.entity.StageSyncStatus
import com.lhordkent.drivealert.data.local.entity.StoredPreferredVolume
import com.lhordkent.drivealert.data.local.entity.StoredVisibleSign
import com.lhordkent.drivealert.data.local.entity.StoredWarningSound
import com.lhordkent.drivealert.data.local.entity.StoredWarningStage

class DriveAlertTypeConverters {
    @TypeConverter fun monitoringSessionStatus(value: String) = enumValueOf<MonitoringSessionStatus>(value)
    @TypeConverter fun monitoringSessionStatus(value: MonitoringSessionStatus) = value.name
    @TypeConverter fun warningStage(value: String?): StoredWarningStage? =
        value?.let { enumValueOf<StoredWarningStage>(it) }
    @TypeConverter fun warningStage(value: StoredWarningStage?): String? = value?.name
    @TypeConverter fun visibleSign(value: String) = enumValueOf<StoredVisibleSign>(value)
    @TypeConverter fun visibleSign(value: StoredVisibleSign) = value.name
    @TypeConverter fun warningSound(value: String) = enumValueOf<StoredWarningSound>(value)
    @TypeConverter fun warningSound(value: StoredWarningSound) = value.name
    @TypeConverter fun preferredVolume(value: String) = enumValueOf<StoredPreferredVolume>(value)
    @TypeConverter fun preferredVolume(value: StoredPreferredVolume) = value.name
    @TypeConverter fun syncRecordType(value: String) = enumValueOf<StageSyncRecordType>(value)
    @TypeConverter fun syncRecordType(value: StageSyncRecordType) = value.name
    @TypeConverter fun syncEligibility(value: String) = enumValueOf<StageSyncEligibility>(value)
    @TypeConverter fun syncEligibility(value: StageSyncEligibility) = value.name
    @TypeConverter fun syncStatus(value: String) = enumValueOf<StageSyncStatus>(value)
    @TypeConverter fun syncStatus(value: StageSyncStatus) = value.name
    @TypeConverter fun connectionStatus(value: String) = enumValueOf<ConnectionStatus>(value)
    @TypeConverter fun connectionStatus(value: ConnectionStatus) = value.name
    @TypeConverter fun notificationDispatchStatus(value: String) = enumValueOf<NotificationDispatchStatus>(value)
    @TypeConverter fun notificationDispatchStatus(value: NotificationDispatchStatus) = value.name
}
