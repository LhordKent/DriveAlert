package com.lhordkent.drivealert.detection

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.lhordkent.drivealert.DriveAlertApplication
import com.lhordkent.drivealert.data.repository.CalibrationRepository
import com.lhordkent.drivealert.data.repository.AlertRepository
import com.lhordkent.drivealert.data.repository.ConfirmedAlertInput
import com.lhordkent.drivealert.data.repository.MonitoringSessionRepository
import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import com.lhordkent.drivealert.data.repository.ProvisionedDeviceRepository
import com.lhordkent.drivealert.data.repository.StartMonitoringSessionInput
import com.lhordkent.drivealert.data.local.entity.MonitoringSessionStatus
import com.lhordkent.drivealert.data.sync.StageSyncRecordInput
import com.lhordkent.drivealert.data.sync.StageSyncRepository
import com.lhordkent.drivealert.detection.face.MediaPipeFaceLandmarkerProcessor
import com.lhordkent.drivealert.detection.frame.Esp32MjpegFrameSource
import com.lhordkent.drivealert.detection.frame.FramePacket
import com.lhordkent.drivealert.detection.frame.MonotonicFrameGate
import com.lhordkent.drivealert.detection.frame.SharedBitmapFrame
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.detection.model.CalibrationProgress
import com.lhordkent.drivealert.detection.model.CalibrationResult
import com.lhordkent.drivealert.detection.model.CalibrationStatus
import com.lhordkent.drivealert.detection.model.MonitoringDetectionResult
import com.lhordkent.drivealert.detection.model.SignType
import com.lhordkent.drivealert.detection.model.TemporalState
import com.lhordkent.drivealert.monitoring.ActiveMonitoringState
import com.lhordkent.drivealert.monitoring.MonitoringEffect
import com.lhordkent.drivealert.monitoring.MonitoringSessionController
import com.lhordkent.drivealert.monitoring.PendingHardwareWarningOutputGateway
import com.lhordkent.drivealert.monitoring.WarningDeliveryStatus
import com.lhordkent.drivealert.monitoring.WarningOutputGateway
import com.lhordkent.drivealert.monitoring.warningOutputCommand
import com.lhordkent.drivealert.monitoring.toVisibleSign
import com.lhordkent.drivealert.notification.DriverWarningNotificationCoordinator
import com.lhordkent.drivealert.postauth.PreferredVolume
import com.lhordkent.drivealert.postauth.WarningSound
import com.lhordkent.drivealert.postauth.WarningStage
import com.lhordkent.drivealert.device.AndroidDriveAlertEndpointResolver
import com.lhordkent.drivealert.device.DriveAlertEndpointResolver
import com.lhordkent.drivealert.device.DriveAlertSessionController
import com.lhordkent.drivealert.device.HttpDriveAlertSessionController
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DriverVisionUiState(
    val streamState: StreamConnectionState = StreamConnectionState.SETUP_REQUIRED,
    val deviceConfigured: Boolean = false,
    val faceDetected: Boolean? = null,
    val alignmentConfirmed: Boolean = false,
    val detection: MonitoringDetectionResult? = null,
    val calibrationProgress: CalibrationProgress? = null,
    val calibrationInProgress: Boolean = false,
    val activeCalibration: CalibrationResult? = null,
    val disconnectInProgress: Boolean = false,
    val monitoring: ActiveMonitoringState = ActiveMonitoringState(),
    val errorMessage: String? = null,
)

class DriverVisionViewModel @JvmOverloads constructor(
    application: Application,
    private val calibrationRepository: CalibrationRepository =
        (application as DriveAlertApplication).container.calibrationRepository,
    private val deviceRepository: ProvisionedDeviceRepository =
        (application as DriveAlertApplication).container.provisionedDeviceRepository,
    private val endpointResolver: DriveAlertEndpointResolver = AndroidDriveAlertEndpointResolver(application),
    private val sessionController: DriveAlertSessionController = HttpDriveAlertSessionController(),
    private val monitoringSessionRepository: MonitoringSessionRepository =
        (application as DriveAlertApplication).container.monitoringSessionRepository,
    private val alertRepository: AlertRepository =
        (application as DriveAlertApplication).container.alertRepository,
    private val stageSyncRepository: StageSyncRepository =
        (application as DriveAlertApplication).container.stageSyncRepository,
    private val warningOutputGateway: WarningOutputGateway = PendingHardwareWarningOutputGateway(),
    private val notificationCoordinator: DriverWarningNotificationCoordinator =
        (application as DriveAlertApplication).container.driverWarningNotificationCoordinator,
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : AndroidViewModel(application), Closeable {
    private val mutableState = MutableStateFlow(DriverVisionUiState())
    val state: StateFlow<DriverVisionUiState> = mutableState.asStateFlow()
    private val mutablePreviewFrame = MutableStateFlow<SharedBitmapFrame?>(null)
    val previewFrame: StateFlow<SharedBitmapFrame?> = mutablePreviewFrame.asStateFlow()
    private var engine = DriveAlertDetectionEngine()
    private val processor = MediaPipeFaceLandmarkerProcessor(
        context = application,
        onObservation = { timestamp, observation ->
            val result = engine.process(timestamp, observation)
            viewModelScope.launch { acceptDetection(result) }
        },
        onError = { error -> mutableState.update { it.copy(errorMessage = error.message ?: "Face processing stopped.") } },
    )
    private var boundDriverId: String? = null
    private var activeDevice: ProvisionedDevice? = null
    private var deviceJob: Job? = null
    private var reconnectJob: Job? = null
    private var monitoringJob: Job? = null
    private var monitoringStartJob: Job? = null
    private var source: Esp32MjpegFrameSource? = null
    private var latestPreview: SharedBitmapFrame? = null
    private var streamWanted = false
    private var reconnectAttempt = 0
    private val inferenceGate = MonotonicFrameGate(INFERENCE_INTERVAL_MS)
    private var persistedCalibrationId: String? = null
    private val monitoringController = MonitoringSessionController(elapsedClock)
    private var monitoringStarting = false
    private var sessionStartedAtElapsedMs: Long? = null
    private var sessionStartedAtEpochMillis: Long? = null
    private var monitoringSound: WarningSound = WarningSound.DIGITAL_BEEP
    private var monitoringVolume: PreferredVolume = PreferredVolume.MEDIUM
    private var monitoringNotificationsEnabled: Boolean = true
    private var highestPersistedStage: WarningStage? = null
    private var activeAlertId: String? = null

    fun bindDriver(driverUserId: String?) {
        if (driverUserId == boundDriverId) return
        stopMonitoring(interrupted = true)
        stopStream(resetAlignment = true)
        deviceJob?.cancel()
        boundDriverId = driverUserId
        activeDevice = null
        engine = DriveAlertDetectionEngine()
        persistedCalibrationId = null
        mutableState.value = DriverVisionUiState()
        if (driverUserId == null) return
        deviceJob = viewModelScope.launch {
            calibrationRepository.active(driverUserId)?.let { calibration ->
                engine.activatePersistedCalibration(calibration)
                persistedCalibrationId = calibration.calibrationId
                mutableState.update { it.copy(activeCalibration = calibration) }
            }
            deviceRepository.observeActive(driverUserId).collect { device ->
                val endpointChanged = activeDevice?.deviceId != device?.deviceId
                activeDevice = device
                mutableState.update {
                    it.copy(
                        streamState = if (device == null) StreamConnectionState.SETUP_REQUIRED else it.streamState,
                        deviceConfigured = device != null,
                        alignmentConfirmed = if (endpointChanged) false else it.alignmentConfirmed,
                    ).let { updated ->
                        if (device != null && updated.streamState == StreamConnectionState.SETUP_REQUIRED) {
                            updated.copy(streamState = StreamConnectionState.READY)
                        } else updated
                    }
                }
                if (streamWanted && device != null) connect(device)
            }
        }
    }

    fun startVision() {
        streamWanted = true
        val device = activeDevice
        if (device == null) {
            mutableState.update { it.copy(streamState = StreamConnectionState.SETUP_REQUIRED, errorMessage = "Connect the DriveAlert camera first.") }
        } else if (source == null) {
            connect(device)
        }
    }

    fun confirmAlignment() {
        if (mutableState.value.streamState == StreamConnectionState.CONNECTED) {
            mutableState.update { it.copy(alignmentConfirmed = true) }
        }
    }

    fun disconnectCurrentSession(onDisconnected: () -> Unit = {}) {
        val device = activeDevice
        if (device == null || mutableState.value.streamState != StreamConnectionState.CONNECTED) {
            mutableState.update { it.copy(errorMessage = "The DriveAlert camera is not currently connected.") }
            return
        }
        if (mutableState.value.disconnectInProgress) return
        stopMonitoring(interrupted = true)
        mutableState.update { it.copy(disconnectInProgress = true, errorMessage = null) }
        viewModelScope.launch {
            sessionController.disconnect(device).fold(
                onSuccess = {
                    stopStream(resetAlignment = true)
                    reconnectAttempt = 0
                    mutableState.update {
                        it.copy(
                            streamState = StreamConnectionState.READY,
                            disconnectInProgress = false,
                            errorMessage = null,
                        )
                    }
                    onDisconnected()
                },
                onFailure = { error ->
                    mutableState.update {
                        it.copy(
                            disconnectInProgress = false,
                            errorMessage = error.message ?: "Could not disconnect the DriveAlert camera.",
                        )
                    }
                },
            )
        }
    }

    fun startCalibration() {
        if (!mutableState.value.alignmentConfirmed || mutableState.value.streamState != StreamConnectionState.CONNECTED) return
        engine.startCalibration()
        engine.beginCalibrationPhase()
        mutableState.update { it.copy(calibrationProgress = null, calibrationInProgress = true, errorMessage = null) }
    }

    fun beginCalibrationPhase() = engine.beginCalibrationPhase()

    fun repeatCalibrationPhase() = engine.repeatCalibrationPhase()

    fun cancelCalibration() {
        val userId = boundDriverId ?: return
        viewModelScope.launch {
            engine = DriveAlertDetectionEngine()
            val active = calibrationRepository.active(userId)
            active?.let(engine::activatePersistedCalibration)
            mutableState.update { it.copy(calibrationProgress = null, calibrationInProgress = false, activeCalibration = active) }
        }
    }

    fun startMonitoring(
        sound: WarningSound = WarningSound.DIGITAL_BEEP,
        volume: PreferredVolume = PreferredVolume.MEDIUM,
        notificationsEnabled: Boolean = true,
    ) {
        if (monitoringController.state.isActive || monitoringStarting) return
        val driverId = boundDriverId ?: return
        val calibration = mutableState.value.activeCalibration ?: return
        val device = activeDevice ?: return
        if (mutableState.value.streamState != StreamConnectionState.CONNECTED) return
        monitoringStarting = true
        monitoringSound = sound
        monitoringVolume = volume
        monitoringNotificationsEnabled = notificationsEnabled
        monitoringStartJob = viewModelScope.launch {
            try {
                val startedAtEpoch = wallClock()
                val sessionId = monitoringSessionRepository.start(
                    StartMonitoringSessionInput(
                        driverUserId = driverId,
                        calibrationId = calibration.calibrationId,
                        deviceId = device.deviceId,
                        startedAtEpochMillis = startedAtEpoch,
                    ),
                )
                monitoringController.start(sessionId)
                sessionStartedAtElapsedMs = monitoringController.state.startedAtElapsedMs
                sessionStartedAtEpochMillis = startedAtEpoch
                highestPersistedStage = null
                activeAlertId = null
                publishMonitoringState()
                monitoringJob?.cancel()
                monitoringJob = viewModelScope.launch {
                    while (monitoringController.state.isActive) {
                        delay(MONITORING_TICK_MS)
                        handleMonitoringEffects(monitoringController.tick(), driverId, sessionId)
                        persistHighestStage(driverId, sessionId)
                        publishMonitoringState()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update { it.copy(errorMessage = error.message ?: "Monitoring could not start.") }
            } finally {
                monitoringStarting = false
                monitoringStartJob = null
            }
        }
    }

    fun stopMonitoring(interrupted: Boolean = false) {
        monitoringStartJob?.cancel()
        monitoringStartJob = null
        monitoringStarting = false
        monitoringJob?.cancel()
        monitoringJob = null
        val sessionId = monitoringController.state.sessionId
        val driverId = boundDriverId
        monitoringController.stop()
        publishMonitoringState()
        sessionStartedAtElapsedMs = null
        sessionStartedAtEpochMillis = null
        highestPersistedStage = null
        activeAlertId = null
        if (sessionId != null && driverId != null) {
            viewModelScope.launch {
                runCatching {
                    monitoringSessionRepository.finish(
                        driverUserId = driverId,
                        sessionId = sessionId,
                        status = if (interrupted) MonitoringSessionStatus.INTERRUPTED else MonitoringSessionStatus.COMPLETED,
                        endedAtEpochMillis = wallClock(),
                    )
                }.onFailure { error ->
                    mutableState.update { it.copy(errorMessage = error.message ?: "Monitoring session could not be closed.") }
                }
            }
        }
    }

    private fun connect(device: ProvisionedDevice) {
        reconnectJob?.cancel()
        source?.stop()
        source = null
        mutableState.update { it.copy(streamState = StreamConnectionState.CONNECTING, errorMessage = null) }
        reconnectJob = viewModelScope.launch {
            val resolved = endpointResolver.resolve(device)
            if (resolved == null) {
                scheduleReconnect("DriveAlert camera is unavailable on this network.")
                return@launch
            }
            if (resolved != device) deviceRepository.saveActive(resolved)
            val next = Esp32MjpegFrameSource(
                streamUrl = resolved.streamUrl,
                onState = { status, message -> mutableState.update { it.copy(streamState = status, errorMessage = message) } },
                onDisconnected = { error -> scheduleReconnect(error?.message ?: "Camera stream ended.") },
            )
            source = next
            next.start(::acceptFrame)
        }
    }

    private fun acceptFrame(packet: FramePacket<SharedBitmapFrame>) {
        reconnectAttempt = 0
        val frame = packet.payload
        val previous = latestPreview
        latestPreview = frame
        mutablePreviewFrame.value = frame
        if (mutableState.value.streamState != StreamConnectionState.CONNECTED || mutableState.value.errorMessage != null) {
            mutableState.update { it.copy(streamState = StreamConnectionState.CONNECTED, errorMessage = null) }
        }
        previous?.release()

        if (!inferenceGate.tryAcquire(packet.timestampMs)) return
        val inferenceSource = frame.tryRetain() ?: return
        processor.submitLazy(
            width = packet.width,
            height = packet.height,
            timestampMs = packet.timestampMs,
            imageFactory = {
                // MediaPipe owns its MPImage. Isolate its pixels from the Bitmap
                // retained by Compose, but only after the processor accepts work.
                val bitmap = inferenceSource.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    ?: error("Camera frame could not be copied for face processing.")
                try {
                    BitmapImageBuilder(bitmap).build()
                } catch (error: Throwable) {
                    bitmap.recycle()
                    throw error
                }
            },
            releaseSource = inferenceSource::release,
        )
    }

    private suspend fun acceptDetection(result: MonitoringDetectionResult) {
        val calibrationCompleted = mutableState.value.calibrationInProgress && result.calibration.status == CalibrationStatus.COMPLETE
        mutableState.update {
            it.copy(
                faceDetected = result.faceDetected,
                detection = result,
                calibrationProgress = if (it.calibrationInProgress) result.calibration else it.calibrationProgress,
                calibrationInProgress = if (calibrationCompleted) false else it.calibrationInProgress,
                activeCalibration = result.activeCalibration ?: it.activeCalibration,
            )
        }
        val calibration = result.activeCalibration ?: return
        val userId = boundDriverId ?: return
        if (calibrationCompleted && calibration.calibrationId != persistedCalibrationId) {
            calibrationRepository.saveAndActivate(userId, calibration, System.currentTimeMillis())
            persistedCalibrationId = calibration.calibrationId
        }
        val sessionId = monitoringController.state.sessionId
        if (sessionId != null) {
            val effects = monitoringController.accept(result.events, result.currentlyConfirmedSigns())
            handleMonitoringEffects(effects, userId, sessionId)
            persistHighestStage(userId, sessionId)
            publishMonitoringState()
        }
    }

    private suspend fun handleMonitoringEffects(
        effects: List<MonitoringEffect>,
        driverId: String,
        sessionId: String,
    ) {
        for (effect in effects) when (effect) {
            is MonitoringEffect.ActivateWarning -> {
                val delivery = runCatching {
                    warningOutputGateway.activate(effect.stage.warningOutputCommand(monitoringSound, monitoringVolume))
                }.getOrElse { WarningDeliveryStatus.FAILED }
                monitoringController.setWarningDelivery(delivery)
            }
            is MonitoringEffect.RecordAlert -> {
                val delivered = monitoringController.state.warningDelivery == WarningDeliveryStatus.DELIVERED
                activeAlertId = alertRepository.recordConfirmedAlert(
                    ConfirmedAlertInput(
                        sessionId = sessionId,
                        driverUserId = driverId,
                        detectedAtEpochMillis = toEpochMillis(effect.occurredAtElapsedMs),
                        warningStageAtDetection = effect.stage,
                        signs = effect.signs,
                        alarmTriggered = delivered,
                        alarmTriggeredAtEpochMillis = if (delivered) wallClock() else null,
                    ),
                )
                activeAlertId?.let { alertId ->
                    notificationCoordinator.notifyConfirmedAlert(
                        alertId = alertId,
                        stage = effect.stage,
                        signs = effect.signs,
                        enabled = monitoringNotificationsEnabled,
                    )
                }
            }
            is MonitoringEffect.MergeLatestAlertSigns -> {
                activeAlertId?.let { alertId ->
                    alertRepository.addSigns(driverId, alertId, effect.signs)
                    notificationCoordinator.notifyConfirmedAlert(
                        alertId = alertId,
                        stage = requireNotNull(monitoringController.state.currentStage),
                        signs = monitoringController.state.latestSigns,
                        enabled = monitoringNotificationsEnabled,
                    )
                }
            }
            is MonitoringEffect.RecordStage3Boundary -> stageSyncRepository.createSynchronizationRecord(
                StageSyncRecordInput(
                    sessionId = sessionId,
                    driverUserId = driverId,
                    recordType = effect.type,
                    periodStartedAtEpochMillis = toEpochMillis(effect.periodStartedAtElapsedMs),
                    periodEndedAtEpochMillis = toEpochMillis(effect.periodEndedAtElapsedMs),
                    eventCount = effect.eventCount,
                    signs = effect.signs,
                ),
            )
        }
    }

    private suspend fun persistHighestStage(driverId: String, sessionId: String) {
        val stage = monitoringController.state.currentStage ?: return
        if ((highestPersistedStage?.ordinal ?: -1) >= stage.ordinal) return
        monitoringSessionRepository.updateHighestStage(driverId, sessionId, stage)
        highestPersistedStage = stage
    }

    private fun publishMonitoringState() {
        mutableState.update { it.copy(monitoring = monitoringController.state) }
    }

    private fun toEpochMillis(elapsedMs: Long): Long {
        val elapsedStart = requireNotNull(sessionStartedAtElapsedMs)
        val epochStart = requireNotNull(sessionStartedAtEpochMillis)
        return epochStart + (elapsedMs - elapsedStart).coerceAtLeast(0L)
    }

    private fun scheduleReconnect(message: String) {
        source?.stop()
        source = null
        if (!streamWanted) return
        mutableState.update {
            it.copy(
                streamState = if (reconnectAttempt >= 3) StreamConnectionState.UNAVAILABLE else StreamConnectionState.RECONNECTING,
                errorMessage = message,
            )
        }
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val delayMs = minOf(8_000L, 1_000L shl minOf(reconnectAttempt, 3))
            reconnectAttempt++
            delay(delayMs)
            activeDevice?.let(::connect)
        }
    }

    private fun stopStream(resetAlignment: Boolean) {
        streamWanted = false
        reconnectJob?.cancel()
        source?.stop()
        source = null
        latestPreview?.release()
        latestPreview = null
        mutablePreviewFrame.value = null
        inferenceGate.reset()
        mutableState.update {
            it.copy(
                faceDetected = null,
                alignmentConfirmed = if (resetAlignment) false else it.alignmentConfirmed,
            )
        }
    }

    override fun close() {
        stopMonitoring(interrupted = true)
        stopStream(resetAlignment = true)
        processor.close()
    }

    override fun onCleared() = close()

    companion object {
        private const val INFERENCE_INTERVAL_MS = 100L
        private const val MONITORING_TICK_MS = 250L
    }
}

private fun MonitoringDetectionResult.currentlyConfirmedSigns() = buildSet {
    if (eye.state == TemporalState.CONFIRMED) add(SignType.PROLONGED_EYE_CLOSURE.toVisibleSign())
    if (yawn.state == TemporalState.CONFIRMED) add(SignType.YAWNING.toVisibleSign())
    if (head.state == TemporalState.CONFIRMED) add(SignType.HEAD_NODDING.toVisibleSign())
}
