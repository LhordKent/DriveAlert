package com.lhordkent.drivealert.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lhordkent.drivealert.R
import com.lhordkent.drivealert.monitoring.VisibilityIssue

interface DriverVisibilityNotificationGateway {
    fun post(issue: VisibilityIssue): NotificationDeliveryStatus
    fun cancel()
}

class DriverVisibilityNotificationCoordinator(
    private val gateway: DriverVisibilityNotificationGateway,
) {
    fun notify(issue: VisibilityIssue, enabled: Boolean): NotificationDeliveryStatus {
        if (!enabled) return NotificationDeliveryStatus.DISABLED_BY_DRIVER
        return runCatching { gateway.post(issue) }.getOrElse { NotificationDeliveryStatus.FAILED }
    }

    fun clear() {
        runCatching(gateway::cancel)
    }
}

class AndroidDriverVisibilityNotificationGateway(
    private val context: Context,
) : DriverVisibilityNotificationGateway {
    override fun post(issue: VisibilityIssue): NotificationDeliveryStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return NotificationDeliveryStatus.PERMISSION_DENIED
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return NotificationDeliveryStatus.SYSTEM_DISABLED
        val text = when (issue) {
            VisibilityIssue.LOWER_FACE_OBSTRUCTED ->
                "Mouth visibility is blocked. Yawning monitoring is unavailable."
            VisibilityIssue.EYE_REGION_OBSTRUCTED ->
                "Eye visibility is blocked. Eye-closure monitoring is unavailable."
            VisibilityIssue.BOTH_REGIONS_OBSTRUCTED ->
                "Monitoring is paused. Restore a clear view of your eyes and mouth."
            VisibilityIssue.FACE_UNAVAILABLE ->
                "Monitoring is paused. Restore a clear camera view of your face."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DriveAlert camera view blocked")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return NotificationDeliveryStatus.POSTED
    }

    override fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "monitoring_visibility_alerts"
        private const val NOTIFICATION_ID = 0x44564953

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoring visibility alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts when the DriveAlert camera cannot monitor part or all of the driver's face"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
