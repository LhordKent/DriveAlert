package com.lhordkent.drivealert.data.history

import android.content.Context
import androidx.work.*
import com.lhordkent.drivealert.DriveAlertApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class HistoryCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        (applicationContext as DriveAlertApplication).container.historyRepository.cleanupAll()
        (applicationContext as DriveAlertApplication).container.monitoringLogRepository.cleanupAll()
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (_: Exception) { Result.retry() }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("history-retention",
                ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<HistoryCleanupWorker>(1, TimeUnit.HOURS).build())
        }
    }
}
