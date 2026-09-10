package com.lhordkent.drivealert.data.repository

import com.lhordkent.drivealert.data.local.dao.MonitoringSessionDao
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionEntity
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionStatus
import com.lhordkent.drivealert.data.local.entity.StoredWarningStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitoringSessionRepositoryTest {
    @Test
    fun explicitStartPauseResumeAndCompletionUseManuscriptStatuses() = runTest {
        val dao = FakeMonitoringSessionDao()
        val repository = RoomMonitoringSessionRepository(
            dao = dao,
            clock = { 2_000L },
            idFactory = { "session-1" },
        )

        repository.start(StartMonitoringSessionInput("driver-a", "calibration-1", "device-1", 1_000L))
        assertEquals(MonitoringSessionStatus.ACTIVE, dao.current?.status)
        assertNull(dao.current?.endedAtEpochMillis)

        repository.pause("driver-a", "session-1")
        assertEquals(MonitoringSessionStatus.PAUSED, dao.current?.status)

        repository.resume("driver-a", "session-1")
        repository.updateHighestStage("driver-a", "session-1", com.lhordkent.drivealert.postauth.WarningStage.STAGE_2)
        assertEquals(StoredWarningStage.STAGE_2, dao.current?.highestWarningStage)
        repository.finish("driver-a", "session-1", MonitoringSessionStatus.COMPLETED, 3_000L)
        assertEquals(MonitoringSessionStatus.COMPLETED, dao.current?.status)
        assertEquals(3_000L, dao.current?.endedAtEpochMillis)
        assertEquals("calibration-1", dao.current?.calibrationId)
        assertEquals("device-1", dao.current?.deviceId)
    }
}

private class FakeMonitoringSessionDao : MonitoringSessionDao {
    var current: MonitoringSessionEntity? = null
    private val state = MutableStateFlow<List<MonitoringSessionEntity>>(emptyList())

    override suspend fun insert(session: MonitoringSessionEntity) {
        current = session
        state.value = listOf(session)
    }

    override suspend fun update(session: MonitoringSessionEntity) {
        current = session
        state.value = listOf(session)
    }

    override suspend fun getById(driverUserId: String, sessionId: String) =
        current?.takeIf { it.driverUserId == driverUserId && it.sessionId == sessionId }

    override fun observeForDriver(driverUserId: String): Flow<List<MonitoringSessionEntity>> = state
    override fun observeLatestForDriver(driverUserId: String): Flow<MonitoringSessionEntity?> =
        MutableStateFlow(current)
}
