package com.lhordkent.drivealert.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lhordkent.drivealert.DriveAlertApplication
import com.lhordkent.drivealert.MainActivity
import com.lhordkent.drivealert.R
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionStatus
import com.lhordkent.drivealert.detection.DriverVisionRuntime
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.postauth.PreferredVolume
import com.lhordkent.drivealert.postauth.WarningSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class DriveAlertMonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val applicationContainer get() = (application as DriveAlertApplication).container
    private val runtime: DriverVisionRuntime get() = applicationContainer.driverVisionRuntime
    private val recoveryStore by lazy { MonitoringRecoveryStore(this) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockRenewJob: Job? = null
    private var stateJob: Job? = null
    private var startInProgress = false

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        stateJob = serviceScope.launch {
            runtime.state.collectLatest { state ->
                if (wakeLock?.isHeld == true) {
                    val status = when {
                        state.streamState != StreamConnectionState.CONNECTED -> state.streamState.name.lowercase()
                        state.lighting.recovering -> "Monitoring paused; checking visibility"
                        state.visibilityIssue == VisibilityIssue.INSUFFICIENT_LIGHTING ||
                            state.lighting.quality == LightingQuality.INSUFFICIENT -> "Monitoring paused; insufficient lighting"
                        state.lighting.quality == LightingQuality.UNKNOWN -> "Monitoring paused; checking visibility"
                        else -> "connected"
                    }
                    showForegroundNotification(status)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showForegroundNotification("starting")
        acquireWakeLock()
        when (intent?.action) {
            ACTION_START -> startFromIntent(intent)
            ACTION_STOP -> stopMonitoring(intent.getBooleanExtra(EXTRA_INTERRUPTED, false))
            null -> recoverAfterProcessDeath()
            else -> finishService(clearRecovery = false)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startFromIntent(intent: Intent) {
        if (!monitoringStartAllowed(runtime.state.value.monitoring.isActive, startInProgress)) return
        recoveryStore.load()?.let { stale ->
            interruptStaleSession(stale)
            recoveryStore.clear()
        }
        val driverId = intent.getStringExtra(EXTRA_DRIVER_ID)
        if (driverId.isNullOrBlank()) {
            failStart("No driver is signed in.")
            return
        }
        startMonitoring(
            driverId = driverId,
            sound = enumExtra(intent, EXTRA_SOUND, WarningSound.DIGITAL_BEEP),
            volume = enumExtra(intent, EXTRA_VOLUME, PreferredVolume.MEDIUM),
            notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS, true),
            accessoryMode = enumExtra(intent, EXTRA_ACCESSORY, DriverAccessoryMode.NONE),
        )
    }

    private fun recoverAfterProcessDeath() {
        val recovery = recoveryStore.load()
        when (recoveryDisposition(recovery?.bootCount, recoveryStore.currentBootCount())) {
            RecoveryDisposition.NONE -> return finishService(clearRecovery = true)
            RecoveryDisposition.INTERRUPT_AFTER_REBOOT -> {
                recovery?.let { finishStoredSession(it, MonitoringSessionStatus.INTERRUPTED) }
                    ?: finishService(clearRecovery = true)
                return
            }
            RecoveryDisposition.RECOVER_SAME_BOOT -> Unit
        }
        checkNotNull(recovery)
        if (!monitoringStartAllowed(runtime.state.value.monitoring.isActive, startInProgress)) return
        startInProgress = true
        runtime.bindDriver(recovery.driverUserId)
        runtime.startVision()
        serviceScope.launch {
            runCatching {
                withTimeout(RECOVERY_TIMEOUT_MS) {
                    runtime.state.first {
                        it.deviceConfigured && it.activeCalibration != null &&
                            it.streamState == StreamConnectionState.CONNECTED
                    }
                }
            }.onSuccess {
                startMonitoring(
                    driverId = recovery.driverUserId,
                    sound = recovery.sound,
                    volume = recovery.volume,
                    notificationsEnabled = recovery.notificationsEnabled,
                    accessoryMode = recovery.accessoryMode,
                    existingSessionId = recovery.sessionId,
                    existingStartedAtEpochMillis = recovery.startedAtEpochMillis,
                )
            }.onFailure {
                finishStoredSession(recovery, MonitoringSessionStatus.INTERRUPTED)
            }
        }
    }

    private fun startMonitoring(
        driverId: String,
        sound: WarningSound,
        volume: PreferredVolume,
        notificationsEnabled: Boolean,
        accessoryMode: DriverAccessoryMode,
        existingSessionId: String? = null,
        existingStartedAtEpochMillis: Long? = null,
    ) {
        startInProgress = true
        runtime.startMonitoring(
            sound = sound,
            volume = volume,
            notificationsEnabled = notificationsEnabled,
            accessoryMode = accessoryMode,
            existingSessionId = existingSessionId,
            existingStartedAtEpochMillis = existingStartedAtEpochMillis,
            onStarted = { sessionId, startedAt ->
                startInProgress = false
                recoveryStore.save(
                    MonitoringRecoveryRecord(
                        sessionId = sessionId,
                        driverUserId = driverId,
                        startedAtEpochMillis = startedAt,
                        sound = sound,
                        volume = volume,
                        notificationsEnabled = notificationsEnabled,
                        accessoryMode = accessoryMode,
                        bootCount = recoveryStore.currentBootCount(),
                    ),
                )
                showForegroundNotification(
                    if (runtime.state.value.lighting.quality == LightingQuality.USABLE) "active"
                    else "Monitoring paused; checking visibility",
                )
            },
            onFailure = { error ->
                startInProgress = false
                failStart(error.message ?: "Monitoring could not start.")
            },
        )
    }

    private fun stopMonitoring(interrupted: Boolean) {
        startInProgress = false
        val recovery = recoveryStore.load()
        if (!runtime.state.value.monitoring.isActive && recovery != null) {
            finishStoredSession(
                recovery,
                if (interrupted) MonitoringSessionStatus.INTERRUPTED else MonitoringSessionStatus.COMPLETED,
            )
            return
        }
        runtime.stopMonitoring(interrupted) { finishService(clearRecovery = true) }
    }

    private fun interruptStaleSession(record: MonitoringRecoveryRecord) {
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                applicationContainer.monitoringSessionRepository.finish(
                    driverUserId = record.driverUserId,
                    sessionId = record.sessionId,
                    status = MonitoringSessionStatus.INTERRUPTED,
                    endedAtEpochMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun finishStoredSession(record: MonitoringRecoveryRecord, status: MonitoringSessionStatus) {
        serviceScope.launch {
            runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    applicationContainer.monitoringSessionRepository.finish(
                        driverUserId = record.driverUserId,
                        sessionId = record.sessionId,
                        status = status,
                        endedAtEpochMillis = System.currentTimeMillis(),
                    )
                }
            }
            finishService(clearRecovery = true)
        }
    }

    private fun failStart(message: String) {
        showForegroundNotification(message)
        finishService(clearRecovery = true)
    }

    private fun finishService(clearRecovery: Boolean) {
        if (clearRecovery) recoveryStore.clear()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:monitoring")
            .apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        wakeLockRenewJob?.cancel()
        wakeLockRenewJob = serviceScope.launch {
            while (true) {
                delay(WAKE_LOCK_RENEWAL_MS)
                wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLockRenewJob?.cancel()
        wakeLockRenewJob = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun showForegroundNotification(status: String) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(status),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else 0,
        )
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DriveAlertMonitoringService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (status) {
            "connected", "active" -> "Camera connected and monitoring is active"
            "reconnecting", "connecting" -> "Monitoring is active; reconnecting to the camera"
            "unavailable" -> "Monitoring is active; camera is unavailable"
            "starting" -> "Starting camera monitoring"
            else -> status.replaceFirstChar { it.uppercase() }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (status.startsWith("Monitoring paused")) "DriveAlert monitoring paused" else "DriveAlert monitoring active")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop monitoring", stopIntent)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.lhordkent.drivealert.action.START_MONITORING"
        private const val ACTION_STOP = "com.lhordkent.drivealert.action.STOP_MONITORING"
        private const val EXTRA_DRIVER_ID = "driver_id"
        private const val EXTRA_SOUND = "sound"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_NOTIFICATIONS = "notifications"
        private const val EXTRA_ACCESSORY = "accessory"
        private const val EXTRA_INTERRUPTED = "interrupted"
        private const val CHANNEL_ID = "active_monitoring"
        private const val NOTIFICATION_ID = 4100
        private const val RECOVERY_TIMEOUT_MS = 30_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60_000L
        private const val WAKE_LOCK_RENEWAL_MS = 9 * 60_000L

        fun start(
            context: Context,
            driverId: String,
            sound: WarningSound,
            volume: PreferredVolume,
            notificationsEnabled: Boolean,
            accessoryMode: DriverAccessoryMode,
        ) {
            val intent = Intent(context, DriveAlertMonitoringService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DRIVER_ID, driverId)
                .putExtra(EXTRA_SOUND, sound.name)
                .putExtra(EXTRA_VOLUME, volume.name)
                .putExtra(EXTRA_NOTIFICATIONS, notificationsEnabled)
                .putExtra(EXTRA_ACCESSORY, accessoryMode.name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, interrupted: Boolean = false) {
            context.startService(
                Intent(context, DriveAlertMonitoringService::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_INTERRUPTED, interrupted),
            )
        }

        fun dismissAfterRuntimeStop(context: Context) {
            MonitoringRecoveryStore(context).clear()
            context.stopService(Intent(context, DriveAlertMonitoringService::class.java))
        }

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Active monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows when DriveAlert is actively monitoring the driver"
                    setSound(null, null)
                },
            )
        }

        private inline fun <reified T : Enum<T>> enumExtra(intent: Intent, key: String, fallback: T): T =
            runCatching { enumValueOf<T>(intent.getStringExtra(key).orEmpty()) }.getOrDefault(fallback)
    }
}
