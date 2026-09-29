package com.lhordkent.drivealert.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.lhordkent.drivealert.DriveAlertApplication

class TrustedContactDeviceRegistrationWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()
        val application = applicationContext as DriveAlertApplication
        if (!application.container.trustedContactDeviceRegistrar.isConfigured) return Result.success()
        return if (runCatching {
                application.container.trustedContactDeviceRegistrar.registerCurrentDevice()
            }.getOrDefault(false)
        ) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "trusted-contact-device-registration"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<TrustedContactDeviceRegistrationWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
