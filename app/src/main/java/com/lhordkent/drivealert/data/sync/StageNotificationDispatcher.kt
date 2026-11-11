package com.lhordkent.drivealert.data.sync

import com.lhordkent.drivealert.data.local.dao.StageNotificationGroupDao
import com.lhordkent.drivealert.data.local.entity.NotificationDispatchStatus

data class NotificationDispatchChunk(
    val dispatchGroupId: String,
    val batchId: String,
    val chunkIndex: Int,
    val chunkCount: Int,
    val recordIds: List<String>,
)

sealed interface NotificationDispatchResult {
    data object Delivered : NotificationDispatchResult
    data object AwaitingMore : NotificationDispatchResult
    data object Disabled : NotificationDispatchResult
    data class Retryable(val code: String) : NotificationDispatchResult
    data class Terminal(val code: String) : NotificationDispatchResult
}

fun interface StageNotificationRemoteDataSource {
    suspend fun dispatch(chunk: NotificationDispatchChunk): NotificationDispatchResult
}

class StageNotificationDispatcher(
    private val dao: StageNotificationGroupDao,
    private val remote: StageNotificationRemoteDataSource,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun dispatchPending(driverUserId: String, groupLimit: Int = 10): Boolean {
        for (group in dao.pendingGroups(driverUserId, groupLimit)) {
            val chunks = buildNotificationDispatchChunks(group.dispatchGroupId, group.recordIds)
            if (chunks.isEmpty()) {
                dao.complete(group.dispatchGroupId, NotificationDispatchStatus.TERMINAL, clock(), "EMPTY_GROUP")
                continue
            }
            var result: NotificationDispatchResult = NotificationDispatchResult.AwaitingMore
            for (chunk in chunks) {
                result = remote.dispatch(chunk)
                if (result is NotificationDispatchResult.Retryable ||
                    result is NotificationDispatchResult.Terminal ||
                    result is NotificationDispatchResult.Disabled
                ) break
            }
            val now = clock()
            when (result) {
                NotificationDispatchResult.Delivered ->
                    dao.complete(group.dispatchGroupId, NotificationDispatchStatus.DELIVERED, now, null)
                is NotificationDispatchResult.Terminal ->
                    dao.complete(group.dispatchGroupId, NotificationDispatchStatus.TERMINAL, now, result.code)
                NotificationDispatchResult.Disabled -> return true
                // An unfinished group must not block later groups, and must not retry forever.
                NotificationDispatchResult.AwaitingMore -> retryLater(group.dispatchGroupId, group.attemptCount, now, "AWAITING_COMPLETION")
                is NotificationDispatchResult.Retryable -> retryLater(group.dispatchGroupId, group.attemptCount, now, result.code)
            }
        }
        return dao.pendingGroups(driverUserId, 1).isEmpty()
    }

    private suspend fun retryLater(groupId: String, previousAttempts: Int, now: Long, code: String) {
        val attempts = previousAttempts + 1
        if (attempts >= MAX_ATTEMPTS) {
            dao.complete(groupId, NotificationDispatchStatus.TERMINAL, now, "RETRY_EXHAUSTED:$code".take(80))
        } else {
            dao.recordAttempt(groupId, attempts, now, code)
        }
    }

    companion object {
        const val MAX_RECORDS_PER_CHUNK = 20

        /** With WorkManager's 30 s exponential backoff (capped at 5 h) this spans roughly a day and a half. */
        const val MAX_ATTEMPTS = 12
    }
}

internal fun buildNotificationDispatchChunks(
    dispatchGroupId: String,
    recordIds: List<String>,
): List<NotificationDispatchChunk> {
    val chunks = recordIds.chunked(StageNotificationDispatcher.MAX_RECORDS_PER_CHUNK)
    return chunks.mapIndexed { index, ids ->
        NotificationDispatchChunk(
            dispatchGroupId = dispatchGroupId,
            batchId = "$dispatchGroupId:$index",
            chunkIndex = index,
            chunkCount = chunks.size,
            recordIds = ids,
        )
    }
}
