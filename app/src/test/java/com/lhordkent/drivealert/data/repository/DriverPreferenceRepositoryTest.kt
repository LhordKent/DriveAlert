package com.lhordkent.drivealert.data.repository

import com.lhordkent.drivealert.data.local.dao.DriverPreferenceDao
import com.lhordkent.drivealert.data.local.entity.DriverPreferenceEntity
import com.lhordkent.drivealert.data.local.entity.StoredPreferredVolume
import com.lhordkent.drivealert.data.local.entity.StoredWarningSound
import com.lhordkent.drivealert.postauth.PreferredVolume
import com.lhordkent.drivealert.postauth.WarningSound
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DriverPreferenceRepositoryTest {
    @Test
    fun `Digital Beep is the default sound`() {
        assertEquals(WarningSound.DIGITAL_BEEP, DriverPreferences().warningSound)
    }

    @Test
    fun `domain exposes exactly the five final sounds`() {
        assertEquals(
            listOf(
                WarningSound.DIGITAL_BEEP,
                WarningSound.ROOSTER_CALL,
                WarningSound.ALARM_CLOCK,
                WarningSound.DIGITAL_BEEP_2,
                WarningSound.BELL_CHIME,
            ),
            WarningSound.values().toList(),
        )
    }

    @Test
    fun `legacy Siren Pulse reads as Digital Beep 2 and is normalized on a later save`() = runBlocking {
        val dao = FakePreferenceDao(entity(StoredWarningSound.SIREN_PULSE))
        val repository = RoomDriverPreferenceRepository(dao, clock = { 99L })

        assertEquals(WarningSound.DIGITAL_BEEP_2, repository.observe(DRIVER_ID).first().warningSound)
        repository.setPreferredVolume(DRIVER_ID, PreferredVolume.HIGH)

        assertEquals(StoredWarningSound.DIGITAL_BEEP_2, dao.current.value?.selectedWarningSound)
        assertEquals(StoredPreferredVolume.HIGH, dao.current.value?.preferredWarningVolume)
    }

    @Test
    fun `changing volume preserves every nonlegacy explicit sound`() = runBlocking {
        val cases = mapOf(
            StoredWarningSound.DIGITAL_BEEP to WarningSound.DIGITAL_BEEP,
            StoredWarningSound.ROOSTER_CALL to WarningSound.ROOSTER_CALL,
            StoredWarningSound.ALARM_CLOCK to WarningSound.ALARM_CLOCK,
            StoredWarningSound.DIGITAL_BEEP_2 to WarningSound.DIGITAL_BEEP_2,
            StoredWarningSound.BELL_CHIME to WarningSound.BELL_CHIME,
        )
        cases.forEach { (stored, domain) ->
            val dao = FakePreferenceDao(entity(stored))
            val repository = RoomDriverPreferenceRepository(dao)
            assertEquals(domain, repository.observe(DRIVER_ID).first().warningSound)
            repository.setPreferredVolume(DRIVER_ID, PreferredVolume.MINIMUM)
            assertEquals(stored, dao.current.value?.selectedWarningSound)
        }
    }

    private fun entity(sound: StoredWarningSound) = DriverPreferenceEntity(
        driverUserId = DRIVER_ID,
        selectedWarningSound = sound,
        preferredWarningVolume = StoredPreferredVolume.MEDIUM,
        setupAndDeviceRemindersEnabled = true,
        contactRequestUpdatesEnabled = true,
        trustedRequestUpdatesEnabled = true,
        sharedStage3RecordsEnabled = true,
        warningAlertsEnabled = true,
        updatedAtEpochMillis = 1L,
    )

    companion object {
        private const val DRIVER_ID = "driver-a"
    }
}

private class FakePreferenceDao(initial: DriverPreferenceEntity?) : DriverPreferenceDao {
    val current = MutableStateFlow(initial)

    override suspend fun upsert(preference: DriverPreferenceEntity) {
        current.value = preference
    }

    override fun observe(driverUserId: String): Flow<DriverPreferenceEntity?> = current

    override suspend fun get(driverUserId: String): DriverPreferenceEntity? = current.value
}
