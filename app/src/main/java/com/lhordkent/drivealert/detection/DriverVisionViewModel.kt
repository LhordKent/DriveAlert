package com.lhordkent.drivealert.detection

import android.app.Application
import android.graphics.Bitmap
import com.lhordkent.drivealert.detection.face.DriverRegion
import com.lhordkent.drivealert.detection.face.DriverFaceSelector
import com.lhordkent.drivealert.detection.face.DriverRegionStore
import com.lhordkent.drivealert.detection.face.SavedDriverRegion
import com.lhordkent.drivealert.detection.face.DriverTrackingState
import com.lhordkent.drivealert.detection.face.DriverTrackingStatus
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.lhordkent.drivealert.BuildConfig
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
import com.lhordkent.drivealert.detection.frame.FrameLuminanceSampler
import com.lhordkent.drivealert.detection.frame.MonotonicFrameGate
import com.lhordkent.drivealert.detection.frame.SharedBitmapFrame
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.detection.model.CalibrationProgress
import com.lhordkent.drivealert.detection.model.CalibrationResult
import com.lhordkent.drivealert.detection.model.CalibrationStatus
import com.lhordkent.drivealert.detection.model.FaceAttributeResult
import com.lhordkent.drivealert.detection.model.MonitoringDetectionResult
import com.lhordkent.drivealert.detection.model.RegionalVisibility
import com.lhordkent.drivealert.detection.model.SignType
import com.lhordkent.drivealert.detection.model.TemporalState
import com.lhordkent.drivealert.monitoring.ActiveMonitoringState
import com.lhordkent.drivealert.monitoring.MonitoringEffect
import com.lhordkent.drivealert.monitoring.MonitoringSessionController
import com.lhordkent.drivealert.monitoring.WarningDeliveryStatus
import com.lhordkent.drivealert.monitoring.WarningOutputGateway
import com.lhordkent.drivealert.monitoring.VisibilityAlertController
import com.lhordkent.drivealert.monitoring.VisibilityAlertEffect
import com.lhordkent.drivealert.monitoring.DriverAccessoryMode
import com.lhordkent.drivealert.monitoring.SceneBrightnessTracker
import com.lhordkent.drivealert.monitoring.FaceLightingAssessment
import com.lhordkent.drivealert.monitoring.FaceLightingTracker
import com.lhordkent.drivealert.monitoring.lightingVisibilityIssue
import com.lhordkent.drivealert.monitoring.declaredIssue
import com.lhordkent.drivealert.monitoring.VisibilityIssue
import com.lhordkent.drivealert.monitoring.VisibilityOutputCommand
import com.lhordkent.drivealert.monitoring.VisibilityOutputGateway
import com.lhordkent.drivealert.monitoring.DriveAlertMonitoringService
import com.lhordkent.drivealert.monitoring.MonitoringRecoveryStore
import com.lhordkent.drivealert.monitoring.activateSafely
import com.lhordkent.drivealert.monitoring.warningOutputCommand
import com.lhordkent.drivealert.monitoring.toVisibleSign
import com.lhordkent.drivealert.postauth.VisibleSign
import com.lhordkent.drivealert.notification.DriverWarningNotificationCoordinator
import com.lhordkent.drivealert.notification.DriverVisibilityNotificationCoordinator
import com.lhordkent.drivealert.postauth.PreferredVolume
import com.lhordkent.drivealert.postauth.WarningSound
import com.lhordkent.drivealert.postauth.WarningStage
import com.lhordkent.drivealert.device.AndroidDriveAlertEndpointResolver
import com.lhordkent.drivealert.device.DriveAlertEndpointResolver
import com.lhordkent.drivealert.device.DriveAlertSessionController
import com.lhordkent.drivealert.device.HttpDriveAlertSessionController
import com.lhordkent.drivealert.device.HttpWarningOutputGateway
import com.lhordkent.drivealert.device.HttpVisibilityOutputGateway
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DriverVisionUiState(
    val streamState: StreamConnectionState = StreamConnectionState.SETUP_REQUIRED,
    val deviceConfigured: Boolean = false,
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val bleSetupRequested: Boolean = false,
    val faceDetected: Boolean? = null,
    val alignmentConfirmed: Boolean = false,
    val driverRegion: DriverRegion = DriverRegion.DEFAULT,
    val driverTracking: DriverTrackingState = DriverTrackingState(),
    val detection: MonitoringDetectionResult? = null,
    val faceAttributes: FaceAttributeResult? = null,
    val calibrationProgress: CalibrationProgress? = null,
    val calibrationInProgress: Boolean = false,
    val activeCalibration: CalibrationResult? = null,
    val disconnectInProgress: Boolean = false,
    val monitoring: ActiveMonitoringState = ActiveMonitoringState(),
    val accessoryMode: DriverAccessoryMode = DriverAccessoryMode.NONE,
    val visibilityIssue: VisibilityIssue? = null,
    val visibilityAcknowledgementRequired: VisibilityIssue? = null,
    val debugWarningDelivery: WarningDeliveryStatus? = null,
    val lowLight: Boolean? = null,
    val ambientLuminance: Double? = null,
    val smoothedLuminance: Double? = null,
    val lighting: FaceLightingAssessment = FaceLightingAssessment(),
    val errorMessage: String? = null,
)

class DriverVisionRuntime @JvmOverloads constructor(
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
    warningOutputGateway: WarningOutputGateway? = null,
    visibilityOutputGateway: VisibilityOutputGateway? = null,
    private val notificationCoordinator: DriverWarningNotificationCoordinator =
        (application as DriveAlertApplication).container.driverWarningNotificationCoordinator,
    private val visibilityNotificationCoordinator: DriverVisibilityNotificationCoordinator =
        (application as DriveAlertApplication).container.driverVisibilityNotificationCoordinator,
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val monitoringLogWriter: com.lhordkent.drivealert.data.monitoringlog.MonitoringLogWriter? = null,
) : Closeable {
    private val mutableState = MutableStateFlow(DriverVisionUiState())
    val state: StateFlow<DriverVisionUiState> = mutableState.asStateFlow()
    private val mutablePreviewFrame = MutableStateFlow<SharedBitmapFrame?>(null)
    val previewFrame: StateFlow<SharedBitmapFrame?> = mutablePreviewFrame.asStateFlow()
    private var engine = DriveAlertDetectionEngine()
    private val lightingTracker = FaceLightingTracker()
    private val regionStore = DriverRegionStore()
    private var regionKey: String? = null
    private var savedRegion: SavedDriverRegion? = null
    @Volatile private var targetConfigurationRevision = 0L
    private var targetGeneration = -1L
    private val processor = MediaPipeFaceLandmarkerProcessor(
        context = application,
        onObservation = { timestamp, observation, faceAttributes, lightingSample, tracking, revision ->
            val generation = streamGeneration
            if (BuildConfig.DEBUG) Log.d("DriverFaceLock", "result status=${tracking.status} target=${tracking.generation} faces=${tracking.detectedFaceCount} age=${elapsedClock() - timestamp}ms revision=$revision/$targetConfigurationRevision")
            scope.launch {
                if (revision != targetConfigurationRevision || timestamp < observationCutoffMs || !streamWanted || generation != streamGeneration ||
                    mutableState.value.streamState != StreamConnectionState.CONNECTED ||
                    elapsedClock() - timestamp > DriverFaceSelector.MAX_FRAME_GAP_MS
                ) return@launch
                val saved = savedRegion
                if (saved != null && (saved.width != tracking.frameWidth || saved.height != tracking.frameHeight)) {
                    regionKey?.let(regionStore::remove)
                    savedRegion = null
                    mutableState.update { it.copy(alignmentConfirmed = false) }
                    resetLighting()
                    return@launch
                }
                if (targetGeneration != tracking.generation) {
                    targetGeneration = tracking.generation
                    lightingTracker.reset()
                    engine.invalidateDriverTarget()
                    monitoringController.accept(emptyList(), emptySet())
                    if (mutableState.value.calibrationInProgress) {
                        mutableState.update { it.copy(errorMessage = "Driver tracking changed. Restart calibration from the neutral position.") }
                    }
                }
                mutableState.update { it.copy(driverTracking = tracking) }
                // Serialize quality, engine and session decisions on the runtime's main scope.
                val lighting = lightingTracker.update(elapsedClock(), lightingSample?.takeIf { it.timestampMs >= observationCutoffMs })
                if (BuildConfig.DEBUG && lighting.quality != mutableState.value.lighting.quality) {
                    Log.d("DriverFaceLock", "lighting=${lighting.quality} target=${tracking.generation}")
                }
                val result = engine.process(
                    timestamp,
                    observation,
                    effectiveRegionalVisibility(faceAttributes),
                    assessmentAllowed = tracking.locked && mutableState.value.alignmentConfirmed && lighting.allowsAssessment(elapsedClock()),
                )
                acceptDetection(result, faceAttributes, lighting)
            }
        },
        onError = { error -> mutableState.update { it.copy(errorMessage = error.message ?: "Face processing stopped.") } },
    )
    private var boundDriverId: String? = null
    private var activeDevice: ProvisionedDevice? = null
    private val warningOutputGateway: WarningOutputGateway = warningOutputGateway ?: HttpWarningOutputGateway(
        deviceProvider = { activeDevice },
        endpointResolver = endpointResolver,
        onDeviceResolved = { resolved ->
            activeDevice = resolved
            deviceRepository.saveActive(resolved)
        },
    )
    private val visibilityOutputGateway: VisibilityOutputGateway = visibilityOutputGateway ?: HttpVisibilityOutputGateway(
        deviceProvider = { activeDevice },
        endpointResolver = endpointResolver,
        onDeviceResolved = { resolved ->
            activeDevice = resolved
            deviceRepository.saveActive(resolved)
        },
    )
    private var deviceJob: Job? = null
    private var reconnectJob: Job? = null
    private var monitoringJob: Job? = null
    private var repeatWarningJob: Job? = null
    private var repeatWarningSigns = emptySet<VisibleSign>()
    private var monitoringStartJob: Job? = null
    private var brightnessSampleJob: Job? = null
    private var visionHealthJob: Job? = null
    private var source: Esp32MjpegFrameSource? = null
    private var sourceStreamUrl: String? = null
    private var latestPreview: SharedBitmapFrame? = null
    @Volatile private var streamWanted = false
    @Volatile private var observationCutoffMs = 0L
    @Volatile private var streamGeneration = 0
    @Volatile private var lastFrameTimestampMs: Long? = null
    private var lastDetectionTimestampMs: Long? = null
    private var lastBrightnessTimestampMs: Long? = null
    private var reconnectAttempt = 0
    private var heartbeatJob: Job? = null
    private val inferenceGate = MonotonicFrameGate(INFERENCE_INTERVAL_MS)
    private val brightnessGate = MonotonicFrameGate(BRIGHTNESS_SAMPLE_INTERVAL_MS)
    private val brightnessTracker = SceneBrightnessTracker()
    private var brightnessGeneration = 0
    private var persistedCalibrationId: String? = null
    private val monitoringController = MonitoringSessionController(elapsedClock)
    private val visibilityAlertController = VisibilityAlertController()
    private var monitoringStarting = false
    private var sessionStartedAtElapsedMs: Long? = null
    private var sessionStartedAtEpochMillis: Long? = null
    private var monitoringSound: WarningSound = WarningSound.DIGITAL_BEEP
    private var monitoringVolume: PreferredVolume = PreferredVolume.MEDIUM
    private var monitoringNotificationsEnabled: Boolean = true
    private var monitoringAccessoryMode: DriverAccessoryMode = DriverAccessoryMode.NONE
    private var highestPersistedStage: WarningStage? = null
    private var activeAlertId: String? = null

    fun bindDriver(driverUserId: String?) {
        if (driverUserId == boundDriverId) return
        stopMonitoring(interrupted = true)
        stopStream(resetAlignment = true)
        deviceJob?.cancel()
        boundDriverId = driverUserId
        activeDevice = null
        regionStore.clear()
        regionKey = null
        savedRegion = null
        targetConfigurationRevision = processor.configure(null)
        engine = DriveAlertDetectionEngine()
        persistedCalibrationId = null
        mutableState.value = DriverVisionUiState()
        if (driverUserId == null) return
        deviceJob = scope.launch {
            calibrationRepository.active(driverUserId)?.let { calibration ->
                engine.activatePersistedCalibration(calibration)
                persistedCalibrationId = calibration.calibrationId
                mutableState.update { it.copy(activeCalibration = calibration) }
            }
            deviceRepository.observeActive(driverUserId).collect { device ->
                val endpointChanged = activeDevice?.deviceId != device?.deviceId
                activeDevice = device
                val nextRegionKey = device?.let { "${it.driverUserId}/${it.deviceId}/${it.streamPort}${it.streamPath}" }
                if (nextRegionKey != regionKey) {
                    regionKey = nextRegionKey
                    savedRegion = nextRegionKey?.let(regionStore::load)
                    mutableState.update { it.copy(driverRegion = savedRegion?.region ?: DriverRegion.DEFAULT, alignmentConfirmed = savedRegion != null) }
                    resetLighting()
                }
                mutableState.update {
                    it.copy(
                        streamState = if (device == null) StreamConnectionState.SETUP_REQUIRED else it.streamState,
                        deviceConfigured = device != null,
                        deviceAddress = device?.lastKnownIp,
                        deviceName = device?.hostname ?: device?.deviceId,
                        alignmentConfirmed = if (endpointChanged) savedRegion != null else it.alignmentConfirmed,
                    ).let { updated ->
                        if (device != null && updated.streamState == StreamConnectionState.SETUP_REQUIRED) {
                            updated.copy(streamState = StreamConnectionState.READY)
                        } else updated
                    }
                }
                val shouldConnect = shouldConnectStreamForDeviceUpdate(
                    streamWanted = streamWanted,
                    nextStreamUrl = device?.streamUrl,
                    activeSourceStreamUrl = sourceStreamUrl,
                    connectionAttemptActive = reconnectJob?.isActive == true,
                )
                if (shouldConnect) device?.let(::connect)
            }
        }
    }

    fun startVision() {
        if (mutableState.value.disconnectInProgress) return
        streamWanted = true
        mutableState.update { it.copy(bleSetupRequested = false) }
        if (heartbeatJob?.isActive != true) heartbeatJob = scope.launch {
            while (streamWanted) {
                activeDevice?.let { sessionController.heartbeat(it) }
                delay(10_000)
            }
        }
        if (visionHealthJob?.isActive != true) {
            visionHealthJob = scope.launch {
                while (streamWanted) {
                    delay(MONITORING_TICK_MS)
                    expireCameraReadings()
                }
            }
        }
        val device = activeDevice
        if (device == null) {
            mutableState.update { it.copy(streamState = StreamConnectionState.SETUP_REQUIRED, errorMessage = "Connect the DriveAlert camera first.") }
        } else if (source == null) {
            connect(device)
        }
    }

    fun returnToBleProvisioning(onReady: () -> Unit = {}) {
        if (activeDevice == null) {
            mutableState.update { it.copy(bleSetupRequested = true) }
            onReady()
        } else disconnectCurrentSession(onReady)
    }

    fun openBleSetup(onReady: () -> Unit = {}) {
        stopMonitoring(interrupted = true)
        stopStream(resetAlignment = true)
        mutableState.update { it.copy(bleSetupRequested = true, streamState = StreamConnectionState.READY, errorMessage = null) }
        onReady()
    }

    fun beginAppAlignmentSession() {
        // A new Activity/ViewModel can share a surviving application process.
        // Returning to an ongoing drive must keep its foreground monitoring alive.
        if (monitoringController.state.isActive || monitoringStarting) return
        regionStore.clear()
        if (mutableState.value.calibrationInProgress) cancelCalibration()
        updateDriverRegion(DriverRegion.DEFAULT)
    }

    fun confirmAlignment() {
        val current = mutableState.value
        val key = regionKey ?: return
        if (current.streamState != StreamConnectionState.CONNECTED || !current.driverTracking.locked ||
            current.monitoring.isActive || current.calibrationInProgress) return
        savedRegion = SavedDriverRegion(current.driverRegion, current.driverTracking.frameWidth, current.driverTracking.frameHeight)
        regionStore.save(key, checkNotNull(savedRegion))
        mutableState.update { it.copy(alignmentConfirmed = true) }
    }

    fun updateDriverRegion(region: DriverRegion) {
        if (monitoringController.state.isActive || monitoringStarting || mutableState.value.calibrationInProgress) return
        regionKey?.let(regionStore::remove)
        savedRegion = null
        mutableState.update { it.copy(driverRegion = region, alignmentConfirmed = false) }
        resetLighting()
    }

    fun disconnectCurrentSession(onDisconnected: () -> Unit = {}) {
        val device = activeDevice
        if (device == null) {
            mutableState.update { it.copy(errorMessage = "No saved DriveAlert camera is available.") }
            return
        }
        if (mutableState.value.disconnectInProgress) return
        val driverAtRequest = boundDriverId
        stopMonitoring(interrupted = true)
        mutableState.update { it.copy(disconnectInProgress = true, errorMessage = null) }
        scope.launch {
            val result = sessionController.disconnect(device)
            if (boundDriverId != driverAtRequest) return@launch
            result.fold(
                onSuccess = {
                    stopStream(resetAlignment = true)
                    reconnectAttempt = 0
                    mutableState.update {
                        it.copy(
                            streamState = StreamConnectionState.READY,
                            disconnectInProgress = false,
                            bleSetupRequested = true,
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

    fun prepareForAccountChange(onComplete: () -> Unit) {
        stopMonitoring(interrupted = true)
        val device = activeDevice
        if (device == null) {
            stopStream(resetAlignment = true)
            onComplete()
            return
        }
        scope.launch {
            // Best effort: a reachable ESP32 restarts into BLE-ready mode so the
            // next account can provision it. Logout must still complete if the
            // device is powered off or no longer on this network.
            try {
                sessionController.disconnect(device)
            } finally {
                stopStream(resetAlignment = true)
                onComplete()
            }
        }
    }

    fun startCalibration() {
        if (!mutableState.value.alignmentConfirmed || !mutableState.value.driverTracking.locked || !mutableState.value.lighting.allowsAssessment(elapsedClock()) || mutableState.value.streamState != StreamConnectionState.CONNECTED || monitoringController.state.isActive) return
        engine.startCalibration()
        engine.beginCalibrationPhase()
        mutableState.update { it.copy(calibrationProgress = null, calibrationInProgress = true, errorMessage = null) }
    }

    fun beginCalibrationPhase() {
        if (mutableState.value.driverTracking.locked && mutableState.value.alignmentConfirmed && mutableState.value.lighting.allowsAssessment(elapsedClock())) {
            engine.beginCalibrationPhase()
            mutableState.update { it.copy(errorMessage = null) }
        }
    }

    fun repeatCalibrationPhase() = engine.repeatCalibrationPhase()

    fun cancelCalibration() {
        val userId = boundDriverId ?: return
        scope.launch {
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
        accessoryMode: DriverAccessoryMode = DriverAccessoryMode.NONE,
        existingSessionId: String? = null,
        existingStartedAtEpochMillis: Long? = null,
        onStarted: (String, Long) -> Unit = { _, _ -> },
        onFailure: (Throwable) -> Unit = {},
    ) {
        if (monitoringController.state.isActive || monitoringStarting) {
            monitoringController.state.sessionId?.let { onStarted(it, sessionStartedAtEpochMillis ?: wallClock()) }
            return
        }
        val driverId = boundDriverId ?: return onFailure(IllegalStateException("No driver is signed in."))
        val calibration = mutableState.value.activeCalibration
            ?: return onFailure(IllegalStateException("Driver calibration is required."))
        val device = activeDevice ?: return onFailure(IllegalStateException("No DriveAlert camera is configured."))
        if (mutableState.value.streamState != StreamConnectionState.CONNECTED) {
            return onFailure(IllegalStateException("The DriveAlert camera is not connected."))
        }
        if (!mutableState.value.alignmentConfirmed) {
            return onFailure(IllegalStateException("Confirm the driver area in Camera Alignment first."))
        }
        if (mutableState.value.calibrationInProgress) return onFailure(IllegalStateException("Finish calibration first."))
        monitoringStarting = true
        monitoringSound = sound
        monitoringVolume = volume
        monitoringNotificationsEnabled = notificationsEnabled
        monitoringAccessoryMode = accessoryMode
        monitoringStartJob = scope.launch {
            try {
                val startedAtEpoch = existingStartedAtEpochMillis ?: wallClock()
                val sessionId = existingSessionId ?: monitoringSessionRepository.start(
                    StartMonitoringSessionInput(
                        driverUserId = driverId,
                        calibrationId = calibration.calibrationId,
                        deviceId = device.deviceId,
                        startedAtEpochMillis = startedAtEpoch,
                    ),
                )
                val recoveredDuration = (wallClock() - startedAtEpoch).coerceAtLeast(0L)
                monitoringController.start(sessionId, recoveredDuration)
                visibilityAlertController.beginSession(accessoryMode)
                visibilityNotificationCoordinator.clear()
                sessionStartedAtElapsedMs = monitoringController.state.startedAtElapsedMs
                sessionStartedAtEpochMillis = startedAtEpoch
                highestPersistedStage = null
                activeAlertId = null
                mutableState.update {
                    it.copy(
                        accessoryMode = accessoryMode,
                        visibilityIssue = accessoryMode.declaredIssue(),
                        visibilityAcknowledgementRequired = null,
                    )
                }
                publishMonitoringState()
                monitoringLogWriter?.begin(driverId, sessionId, startedAtEpoch, existingSessionId != null)
                onStarted(sessionId, startedAtEpoch)
                monitoringJob?.cancel()
                monitoringJob = scope.launch {
                    while (monitoringController.state.isActive) {
                        delay(MONITORING_TICK_MS)
                        expireCameraReadings()
                        handleMonitoringEffects(monitoringController.tick(), driverId, sessionId)
                        persistHighestStage(driverId, sessionId)
                        publishMonitoringState()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableState.update { it.copy(errorMessage = error.message ?: "Monitoring could not start.") }
                onFailure(error)
            } finally {
                monitoringStarting = false
                monitoringStartJob = null
            }
        }
    }

    fun stopMonitoring(interrupted: Boolean = false, onFinished: () -> Unit = {}) {
        monitoringStartJob?.cancel()
        monitoringStartJob = null
        monitoringStarting = false
        monitoringJob?.cancel()
        monitoringJob = null
        repeatWarningJob?.cancel()
        repeatWarningJob = null
        val sessionId = monitoringController.state.sessionId
        val driverId = boundDriverId
        monitoringController.stop()
        resetBrightness()
        resetLighting()
        visibilityAlertController.reset()
        visibilityNotificationCoordinator.clear()
        monitoringAccessoryMode = DriverAccessoryMode.NONE
        mutableState.update {
            it.copy(
                accessoryMode = DriverAccessoryMode.NONE,
                visibilityIssue = null,
                visibilityAcknowledgementRequired = null,
            )
        }
        publishMonitoringState()
        sessionStartedAtElapsedMs = null
        sessionStartedAtEpochMillis = null
        highestPersistedStage = null
        activeAlertId = null
        if (sessionId != null && driverId != null) {
            scope.launch {
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
                onFinished()
            }
        } else onFinished()
    }

    fun testWarningOutput(stage: WarningStage, sound: WarningSound, volume: PreferredVolume) {
        scope.launch {
            val delivery = warningOutputGateway.activateSafely(stage.warningOutputCommand(sound, volume))
            mutableState.update {
                it.copy(
                    debugWarningDelivery = delivery,
                    errorMessage = if (delivery == WarningDeliveryStatus.DELIVERED) null else "Speaker test failed.",
                )
            }
        }
    }

    private fun connect(device: ProvisionedDevice) {
        reconnectJob?.cancel()
        source?.stop()
        source = null
        sourceStreamUrl = null
        val generation = ++streamGeneration
        clearCameraReadings()
        mutableState.update { it.copy(streamState = StreamConnectionState.CONNECTING, errorMessage = null) }
        reconnectJob = scope.launch {
            try {
                val resolved = endpointResolver.resolve(device)
                if (resolved == null) {
                    scheduleReconnect("DriveAlert camera is unavailable on this network.")
                    return@launch
                }
                if (resolved != device) deviceRepository.saveActive(resolved)
                val next = Esp32MjpegFrameSource(
                    streamUrl = resolved.streamUrl,
                    onState = { status, message ->
                        if (generation == streamGeneration && streamWanted) {
                            mutableState.update { it.copy(streamState = status, errorMessage = message) }
                        }
                    },
                    onDisconnected = { error ->
                        if (generation == streamGeneration && streamWanted) {
                            scheduleReconnect(error?.message ?: "Camera stream ended.")
                        }
                    },
                )
                source = next
                sourceStreamUrl = resolved.streamUrl
                next.start { packet -> acceptFrame(packet, generation) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                scheduleReconnect(error.message ?: "Camera connection failed.")
            }
        }
    }

    private fun acceptFrame(packet: FramePacket<SharedBitmapFrame>, generation: Int) {
        if (!streamWanted || generation != streamGeneration) {
            packet.payload.release()
            return
        }
        lastFrameTimestampMs = packet.timestampMs
        reconnectAttempt = 0
        val frame = packet.payload
        val previous = latestPreview
        latestPreview = frame
        mutablePreviewFrame.value = frame
        if (mutableState.value.streamState != StreamConnectionState.CONNECTED || mutableState.value.errorMessage != null) {
            mutableState.update { it.copy(streamState = StreamConnectionState.CONNECTED, errorMessage = null) }
        }
        previous?.release()

        sampleBrightness(frame, packet.timestampMs)

        if (!inferenceGate.tryAcquire(packet.timestampMs)) return
        val inferenceSource = frame.tryRetain() ?: return
        processor.submitLazy(
            width = packet.width,
            height = packet.height,
            timestampMs = packet.timestampMs,
            bitmapFactory = {
                // Isolate inference pixels from the Bitmap retained by Compose,
                // but only after the processor accepts the work.
                inferenceSource.bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    ?: error("Camera frame could not be copied for face processing.")
            },
            releaseSource = inferenceSource::release,
        )
    }

    private fun sampleBrightness(frame: SharedBitmapFrame, timestampMs: Long) {
        if (!brightnessGate.tryAcquire(timestampMs) || brightnessSampleJob?.isActive == true) return
        val generation = brightnessGeneration
        brightnessSampleJob = scope.launch {
            val sampleSource = frame.tryRetain() ?: return@launch
            try {
                val luminance = withContext(Dispatchers.Default) {
                    FrameLuminanceSampler.sample(sampleSource.bitmap)
                }
                if (!streamWanted || generation != brightnessGeneration ||
                    mutableState.value.streamState != StreamConnectionState.CONNECTED ||
                    elapsedClock() - timestampMs > CAMERA_READING_STALE_MS
                ) return@launch
                val brightness = brightnessTracker.update(timestampMs, luminance)
                lastBrightnessTimestampMs = timestampMs
                mutableState.update {
                    it.copy(ambientLuminance = luminance, smoothedLuminance = brightness.smoothed, lowLight = brightness.lowLight)
                }
                if (BuildConfig.DEBUG) {
                    Log.d(VISIBILITY_LOG_TAG, "luminance=%.1f smoothed=%.1f lowLight=%s".format(luminance, brightness.smoothed, brightness.lowLight))
                }
            } finally {
                sampleSource.release()
            }
        }
    }

    private suspend fun acceptDetection(
        result: MonitoringDetectionResult,
        faceAttributes: FaceAttributeResult,
        lighting: FaceLightingAssessment,
    ) {
        if (!streamWanted || result.timestampMs < observationCutoffMs ||
            mutableState.value.streamState != StreamConnectionState.CONNECTED ||
            elapsedClock() - result.timestampMs > CAMERA_READING_STALE_MS
        ) return
        lastDetectionTimestampMs = result.timestampMs
        val calibrationCompleted = mutableState.value.calibrationInProgress && result.calibration.status == CalibrationStatus.COMPLETE
        val regionalVisibility = effectiveRegionalVisibility(faceAttributes)
        val lightingIssue = lightingVisibilityIssue(
            result.faceDetected, lighting,
            sceneDark = mutableState.value.lowLight == true &&
                lastBrightnessTimestampMs?.let { elapsedClock() - it in 0L..CAMERA_READING_STALE_MS } == true,
        )
        val observedVisibilityIssue = if (monitoringController.state.isActive) {
            when {
                !mutableState.value.alignmentConfirmed || !mutableState.value.driverTracking.locked -> VisibilityIssue.FACE_UNAVAILABLE
                lightingIssue != null -> lightingIssue
                !lighting.allowsAssessment(elapsedClock()) -> null
                regionalVisibility.lowerFaceObstructed && regionalVisibility.eyeRegionObstructed ->
                    VisibilityIssue.BOTH_REGIONS_OBSTRUCTED
                regionalVisibility.eyeRegionObstructed -> VisibilityIssue.EYE_REGION_OBSTRUCTED
                regionalVisibility.lowerFaceObstructed -> VisibilityIssue.LOWER_FACE_OBSTRUCTED
                else -> null
            }
        } else null
        val visibilityEffects = if (monitoringController.state.isActive) {
            visibilityAlertController.update(result.timestampMs, observedVisibilityIssue)
        } else emptyList()
        val visibilityIssue = observedVisibilityIssue ?: visibilityAlertController.reportedIssue
        mutableState.update {
            it.copy(
                faceDetected = result.faceDetected,
                detection = result,
                faceAttributes = faceAttributes,
                lighting = lighting,
                calibrationProgress = if (it.calibrationInProgress) result.calibration else it.calibrationProgress,
                calibrationInProgress = if (calibrationCompleted) false else it.calibrationInProgress,
                activeCalibration = result.activeCalibration ?: it.activeCalibration,
                visibilityIssue = visibilityIssue,
                visibilityAcknowledgementRequired = it.visibilityAcknowledgementRequired?.takeIf { pending ->
                    pending == visibilityIssue
                },
            )
        }
        if (!mutableState.value.driverTracking.locked || !mutableState.value.alignmentConfirmed || !lighting.allowsAssessment(elapsedClock())) {
            repeatWarningJob?.cancel()
            monitoringController.accept(emptyList(), emptySet())
        }
        if (repeatWarningSigns.none { it in result.currentlyConfirmedSigns() }) repeatWarningJob?.cancel()
        val calibration = result.activeCalibration ?: return
        val userId = boundDriverId ?: return
        if (calibrationCompleted && calibration.calibrationId != persistedCalibrationId) {
            calibrationRepository.saveAndActivate(userId, calibration, System.currentTimeMillis())
            persistedCalibrationId = calibration.calibrationId
        }
        val sessionId = monitoringController.state.sessionId
        if (sessionId != null) {
            result.events.forEach { sign ->
                monitoringLogWriter?.sign(userId, sessionId, toEpochMillis(sign.timestampMs), sign)
            }
            val effects = monitoringController.accept(result.events, result.currentlyConfirmedSigns())
            handleMonitoringEffects(effects, userId, sessionId)
            persistHighestStage(userId, sessionId)
            publishMonitoringState()
            // Speaker delivery can suspend; account for this frame's evidence first.
            if (sessionId == monitoringController.state.sessionId && result.timestampMs >= observationCutoffMs) {
                handleVisibilityEffects(visibilityEffects)
            }
        }
    }

    private suspend fun handleVisibilityEffects(effects: List<VisibilityAlertEffect>) {
        val sessionId = monitoringController.state.sessionId ?: return
        val generation = streamGeneration
        effects.forEach { effect ->
            when (effect) {
                is VisibilityAlertEffect.Notify -> {
                    if (mutableState.value.visibilityIssue != effect.issue) return@forEach
                    if (effect.issue != VisibilityIssue.FACE_UNAVAILABLE && effect.issue != VisibilityIssue.INSUFFICIENT_LIGHTING) {
                        mutableState.update { it.copy(visibilityAcknowledgementRequired = effect.issue) }
                    }
                    visibilityOutputGateway.activateSafely(
                        VisibilityOutputCommand(effect.issue, monitoringVolume),
                    )
                    if (sessionId != monitoringController.state.sessionId || generation != streamGeneration ||
                        mutableState.value.visibilityIssue != effect.issue
                    ) return@forEach
                    visibilityNotificationCoordinator.notify(
                        issue = effect.issue,
                        enabled = monitoringNotificationsEnabled,
                    )
                }
                VisibilityAlertEffect.Clear -> {
                    if (visibilityAlertController.reportedIssue != null) return@forEach
                    visibilityNotificationCoordinator.clear()
                    mutableState.update { it.copy(visibilityAcknowledgementRequired = null) }
                }
            }
        }
    }

    fun continueWithDegradedMonitoring() {
        val issue = mutableState.value.visibilityAcknowledgementRequired ?: return
        if (issue == VisibilityIssue.INSUFFICIENT_LIGHTING || issue == VisibilityIssue.FACE_UNAVAILABLE) return
        visibilityAlertController.acknowledge(issue)
        visibilityNotificationCoordinator.clear()
        mutableState.update { it.copy(visibilityAcknowledgementRequired = null) }
    }

    fun dismissVisibilityAcknowledgement() {
        mutableState.update { it.copy(visibilityAcknowledgementRequired = null) }
    }

    private fun effectiveRegionalVisibility(faceAttributes: FaceAttributeResult): RegionalVisibility = RegionalVisibility(
        lowerFaceObstructed = monitoringController.state.isActive && monitoringAccessoryMode.declaresLowerFaceObstruction ||
            faceAttributes.lowerFaceObstructed == true,
        eyeRegionObstructed = monitoringController.state.isActive && monitoringAccessoryMode.declaresEyeRegionObstruction ||
            faceAttributes.eyeRegionObstructed == true,
    )

    private suspend fun handleMonitoringEffects(
        effects: List<MonitoringEffect>,
        driverId: String,
        sessionId: String,
    ) {
        for (effect in effects) when (effect) {
            is MonitoringEffect.ActivateWarning -> {
                repeatWarningJob?.cancel()
                val delivery = activateLoggedWarning(driverId, sessionId,
                    effect.stage.warningOutputCommand(monitoringSound, monitoringVolume),
                )
                monitoringController.setWarningDelivery(delivery)
            }
            is MonitoringEffect.RepeatWarning -> {
                // Do not block frame processing or queue reminders behind a slow HTTP request.
                if (repeatWarningJob?.isActive == true) continue
                val generation = streamGeneration
                repeatWarningSigns = effect.signs
                repeatWarningJob = scope.launch {
                    val current = mutableState.value
                    val detection = current.detection ?: return@launch
                    if (sessionId != monitoringController.state.sessionId || generation != streamGeneration ||
                        monitoringController.state.currentStage != effect.stage ||
                        current.streamState != StreamConnectionState.CONNECTED ||
                        !current.driverTracking.locked || !current.alignmentConfirmed ||
                        !current.lighting.allowsAssessment(elapsedClock()) ||
                        detection.timestampMs < observationCutoffMs ||
                        elapsedClock() - detection.timestampMs > DriverFaceSelector.MAX_FRAME_GAP_MS ||
                        effect.signs.none { it in detection.currentlyConfirmedSigns() }
                    ) return@launch
                    activateLoggedWarning(driverId, sessionId,
                        effect.stage.warningOutputCommand(monitoringSound, monitoringVolume).copy(repeat = true),
                    )
                }
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

    private suspend fun activateLoggedWarning(
        driverId: String, sessionId: String,
        command: com.lhordkent.drivealert.monitoring.WarningOutputCommand,
    ): WarningDeliveryStatus {
        val requestId = monitoringLogWriter?.request(driverId, sessionId, wallClock(), command.stage.ordinal + 1, command.repeat)
        try {
            val result = warningOutputGateway.activateSafely(command)
            requestId?.let { monitoringLogWriter?.result(driverId, sessionId, it, wallClock(), result) }
            return result
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            requestId?.let { monitoringLogWriter?.result(driverId, sessionId, it, wallClock(), null) }
            throw cancelled
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

    private fun resetBrightness() {
        brightnessGeneration++
        brightnessSampleJob?.cancel()
        brightnessSampleJob = null
        brightnessGate.reset()
        brightnessTracker.reset()
        lastBrightnessTimestampMs = null
        mutableState.update { it.copy(lowLight = null, ambientLuminance = null, smoothedLuminance = null) }
    }

    private fun resetLighting() {
        repeatWarningJob?.cancel()
        // Prevent queued results from the previous session from reusing cached quality.
        observationCutoffMs = elapsedClock()
        targetConfigurationRevision = processor.configure(if (regionKey != null) mutableState.value.driverRegion else null)
        targetGeneration = -1L
        lightingTracker.reset()
        engine.invalidateDriverTarget()
        mutableState.update { it.copy(lighting = FaceLightingAssessment(), driverTracking = DriverTrackingState(if (regionKey == null) DriverTrackingStatus.UNCONFIGURED else DriverTrackingStatus.ACQUIRING), detection = null, faceAttributes = null) }
    }

    private fun clearCameraReadings() {
        observationCutoffMs = elapsedClock()
        lastFrameTimestampMs = null
        lastDetectionTimestampMs = null
        resetBrightness()
        resetLighting()
        // An unobserved interval cannot keep a previous drowsiness episode active.
        // Confirmed events and warning history remain in the current session.
        monitoringController.accept(emptyList(), emptySet())
        visibilityAlertController.interrupt()
        visibilityNotificationCoordinator.clear()
        mutableState.update {
            it.copy(faceDetected = null, detection = null, faceAttributes = null,
                visibilityIssue = null, visibilityAcknowledgementRequired = null)
        }
    }

    private fun expireCameraReadings() {
        if (mutableState.value.streamState != StreamConnectionState.CONNECTED) return
        val now = elapsedClock()
        val frameStale = lastFrameTimestampMs?.let { now - it > CAMERA_READING_STALE_MS } == true
        val detectionStale = lastDetectionTimestampMs?.let { now - it > DriverFaceSelector.MAX_FRAME_GAP_MS } == true
        if (frameStale || detectionStale) {
            if (BuildConfig.DEBUG) Log.d("DriverFaceLock", "expire frame=$frameStale detection=$detectionStale age=${lastDetectionTimestampMs?.let { now - it }}ms")
            clearCameraReadings()
        } else {
            val lighting = mutableState.value.lighting
            if (lighting.timestampMs != null && now - lighting.timestampMs > FaceLightingAssessment.FRESHNESS_MS) {
                resetLighting()
                monitoringController.accept(emptyList(), emptySet())
                mutableState.update { it.copy(detection = null, visibilityIssue = null, visibilityAcknowledgementRequired = null) }
                visibilityAlertController.interrupt()
                visibilityNotificationCoordinator.clear()
            }
            if (lastBrightnessTimestampMs?.let { now - it > CAMERA_READING_STALE_MS } == true) resetBrightness()
        }
    }

    private fun toEpochMillis(elapsedMs: Long): Long {
        val elapsedStart = requireNotNull(sessionStartedAtElapsedMs)
        val epochStart = requireNotNull(sessionStartedAtEpochMillis)
        return epochStart + (elapsedMs - elapsedStart).coerceAtLeast(0L)
    }

    private fun scheduleReconnect(message: String) {
        streamGeneration++
        source?.stop()
        source = null
        sourceStreamUrl = null
        clearCameraReadings()
        if (!streamWanted) return
        mutableState.update {
            it.copy(
                streamState = if (reconnectAttempt >= 3) StreamConnectionState.UNAVAILABLE else StreamConnectionState.RECONNECTING,
                errorMessage = message,
            )
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = minOf(8_000L, 1_000L shl minOf(reconnectAttempt, 3))
            reconnectAttempt++
            delay(delayMs)
            activeDevice?.let(::connect)
        }
    }

    private fun stopStream(resetAlignment: Boolean) {
        streamWanted = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        streamGeneration++
        visionHealthJob?.cancel()
        visionHealthJob = null
        reconnectJob?.cancel()
        source?.stop()
        source = null
        sourceStreamUrl = null
        latestPreview?.release()
        latestPreview = null
        mutablePreviewFrame.value = null
        inferenceGate.reset()
        clearCameraReadings()
        mutableState.update {
            it.copy(
                faceDetected = null,
                faceAttributes = null,
                alignmentConfirmed = if (resetAlignment) false else it.alignmentConfirmed,
            )
        }
    }

    override fun close() {
        stopMonitoring(interrupted = true)
        stopStream(resetAlignment = true)
        processor.close()
    }

    companion object {
        private const val INFERENCE_INTERVAL_MS = 100L
        private const val MONITORING_TICK_MS = 250L
        private const val BRIGHTNESS_SAMPLE_INTERVAL_MS = 1_000L
        private const val CAMERA_READING_STALE_MS = 3_000L
        private const val VISIBILITY_LOG_TAG = "DriveAlertVisibility"
    }
}

/** Activity-scoped facade over the application-scoped camera and monitoring runtime. */
class DriverVisionViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = (application as DriveAlertApplication).container.driverVisionRuntime
    private var driverUserId: String? = null

    init { runtime.beginAppAlignmentSession() }

    val state: StateFlow<DriverVisionUiState> = runtime.state
    val previewFrame: StateFlow<SharedBitmapFrame?> = runtime.previewFrame

    fun bindDriver(driverUserId: String?) {
        this.driverUserId = driverUserId
        if (driverUserId == null &&
            (runtime.state.value.monitoring.isActive || MonitoringRecoveryStore(getApplication()).load() != null)
        ) DriveAlertMonitoringService.stop(getApplication(), interrupted = true)
        runtime.bindDriver(driverUserId)
    }

    fun startVision() = runtime.startVision()

    fun returnToBleProvisioning(onReady: () -> Unit = {}) {
        runtime.returnToBleProvisioning(onReady)
        DriveAlertMonitoringService.dismissAfterRuntimeStop(getApplication())
    }

    fun openBleSetup(onReady: () -> Unit = {}) {
        runtime.openBleSetup(onReady)
        DriveAlertMonitoringService.dismissAfterRuntimeStop(getApplication())
    }

    fun confirmAlignment() = runtime.confirmAlignment()
    fun updateDriverRegion(region: DriverRegion) = runtime.updateDriverRegion(region)

    fun disconnectCurrentSession(onDisconnected: () -> Unit = {}) {
        runtime.disconnectCurrentSession(onDisconnected)
        DriveAlertMonitoringService.dismissAfterRuntimeStop(getApplication())
    }

    fun prepareForAccountChange(onComplete: () -> Unit) {
        runtime.prepareForAccountChange(onComplete)
        DriveAlertMonitoringService.dismissAfterRuntimeStop(getApplication())
    }

    fun startCalibration() = runtime.startCalibration()
    fun beginCalibrationPhase() = runtime.beginCalibrationPhase()
    fun repeatCalibrationPhase() = runtime.repeatCalibrationPhase()
    fun cancelCalibration() = runtime.cancelCalibration()

    fun startMonitoring(
        sound: WarningSound = WarningSound.DIGITAL_BEEP,
        volume: PreferredVolume = PreferredVolume.MEDIUM,
        notificationsEnabled: Boolean = true,
        accessoryMode: DriverAccessoryMode = DriverAccessoryMode.NONE,
    ) {
        val driverId = driverUserId ?: return
        DriveAlertMonitoringService.start(
            context = getApplication(),
            driverId = driverId,
            sound = sound,
            volume = volume,
            notificationsEnabled = notificationsEnabled,
            accessoryMode = accessoryMode,
        )
    }

    fun stopMonitoring(interrupted: Boolean = false) =
        DriveAlertMonitoringService.stop(getApplication(), interrupted)

    fun testWarningOutput(stage: WarningStage, sound: WarningSound, volume: PreferredVolume) =
        runtime.testWarningOutput(stage, sound, volume)

    fun continueWithDegradedMonitoring() = runtime.continueWithDegradedMonitoring()
    fun dismissVisibilityAcknowledgement() = runtime.dismissVisibilityAcknowledgement()
}

private fun MonitoringDetectionResult.currentlyConfirmedSigns() = buildSet {
    if (eye.state == TemporalState.CONFIRMED) add(SignType.PROLONGED_EYE_CLOSURE.toVisibleSign())
    if (yawn.state == TemporalState.CONFIRMED) add(SignType.YAWNING.toVisibleSign())
    if (head.state == TemporalState.CONFIRMED) add(SignType.HEAD_NODDING.toVisibleSign())
}

internal fun shouldConnectStreamForDeviceUpdate(
    streamWanted: Boolean,
    nextStreamUrl: String?,
    activeSourceStreamUrl: String?,
    connectionAttemptActive: Boolean,
): Boolean {
    if (!streamWanted || nextStreamUrl == null) return false
    return if (activeSourceStreamUrl != null) {
        activeSourceStreamUrl != nextStreamUrl
    } else {
        !connectionAttemptActive
    }
}
