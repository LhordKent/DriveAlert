package com.lhordkent.drivealert.data.repository

import com.lhordkent.drivealert.data.local.dao.AlertDao
import com.lhordkent.drivealert.data.local.entity.AlertEntity
import com.lhordkent.drivealert.data.local.entity.AlertSignEntity
import com.lhordkent.drivealert.data.local.entity.AlertWithSigns
import com.lhordkent.drivealert.postauth.VisibleSign
import com.lhordkent.drivealert.postauth.WarningStage
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomAlertRepositoryTest {
    @Test
    fun confirmedAlertIsPersistedWithSignsAndNoSynchronizationDependency() = runTest {
        val dao = FakeAlertDao()
        val repository = RoomAlertRepository(
            alertDao = dao,
            clock = { 2_000L },
            idFactory = { "alert-id" },
            zoneId = ZoneOffset.UTC,
        )

        val id = repository.recordConfirmedAlert(
            ConfirmedAlertInput(
                sessionId = "session-id",
                driverUserId = "driver-a",
                detectedAtEpochMillis = 1_000L,
                warningStageAtDetection = WarningStage.STAGE_2,
                signs = setOf(VisibleSign.PROLONGED_EYE_CLOSURE, VisibleSign.HEAD_NODDING),
                alarmTriggered = true,
                alarmTriggeredAtEpochMillis = 1_000L,
            ),
        )

        val stored = repository.observeAlerts("driver-a").first().single()
        assertEquals("alert-id", id)
        assertEquals(WarningStage.STAGE_2, stored.stage)
        assertEquals(setOf(VisibleSign.PROLONGED_EYE_CLOSURE, VisibleSign.HEAD_NODDING), stored.signs)
        assertTrue(dao.insertedStageSyncRecordIds.isEmpty())
    }
}

private class FakeAlertDao : AlertDao() {
    private val alerts = linkedMapOf<String, AlertEntity>()
    private val signs = linkedMapOf<String, MutableList<AlertSignEntity>>()
    private val state = MutableStateFlow<List<AlertWithSigns>>(emptyList())
    val insertedStageSyncRecordIds = mutableListOf<String>()

    override suspend fun insertEntity(alert: AlertEntity) {
        alerts[alert.alertId] = alert
        publish()
    }

    override suspend fun insertSigns(signs: List<AlertSignEntity>) {
        signs.forEach { sign -> this.signs.getOrPut(sign.alertId) { mutableListOf() }.add(sign) }
        publish()
    }

    override suspend fun getById(driverUserId: String, alertId: String): AlertWithSigns? =
        alerts[alertId]?.takeIf { it.driverUserId == driverUserId }?.let {
            AlertWithSigns(it, signs[alertId].orEmpty())
        }

    override fun observeForDriver(driverUserId: String): Flow<List<AlertWithSigns>> = state
    override fun observeForSession(driverUserId: String, sessionId: String): Flow<List<AlertWithSigns>> = state
    override fun observeLatest(driverUserId: String, limit: Int): Flow<List<AlertWithSigns>> = state

    private fun publish() {
        state.value = alerts.values.map { AlertWithSigns(it, signs[it.alertId].orEmpty()) }
    }
}
