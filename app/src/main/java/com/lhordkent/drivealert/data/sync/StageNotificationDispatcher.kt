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
                NotificationDispatchResult.AwaitingMore -> {
                    dao.recordAttempt(group.dispatchGroupId, group.attemptCount + 1, now, "AWAITING_COMPLETION")
                    return false
                }
                NotificationDispatchResult.Disabled -> return true
                is NotificationDispatchResult.Retryable -> {
                    dao.recordAttempt(group.dispatchGroupId, group.attemptCount + 1, now, result.code)
                    return false
                }
            }
        }
        return dao.pendingGroups(driverUserId, 1).isEmpty()
    }

    companion object {
        const val MAX_RECORDS_PER_CHUNK = 20
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
