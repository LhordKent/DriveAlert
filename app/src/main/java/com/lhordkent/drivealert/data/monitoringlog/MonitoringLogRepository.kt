package com.lhordkent.drivealert.data.monitoringlog

import androidx.room.withTransaction
import com.lhordkent.drivealert.data.local.DriveAlertDatabase
import com.lhordkent.drivealert.detection.model.ConfirmedSignEvent
import com.lhordkent.drivealert.monitoring.WarningDeliveryStatus
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MonitoringLogRepository(private val database: DriveAlertDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    val dao = database.monitoringLogDao()
    suspend fun begin(uid: String, id: String, time: Long, recovered: Boolean) = database.withTransaction {
        // Recovered legacy sessions have no log: never fabricate their missing history.
        if (recovered && dao.get(uid, id) == null) return@withTransaction
        if (!recovered && dao.insert(MonitoringLog(id, uid, time)) == -1L) return@withTransaction
        if (dao.get(uid, id)?.endedAt != null) return@withTransaction
        if (recovered) dao.gap(uid, id)
        dao.insert(MonitoringLogEvent(eventId = UUID.randomUUID().toString(), sessionId = id,
            driverUserId = uid, occurredAt = if (recovered) clock() else time, kind = if (recovered) "RECOVERED" else "START"))
    }
    suspend fun append(event: MonitoringLogEvent) = database.withTransaction {
        val header = dao.get(event.driverUserId, event.sessionId) ?: return@withTransaction
        if (header.endedAt == null) dao.insert(event)
    }
    suspend fun end(uid: String, id: String, time: Long, status: String) = database.withTransaction {
        if (dao.end(uid, id, time, status) > 0) {
            dao.insert(MonitoringLogEvent(eventId = UUID.randomUUID().toString(), sessionId = id,
                driverUserId = uid, occurredAt = time, kind = status))
        }
    }
    fun cutoff(days: Int?): Long? {
        require(days == null || days in 1..3650)
        return days?.let { clock() - it * 86_400_000L }
    }
    suspend fun saveRetention(uid: String, days: Int?) = database.withTransaction {
        val before = cutoff(days)
        dao.save(MonitoringLogPreference(uid, days))
        if (before != null) dao.deleteCompleted(uid, before)
    }
    suspend fun confirmDeletion(uid: String, before: Long?, expectedCount: Int, savePreference: Boolean, days: Int?): Boolean = database.withTransaction {
        cutoff(days) // Validate before mutating either table.
        if (dao.deletionCount(uid, before) != expectedCount) return@withTransaction false
        if (savePreference) dao.save(MonitoringLogPreference(uid, days))
        if (!savePreference || days != null) dao.deleteCompleted(uid, before)
        true
    }
    suspend fun cleanupAll() {
        dao.preferences().forEach { pref -> cutoff(pref.retentionDays)?.let { dao.deleteCompleted(pref.driverUserId, it) } }
    }
    suspend fun recover(processId: String) {
        dao.recoverRequests(processId)
        // Repair an end write lost to process death after the core session finished.
        dao.openLogs().forEach { log ->
            val core = database.monitoringSessionDao().getById(log.driverUserId, log.sessionId)
            core?.endedAtEpochMillis?.let { time ->
                dao.gap(log.driverUserId, log.sessionId)
                end(log.driverUserId, log.sessionId, time, core.status.name)
            }
        }
    }
}

/** Ordered application-lifetime writer. Detection and hardware output never await disk IO. */
class MonitoringLogWriter(private val repository: MonitoringLogRepository, scope: CoroutineScope) {
    private data class Write(val uid: String, val id: String, val action: suspend () -> Unit)
    private val queue = Channel<Write>(256)
    private val gaps = java.util.concurrent.ConcurrentHashMap.newKeySet<Pair<String, String>>()
    private val failures = MutableStateFlow<Set<String>>(emptySet())
    val failedDrivers = failures.asStateFlow()
    private val processId = UUID.randomUUID().toString()
    init {
        scope.launch {
            try { repository.recover(processId) } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* Next app launch retries. */ }
            for (write in queue) {
                try {
                    write.action()
                    if (gaps.contains(write.uid to write.id)) repository.dao.gap(write.uid, write.id)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { recordGap(write.uid, write.id) }
            }
        }
    }
    private fun recordGap(uid: String, id: String) {
        gaps.add(uid to id)
        synchronized(failures) { failures.value = failures.value + uid }
    }
    private fun write(uid: String, id: String, action: suspend () -> Unit) {
        if (!queue.trySend(Write(uid, id, action)).isSuccess) recordGap(uid, id)
    }
    fun begin(uid: String, id: String, time: Long, recovered: Boolean) = write(uid, id) { repository.begin(uid, id, time, recovered) }
    fun end(uid: String, id: String, time: Long, status: String) = write(uid, id) {
        repository.end(uid, id, time, status)
        repository.cleanupAll()
    }
    fun sign(uid: String, id: String, time: Long, sign: ConfirmedSignEvent) {
        val context = sign.temporalContext.toMap()
        val event = MonitoringLogEvent(eventId = UUID.randomUUID().toString(), sessionId = id, driverUserId = uid,
            occurredAt = time, kind = "SIGN", sign = sign.type.name, measuredValue = sign.measuredValue,
            threshold = sign.threshold, durationMs = sign.qualifyingDurationMs,
            requiredDurationMs = (context["requiredDurationMs"] ?: context["windowMs"])?.toLong(),
            downwardRatio = context["downwardRatio"], ratioThreshold = context["downwardRatioThreshold"],
            calibrationId = sign.calibrationId, calibrationVersion = sign.calibrationVersion)
        write(uid, id) { repository.append(event) }
    }
    fun request(uid: String, id: String, time: Long, stage: Int, repeated: Boolean): String {
        val requestId = UUID.randomUUID().toString()
        write(uid, id) { repository.append(MonitoringLogEvent(eventId = requestId, sessionId = id, driverUserId = uid,
            occurredAt = time, kind = "ALARM", stage = stage, repeated = repeated, result = "PENDING", processId = processId)) }
        return requestId
    }
    fun result(uid: String, id: String, requestId: String, time: Long, status: WarningDeliveryStatus?) = write(uid, id) {
        repository.dao.result(uid, requestId, when (status) {
            WarningDeliveryStatus.DELIVERED -> "ACCEPTED"
            WarningDeliveryStatus.FAILED -> "FAILED"
            WarningDeliveryStatus.HARDWARE_PENDING -> "UNAVAILABLE"
            else -> "UNKNOWN_INTERRUPTED"
        }, time)
    }
}
