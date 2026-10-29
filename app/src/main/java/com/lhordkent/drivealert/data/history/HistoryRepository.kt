package com.lhordkent.drivealert.data.history

import androidx.room.withTransaction
import com.lhordkent.drivealert.data.local.DriveAlertDatabase
import com.lhordkent.drivealert.data.local.entity.*
import com.lhordkent.drivealert.postauth.Stage3SyncRecord
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

object HistoryRetention {
    const val DAY_MS = 86_400_000L
    fun validate(days: Int?) { require(days == null || days in 1..3650) }
    fun cutoff(now: Long, days: Int): Long { validate(days); return now - days * DAY_MS }
    fun isShorter(previous: Int?, next: Int?): Boolean = next != null && (previous == null || next < previous)
}

data class SharedHistoryVisibility(val expiredBefore: Long = Long.MIN_VALUE, val dismissed: Set<Pair<String, String>> = emptySet()) {
    fun allows(driverId: String, record: Stage3SyncRecord): Boolean =
        record.eventEpochMillis() >= expiredBefore && (driverId to record.id) !in dismissed
}

fun Stage3SyncRecord.eventEpochMillis(): Long = occurredAtEpochMillis
    ?: occurredAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

class HistoryRepository(private val database: DriveAlertDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao get() = database.historyDao()
    private val preferences get() = database.driverPreferenceDao()

    fun observeVisibility(userId: String): Flow<SharedHistoryVisibility> = combine(
        dao.observeExpiry(userId), dao.observeDismissals(userId),
    ) { expiry, dismissed -> SharedHistoryVisibility(expiry?.expiredBeforeEpochMillis ?: Long.MIN_VALUE,
        dismissed.mapTo(mutableSetOf()) { it.driverUserId to it.recordId }) }

    fun expiryCutoff(days: Int): Long = HistoryRetention.cutoff(clock(), days)
    suspend fun countExpiredAlerts(userId: String, days: Int): Int = dao.countExpiredAlerts(userId, expiryCutoff(days))

    suspend fun setRetention(userId: String, days: Int?) {
        require(userId.isNotBlank()); HistoryRetention.validate(days)
        database.withTransaction {
            val current = preferences.get(userId) ?: DriverPreferenceEntity(userId, StoredWarningSound.DIGITAL_BEEP,
                StoredPreferredVolume.MEDIUM, true, true, true, true, updatedAtEpochMillis = clock())
            preferences.upsert(current.copy(historyRetentionDays = days, updatedAtEpochMillis = clock()))
            cleanupInsideTransaction(userId, days)
        }
    }

    suspend fun cleanup(userId: String) = database.withTransaction {
        cleanupInsideTransaction(userId, preferences.get(userId)?.historyRetentionDays)
    }

    private suspend fun cleanupInsideTransaction(userId: String, days: Int?) {
        if (days == null) return
        val cutoff = HistoryRetention.cutoff(clock(), days)
        dao.deleteExpiredAlerts(userId, cutoff)
        val previous = dao.expiry(userId)?.expiredBeforeEpochMillis ?: Long.MIN_VALUE
        if (cutoff > previous) dao.setExpiry(SharedHistoryExpiry(userId, cutoff))
    }

    suspend fun cleanupAll() {
        preferences.usersWithRetention().forEach { cleanup(it) }
    }

    suspend fun deleteAlert(userId: String, alertId: String): Int = dao.deleteAlert(userId, alertId)
    suspend fun clearAlerts(userId: String): Int = dao.deleteExpiredAlerts(userId, Long.MAX_VALUE)
    suspend fun dismissShared(userId: String, driverId: String, ids: List<String>) {
        require(userId.isNotBlank() && driverId.isNotBlank())
        dao.dismiss(ids.distinct().map { SharedHistoryDismissal(userId, driverId, it) })
    }
}
