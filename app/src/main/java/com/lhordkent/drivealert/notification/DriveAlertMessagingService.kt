package com.lhordkent.drivealert.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.lhordkent.drivealert.MainActivity
import com.lhordkent.drivealert.R

class DriveAlertMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        TrustedContactDeviceRegistrationWorker.schedule(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val recipientUid = message.data["recipientUid"]?.takeIf(String::isNotBlank) ?: return
        if (FirebaseAuth.getInstance().currentUser?.uid != recipientUid) return
        val driverUid = message.data["driverUid"]?.takeIf(String::isNotBlank) ?: return
        val audible = message.data["audible"] == "true"
        createChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("drivealert://trusted/drivers/${Uri.encode(driverUid)}"),
            this,
            MainActivity::class.java,
        ).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            driverUid.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = message.data["title"] ?: "DriveAlert Stage 3 warning"
        val body = message.data["body"] ?: "New shared warning activity is available."
        val notification = NotificationCompat.Builder(this, if (audible) URGENT_CHANNEL else UPDATE_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (audible) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(!audible)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify("stage3:$driverUid", NOTIFICATION_ID, notification)
    }

    companion object {
        private const val URGENT_CHANNEL = "trusted_stage3_urgent"
        private const val UPDATE_CHANNEL = "trusted_stage3_updates"
        private const val NOTIFICATION_ID = 3_003

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                URGENT_CHANNEL,
                "Trusted Contact Stage 3 alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Audible Stage 3 transitions and delayed summaries" })
            manager.createNotificationChannel(NotificationChannel(
                UPDATE_CHANNEL,
                "Trusted Contact Stage 3 updates",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Silent continuing Stage 3 updates"
                setSound(null, null)
                enableVibration(false)
            })
        }
    }
}
