package com.lhordkent.drivealert.data.repository

import com.lhordkent.drivealert.data.local.dao.DriverPreferenceDao
import com.lhordkent.drivealert.data.local.entity.DriverPreferenceEntity
import com.lhordkent.drivealert.data.local.entity.StoredPreferredVolume
import com.lhordkent.drivealert.data.local.entity.StoredWarningSound
import com.lhordkent.drivealert.postauth.NotificationPreferences
import com.lhordkent.drivealert.postauth.PreferredVolume
import com.lhordkent.drivealert.postauth.WarningSound
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DriverPreferences(
    val warningSound: WarningSound = WarningSound.DIGITAL_BEEP,
    val preferredVolume: PreferredVolume = PreferredVolume.MEDIUM,
    val notifications: NotificationPreferences = NotificationPreferences(),
)

interface DriverPreferenceRepository {
    fun observe(driverUserId: String): Flow<DriverPreferences>
    suspend fun setWarningSound(driverUserId: String, sound: WarningSound)
    suspend fun setPreferredVolume(driverUserId: String, volume: PreferredVolume)
    suspend fun setNotifications(driverUserId: String, preferences: NotificationPreferences)
}

class RoomDriverPreferenceRepository(
    private val dao: DriverPreferenceDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : DriverPreferenceRepository {
    override fun observe(driverUserId: String): Flow<DriverPreferences> =
        dao.observe(driverUserId).map { it?.toDomain() ?: DriverPreferences() }

    override suspend fun setWarningSound(driverUserId: String, sound: WarningSound) {
        val current = dao.get(driverUserId)?.toDomain() ?: DriverPreferences()
        save(driverUserId, current.copy(warningSound = sound))
    }

    override suspend fun setPreferredVolume(driverUserId: String, volume: PreferredVolume) {
        val current = dao.get(driverUserId)?.toDomain() ?: DriverPreferences()
        save(driverUserId, current.copy(preferredVolume = volume))
    }

    override suspend fun setNotifications(driverUserId: String, preferences: NotificationPreferences) {
        val current = dao.get(driverUserId)?.toDomain() ?: DriverPreferences()
        save(driverUserId, current.copy(notifications = preferences))
    }

    private suspend fun save(driverUserId: String, preferences: DriverPreferences) {
        dao.upsert(
            DriverPreferenceEntity(
                driverUserId = driverUserId,
                selectedWarningSound = preferences.warningSound.toStored(),
                preferredWarningVolume = preferences.preferredVolume.toStored(),
                setupAndDeviceRemindersEnabled = preferences.notifications.setupAndDeviceReminders,
                contactRequestUpdatesEnabled = preferences.notifications.driverRequestUpdates,
                trustedRequestUpdatesEnabled = preferences.notifications.trustedRequestUpdates,
                sharedStage3RecordsEnabled = preferences.notifications.sharedStage3Records,
                updatedAtEpochMillis = clock(),
            ),
        )
    }
}

private fun DriverPreferenceEntity.toDomain() = DriverPreferences(
    warningSound = when (selectedWarningSound) {
        StoredWarningSound.ROOSTER_CALL -> WarningSound.ROOSTER_CALL
        StoredWarningSound.ALARM_CLOCK -> WarningSound.ALARM_CLOCK
        StoredWarningSound.DIGITAL_BEEP -> WarningSound.DIGITAL_BEEP
        StoredWarningSound.SIREN_PULSE -> WarningSound.SIREN_PULSE
        StoredWarningSound.BELL_CHIME -> WarningSound.BELL_CHIME
    },
    preferredVolume = when (preferredWarningVolume) {
        StoredPreferredVolume.MINIMUM -> PreferredVolume.MINIMUM
        StoredPreferredVolume.MEDIUM -> PreferredVolume.MEDIUM
        StoredPreferredVolume.HIGH -> PreferredVolume.HIGH
    },
    notifications = NotificationPreferences(
        setupAndDeviceReminders = setupAndDeviceRemindersEnabled,
        driverRequestUpdates = contactRequestUpdatesEnabled,
        trustedRequestUpdates = trustedRequestUpdatesEnabled,
        sharedStage3Records = sharedStage3RecordsEnabled,
    ),
)

private fun WarningSound.toStored() = when (this) {
    WarningSound.ROOSTER_CALL -> StoredWarningSound.ROOSTER_CALL
    WarningSound.ALARM_CLOCK -> StoredWarningSound.ALARM_CLOCK
    WarningSound.DIGITAL_BEEP -> StoredWarningSound.DIGITAL_BEEP
    WarningSound.SIREN_PULSE -> StoredWarningSound.SIREN_PULSE
    WarningSound.BELL_CHIME -> StoredWarningSound.BELL_CHIME
}

private fun PreferredVolume.toStored() = when (this) {
    PreferredVolume.MINIMUM -> StoredPreferredVolume.MINIMUM
    PreferredVolume.MEDIUM -> StoredPreferredVolume.MEDIUM
    PreferredVolume.HIGH -> StoredPreferredVolume.HIGH
}
