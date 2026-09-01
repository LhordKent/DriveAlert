package com.lhordkent.drivealert.data.repository

import com.lhordkent.drivealert.data.local.dao.MonitoringSessionDao
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionStatus
import com.lhordkent.drivealert.data.local.entity.StoredWarningStage
import com.lhordkent.drivealert.postauth.MonitoringSessionSummary
import com.lhordkent.drivealert.postauth.WarningStage
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class StartMonitoringSessionInput(
    val driverUserId: String,
    val calibrationId: String,
    val deviceId: String,
    val startedAtEpochMillis: Long,
)

interface MonitoringSessionRepository {
    fun observeSessions(driverUserId: String): Flow<List<MonitoringSessionSummary>>
    suspend fun start(input: StartMonitoringSessionInput): String
    suspend fun pause(driverUserId: String, sessionId: String)
    suspend fun resume(driverUserId: String, sessionId: String)
    suspend fun finish(
        driverUserId: String,
        sessionId: String,
        status: MonitoringSessionStatus,
        endedAtEpochMillis: Long,
    )
}

class RoomMonitoringSessionRepository(
    private val dao: MonitoringSessionDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : MonitoringSessionRepository {
    override fun observeSessions(driverUserId: String): Flow<List<MonitoringSessionSummary>> =
        dao.observeForDriver(driverUserId).map { sessions ->
            sessions.map { session ->
                MonitoringSessionSummary(
                    id = session.sessionId,
                    startedAt = Instant.ofEpochMilli(session.startedAtEpochMillis).atZone(zoneId).toLocalDateTime(),
                    endedAt = session.endedAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDateTime() },
                    status = session.status.toDomainLabel(),
                    highestStage = session.highestWarningStage?.toDomain(),
                )
            }
        }

    override suspend fun start(input: StartMonitoringSessionInput): String {
        require(input.driverUserId.isNotBlank())
        require(input.calibrationId.isNotBlank())
        require(input.deviceId.isNotBlank())
        val id = idFactory()
        val now = clock()
        dao.insert(
            com.lhordkent.drivealert.data.local.entity.MonitoringSessionEntity(
                sessionId = id,
                driverUserId = input.driverUserId,
                calibrationId = input.calibrationId,
                deviceId = input.deviceId,
                startedAtEpochMillis = input.startedAtEpochMillis,
                endedAtEpochMillis = null,
                status = MonitoringSessionStatus.ACTIVE,
                highestWarningStage = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        return id
    }

    override suspend fun pause(driverUserId: String, sessionId: String) {
        transition(driverUserId, sessionId, MonitoringSessionStatus.ACTIVE, MonitoringSessionStatus.PAUSED)
    }

    override suspend fun resume(driverUserId: String, sessionId: String) {
        transition(driverUserId, sessionId, MonitoringSessionStatus.PAUSED, MonitoringSessionStatus.ACTIVE)
    }

    override suspend fun finish(
        driverUserId: String,
        sessionId: String,
        status: MonitoringSessionStatus,
        endedAtEpochMillis: Long,
    ) {
        require(status == MonitoringSessionStatus.COMPLETED || status == MonitoringSessionStatus.INTERRUPTED)
        val current = requireNotNull(dao.getById(driverUserId, sessionId))
        require(current.status == MonitoringSessionStatus.ACTIVE || current.status == MonitoringSessionStatus.PAUSED)
        dao.update(
            current.copy(
                status = status,
                endedAtEpochMillis = endedAtEpochMillis,
                updatedAtEpochMillis = clock(),
            ),
        )
    }

    private suspend fun transition(
        driverUserId: String,
        sessionId: String,
        expected: MonitoringSessionStatus,
        target: MonitoringSessionStatus,
    ) {
        val current = requireNotNull(dao.getById(driverUserId, sessionId))
        require(current.status == expected)
        dao.update(current.copy(status = target, updatedAtEpochMillis = clock()))
    }
}

private fun MonitoringSessionStatus.toDomainLabel(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun StoredWarningStage.toDomain() = when (this) {
    StoredWarningStage.STAGE_1 -> WarningStage.STAGE_1
    StoredWarningStage.STAGE_2 -> WarningStage.STAGE_2
    StoredWarningStage.STAGE_3 -> WarningStage.STAGE_3
}
