package com.lhordkent.drivealert.data.sync

import com.lhordkent.drivealert.data.local.dao.StageNotificationGroupDao
import com.lhordkent.drivealert.data.local.entity.NotificationDispatchStatus
import com.lhordkent.drivealert.data.local.entity.StageNotificationGroupEntity
import com.lhordkent.drivealert.data.local.entity.StageNotificationGroupRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageNotificationDispatcherTest {

    @Test
    fun `a group the server keeps retrying does not block later groups`() = runTest {
        val dao = FakeGroupDao().apply {
            addPending("stuck", createdAt = 1, records = listOf("r1"))
            addPending("later", createdAt = 2, records = listOf("r2"))
        }
        val dispatcher = StageNotificationDispatcher(dao, { chunk ->
            if (chunk.dispatchGroupId == "stuck") NotificationDispatchResult.AwaitingMore else NotificationDispatchResult.Delivered
        }, clock = { 100 })

        val finished = dispatcher.dispatchPending("driver")

        assertFalse(finished)
        assertEquals(NotificationDispatchStatus.DELIVERED, dao.groups.getValue("later").status)
        assertEquals(NotificationDispatchStatus.PENDING, dao.groups.getValue("stuck").status)
        assertEquals(1, dao.groups.getValue("stuck").attemptCount)
    }

    @Test
    fun `a group that never completes becomes terminal after the attempt limit`() = runTest {
        val dao = FakeGroupDao().apply { addPending("stuck", createdAt = 1, records = listOf("r1")) }
        val dispatcher = StageNotificationDispatcher(dao, { NotificationDispatchResult.Retryable("HTTP_503") }, clock = { 100 })

        var finished = false
        repeat(StageNotificationDispatcher.MAX_ATTEMPTS) { finished = dispatcher.dispatchPending("driver") }

        assertTrue(finished)
        val group = dao.groups.getValue("stuck")
        assertEquals(NotificationDispatchStatus.TERMINAL, group.status)
        assertEquals("RETRY_EXHAUSTED:HTTP_503", group.lastErrorCode)
    }

    @Test
    fun `disabled notifications stop dispatch without consuming attempts`() = runTest {
        val dao = FakeGroupDao().apply { addPending("group", createdAt = 1, records = listOf("r1")) }
        val dispatcher = StageNotificationDispatcher(dao, { NotificationDispatchResult.Disabled }, clock = { 100 })

        assertTrue(dispatcher.dispatchPending("driver"))
        assertEquals(0, dao.groups.getValue("group").attemptCount)
    }
}

private class FakeGroupDao : StageNotificationGroupDao() {
    val groups = linkedMapOf<String, StageNotificationGroupEntity>()
    private val records = mutableMapOf<String, MutableList<String>>()

    fun addPending(id: String, createdAt: Long, records: List<String>) {
        groups[id] = StageNotificationGroupEntity(id, "driver", NotificationDispatchStatus.PENDING, records.size,
            (records.size + 19) / 20, 0, null, null, createdAt, null)
        this.records[id] = records.toMutableList()
    }

    override suspend fun insertGroup(entity: StageNotificationGroupEntity) { groups[entity.dispatchGroupId] = entity }
    override suspend fun insertRecord(entity: StageNotificationGroupRecordEntity): Long {
        records.getOrPut(entity.dispatchGroupId) { mutableListOf() } += entity.stageSyncRecordId
        return 1
    }
    override suspend fun openGroup(driverUserId: String) =
        groups.values.firstOrNull { it.driverUserId == driverUserId && it.status == NotificationDispatchStatus.OPEN }
    override suspend fun pendingEntities(driverUserId: String, limit: Int) = groups.values
        .filter { it.driverUserId == driverUserId && it.status == NotificationDispatchStatus.PENDING }
        .sortedBy { it.createdAtEpochMillis }.take(limit)
    override suspend fun recordIds(groupId: String) = records[groupId].orEmpty()
    override suspend fun recordCount(groupId: String) = records[groupId].orEmpty().size
    override suspend fun closeGroup(groupId: String): Int {
        val group = groups[groupId]?.takeIf { it.status == NotificationDispatchStatus.OPEN } ?: return 0
        groups[groupId] = group.copy(status = NotificationDispatchStatus.PENDING)
        return 1
    }
    override suspend fun recordAttempt(groupId: String, attemptCount: Int, attemptedAt: Long, errorCode: String?): Int {
        groups[groupId] = groups.getValue(groupId).copy(attemptCount = attemptCount, lastAttemptAtEpochMillis = attemptedAt, lastErrorCode = errorCode)
        return 1
    }
    override suspend fun complete(groupId: String, status: NotificationDispatchStatus, completedAt: Long, errorCode: String?): Int {
        groups[groupId] = groups.getValue(groupId).copy(status = status, completedAtEpochMillis = completedAt, lastErrorCode = errorCode)
        return 1
    }
}
