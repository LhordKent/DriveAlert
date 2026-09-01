package com.lhordkent.drivealert.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.lhordkent.drivealert.DriveAlertApplication
import java.util.concurrent.TimeUnit

class StageSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val driverUserId = inputData.getString(KEY_DRIVER_USER_ID) ?: return Result.failure()
        if (FirebaseAuth.getInstance().currentUser?.uid != driverUserId) return Result.failure()
        val application = applicationContext as DriveAlertApplication
        return try {
            if (application.container.stageSyncProcessor.synchronize(driverUserId)) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_DRIVER_USER_ID = "driverUserId"
    }
}

class WorkManagerStageSyncScheduler(
    context: Context,
) : StageSyncScheduler {
    private val workManager = WorkManager.getInstance(context)

    override fun schedule(driverUserId: String) {
        val request = OneTimeWorkRequestBuilder<StageSyncWorker>()
            .setInputData(workDataOf(StageSyncWorker.KEY_DRIVER_USER_ID to driverUserId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(tag(driverUserId))
            .build()
        workManager.enqueueUniqueWork(uniqueName(driverUserId), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(driverUserId: String) {
        workManager.cancelAllWorkByTag(tag(driverUserId))
    }

    private fun uniqueName(driverUserId: String) = "stage3-sync-$driverUserId"
    private fun tag(driverUserId: String) = "stage3-sync-user-$driverUserId"
}
