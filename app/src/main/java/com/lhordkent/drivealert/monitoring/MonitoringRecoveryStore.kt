package com.lhordkent.drivealert.monitoring

import android.content.Context
import android.provider.Settings
import com.lhordkent.drivealert.postauth.PreferredVolume
import com.lhordkent.drivealert.postauth.WarningSound

data class MonitoringRecoveryRecord(
    val sessionId: String,
    val driverUserId: String,
    val startedAtEpochMillis: Long,
    val sound: WarningSound,
    val volume: PreferredVolume,
    val notificationsEnabled: Boolean,
    val accessoryMode: DriverAccessoryMode,
    val bootCount: Int,
)

/** Small same-boot crash-recovery record. It is never used to resume after reboot. */
class MonitoringRecoveryStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun currentBootCount(): Int = Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.BOOT_COUNT,
        UNKNOWN_BOOT_COUNT,
    )

    fun save(record: MonitoringRecoveryRecord) {
        preferences.edit()
            .putString(KEY_SESSION_ID, record.sessionId)
            .putString(KEY_DRIVER_ID, record.driverUserId)
            .putLong(KEY_STARTED_AT, record.startedAtEpochMillis)
            .putString(KEY_SOUND, record.sound.name)
            .putString(KEY_VOLUME, record.volume.name)
            .putBoolean(KEY_NOTIFICATIONS, record.notificationsEnabled)
            .putString(KEY_ACCESSORY, record.accessoryMode.name)
            .putInt(KEY_BOOT_COUNT, record.bootCount)
            .apply()
    }

    fun load(): MonitoringRecoveryRecord? {
        val sessionId = preferences.getString(KEY_SESSION_ID, null) ?: return null
        val driverId = preferences.getString(KEY_DRIVER_ID, null) ?: return null
        return runCatching {
            MonitoringRecoveryRecord(
                sessionId = sessionId,
                driverUserId = driverId,
                startedAtEpochMillis = preferences.getLong(KEY_STARTED_AT, 0L).also { require(it > 0L) },
                sound = WarningSound.valueOf(requireNotNull(preferences.getString(KEY_SOUND, null))),
                volume = PreferredVolume.valueOf(requireNotNull(preferences.getString(KEY_VOLUME, null))),
                notificationsEnabled = preferences.getBoolean(KEY_NOTIFICATIONS, true),
                accessoryMode = DriverAccessoryMode.valueOf(requireNotNull(preferences.getString(KEY_ACCESSORY, null))),
                bootCount = preferences.getInt(KEY_BOOT_COUNT, UNKNOWN_BOOT_COUNT),
            )
        }.getOrNull()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES = "drivealert_monitoring_recovery"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_DRIVER_ID = "driver_id"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_SOUND = "sound"
        private const val KEY_VOLUME = "volume"
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_ACCESSORY = "accessory"
        private const val KEY_BOOT_COUNT = "boot_count"
        const val UNKNOWN_BOOT_COUNT = -1
    }
}
