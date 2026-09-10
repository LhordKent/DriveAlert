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
import com.lhordkent.drivealert.postauth.VisibleSign
import com.lhordkent.drivealert.postauth.WarningStage

data class DriverWarningMessage(val alertId: String, val stage: WarningStage, val signs: Set<VisibleSign>)

enum class NotificationDeliveryStatus { POSTED, DISABLED_BY_DRIVER, PERMISSION_DENIED, SYSTEM_DISABLED, FAILED }

interface DriverWarningNotificationGateway {
    fun postOrUpdate(message: DriverWarningMessage): NotificationDeliveryStatus
}

class DriverWarningNotificationCoordinator(private val gateway: DriverWarningNotificationGateway) {
    fun notifyConfirmedAlert(
        alertId: String,
        stage: WarningStage,
        signs: Set<VisibleSign>,
        enabled: Boolean,
    ): NotificationDeliveryStatus {
        if (!enabled) return NotificationDeliveryStatus.DISABLED_BY_DRIVER
        return runCatching { gateway.postOrUpdate(DriverWarningMessage(alertId, stage, signs)) }
            .getOrElse { NotificationDeliveryStatus.FAILED }
    }
}

class AndroidDriverWarningNotificationGateway(private val context: Context) : DriverWarningNotificationGateway {
    override fun postOrUpdate(message: DriverWarningMessage): NotificationDeliveryStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return NotificationDeliveryStatus.PERMISSION_DENIED
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return NotificationDeliveryStatus.SYSTEM_DISABLED
        val text = driverWarningText(message.stage, message.signs)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("DriveAlert: ${message.stage.label}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(message.alertId.hashCode(), notification)
        return NotificationDeliveryStatus.POSTED
    }

    companion object {
        const val CHANNEL_ID = "driver_warning_alerts"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "Driver warning alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Confirmed DriveAlert warning events"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

fun driverWarningText(stage: WarningStage, signs: Set<VisibleSign>): String {
    val labels = signs.sortedBy { it.ordinal }.map { it.label }
    val detected = when (labels.size) {
        0 -> "Confirmed warning sign detected."
        1 -> "${labels.single()} detected."
        2 -> "${labels[0]} and ${labels[1]} detected."
        else -> "${labels.dropLast(1).joinToString()}, and ${labels.last()} detected."
    }
    val advisory = when (stage) {
        WarningStage.STAGE_1 -> "Stay alert and consider resting when safe."
        WarningStage.STAGE_2 -> "Pull over and rest when it is safe to do so."
        WarningStage.STAGE_3 -> "Pull over and take a rest break when it is safe to do so."
    }
    return "$detected $advisory"
}
