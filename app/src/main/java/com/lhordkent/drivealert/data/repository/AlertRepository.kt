package com.lhordkent.drivealert.data.repository

import com.lhordkent.drivealert.data.local.dao.AlertDao
import com.lhordkent.drivealert.data.local.entity.AlertEntity
import com.lhordkent.drivealert.data.local.entity.AlertSignEntity
import com.lhordkent.drivealert.data.local.entity.AlertWithSigns
import com.lhordkent.drivealert.data.local.entity.StoredVisibleSign
import com.lhordkent.drivealert.data.local.entity.StoredWarningStage
import com.lhordkent.drivealert.postauth.AlertEvent
import com.lhordkent.drivealert.postauth.VisibleSign
import com.lhordkent.drivealert.postauth.WarningStage
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ConfirmedAlertInput(
    val sessionId: String,
    val driverUserId: String,
    val detectedAtEpochMillis: Long,
    val warningStageAtDetection: WarningStage,
    val signs: Set<VisibleSign>,
    val alarmTriggered: Boolean,
    val alarmTriggeredAtEpochMillis: Long?,
)

interface AlertRepository {
    fun observeAlerts(driverUserId: String): Flow<List<AlertEvent>>
    suspend fun getAlert(driverUserId: String, alertId: String): AlertEvent?
    suspend fun recordConfirmedAlert(input: ConfirmedAlertInput): String
    suspend fun addSigns(driverUserId: String, alertId: String, signs: Set<VisibleSign>)
}

class RoomAlertRepository(
    private val alertDao: AlertDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : AlertRepository {
    override fun observeAlerts(driverUserId: String): Flow<List<AlertEvent>> =
        alertDao.observeForDriver(driverUserId).map { alerts -> alerts.map { it.toDomain(zoneId) } }

    override suspend fun getAlert(driverUserId: String, alertId: String): AlertEvent? =
        alertDao.getById(driverUserId, alertId)?.toDomain(zoneId)

    override suspend fun recordConfirmedAlert(input: ConfirmedAlertInput): String {
        require(input.driverUserId.isNotBlank()) { "A confirmed Alert must belong to an authenticated Driver UID." }
        require(input.signs.isNotEmpty()) { "A confirmed Alert must include at least one selected visible sign." }
        require(input.alarmTriggered || input.alarmTriggeredAtEpochMillis == null)
        val alertId = idFactory()
        val entity = AlertEntity(
            alertId = alertId,
            sessionId = input.sessionId,
            driverUserId = input.driverUserId,
            detectedAtEpochMillis = input.detectedAtEpochMillis,
            warningStageAtDetection = input.warningStageAtDetection.toStored(),
            alarmTriggered = input.alarmTriggered,
            alarmTriggeredAtEpochMillis = input.alarmTriggeredAtEpochMillis,
            createdAtEpochMillis = clock(),
        )
        alertDao.insert(entity, input.signs.mapTo(linkedSetOf()) { AlertSignEntity(alertId, it.toStored()) })
        return alertId
    }

    override suspend fun addSigns(driverUserId: String, alertId: String, signs: Set<VisibleSign>) {
        require(signs.isNotEmpty())
        requireNotNull(alertDao.getById(driverUserId, alertId))
        alertDao.addSigns(alertId, signs.mapTo(linkedSetOf()) { AlertSignEntity(alertId, it.toStored()) })
    }
}

private fun AlertWithSigns.toDomain(zoneId: ZoneId) = AlertEvent(
    id = alert.alertId,
    occurredAt = Instant.ofEpochMilli(alert.detectedAtEpochMillis).atZone(zoneId).toLocalDateTime(),
    signs = signs.mapTo(linkedSetOf()) { it.signType.toDomain() },
    stage = alert.warningStageAtDetection.toDomain(),
    sessionId = alert.sessionId,
    alarmTriggered = alert.alarmTriggered,
    alarmTriggeredAt = alert.alarmTriggeredAtEpochMillis?.let {
        Instant.ofEpochMilli(it).atZone(zoneId).toLocalDateTime()
    },
)

private fun WarningStage.toStored() = when (this) {
    WarningStage.STAGE_1 -> StoredWarningStage.STAGE_1
    WarningStage.STAGE_2 -> StoredWarningStage.STAGE_2
    WarningStage.STAGE_3 -> StoredWarningStage.STAGE_3
}

private fun StoredWarningStage.toDomain() = when (this) {
    StoredWarningStage.STAGE_1 -> WarningStage.STAGE_1
    StoredWarningStage.STAGE_2 -> WarningStage.STAGE_2
    StoredWarningStage.STAGE_3 -> WarningStage.STAGE_3
}

private fun VisibleSign.toStored() = when (this) {
    VisibleSign.PROLONGED_EYE_CLOSURE -> StoredVisibleSign.PROLONGED_EYE_CLOSURE
    VisibleSign.YAWNING -> StoredVisibleSign.YAWNING
    VisibleSign.HEAD_NODDING -> StoredVisibleSign.HEAD_NODDING
}

private fun StoredVisibleSign.toDomain() = when (this) {
    StoredVisibleSign.PROLONGED_EYE_CLOSURE -> VisibleSign.PROLONGED_EYE_CLOSURE
    StoredVisibleSign.YAWNING -> VisibleSign.YAWNING
    StoredVisibleSign.HEAD_NODDING -> VisibleSign.HEAD_NODDING
}
