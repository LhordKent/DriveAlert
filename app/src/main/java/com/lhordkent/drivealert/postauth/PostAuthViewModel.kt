package com.lhordkent.drivealert.postauth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lhordkent.drivealert.data.repository.AlertRepository
import com.lhordkent.drivealert.data.repository.MonitoringSessionRepository
import com.lhordkent.drivealert.data.repository.DriverPreferenceRepository
import com.lhordkent.drivealert.data.profile.UserProfileRepository
import com.lhordkent.drivealert.data.profile.UserRole
import com.lhordkent.drivealert.data.connection.TrustedContactRepository
import com.lhordkent.drivealert.data.connection.TrustedContactConnection
import com.lhordkent.drivealert.data.connection.ConnectionParticipantRole
import com.lhordkent.drivealert.data.local.entity.ConnectionStatus
import com.lhordkent.drivealert.data.sync.StageSyncRepository
import com.lhordkent.drivealert.data.sync.SharedStage3Repository
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PostAuthViewModel(
    private val alertRepository: AlertRepository? = null,
    private val monitoringSessionRepository: MonitoringSessionRepository? = null,
    private val userProfileRepository: UserProfileRepository? = null,
    private val driverPreferenceRepository: DriverPreferenceRepository? = null,
    private val trustedContactRepository: TrustedContactRepository? = null,
    private val stageSyncRepository: StageSyncRepository? = null,
    private val sharedStage3Repository: SharedStage3Repository? = null,
    initialState: PostAuthUiState = seedState(),
) : ViewModel() {
    private var accountEmail: String = ""
    private var boundDriverUserId: String? = null
    private var localDataJob: Job? = null
    private var profileJob: Job? = null
    private var preferenceJob: Job? = null
    private var driverConnectionsJob: Job? = null
    private var trustedConnectionsJob: Job? = null
    private var syncRecordsJob: Job? = null
    private val sharedRecordJobs = mutableMapOf<String, Job>()

    var state by mutableStateOf(initialState)
        private set

    fun bindLocalData(driverUserId: String) {
        if (driverUserId == boundDriverUserId) return
        val alerts = alertRepository ?: return
        val sessions = monitoringSessionRepository ?: return
        boundDriverUserId = driverUserId
        localDataJob?.cancel()
        state = state.copy(isLocalDataLoading = true, localDataErrorMessage = null)
        localDataJob = viewModelScope.launch {
            combine(
                alerts.observeAlerts(driverUserId),
                sessions.observeSessions(driverUserId),
            ) { driverAlerts, monitoringSessions -> driverAlerts to monitoringSessions }
                .catch {
                    state = state.copy(
                        isLocalDataLoading = false,
                        localDataErrorMessage = "Local records could not be loaded.",
                    )
                }
                .collect { (driverAlerts, monitoringSessions) ->
                    state = state.copy(
                        driverAlerts = driverAlerts,
                        monitoringSessions = monitoringSessions,
                        isLocalDataLoading = false,
                        localDataErrorMessage = null,
                    )
                }
        }
    }

    fun bindCloudProfile(userId: String) {
        val repository = userProfileRepository ?: return
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            repository.observe(userId)
                .catch { /* Cloud profile remains unavailable until connectivity returns. */ }
                .collect { profile ->
                    if (profile != null) {
                        state = state.copy(
                            profileDisplayName = profile.fullName.ifBlank { state.profileDisplayName },
                            userRole = profile.userRole,
                        )
                    }
                }
        }
    }

    fun bindPreferences(userId: String) {
        val repository = driverPreferenceRepository ?: return
        preferenceJob?.cancel()
        preferenceJob = viewModelScope.launch {
            repository.observe(userId).collect { preferences ->
                state = state.copy(
                    warningSound = preferences.warningSound,
                    preferredVolume = preferences.preferredVolume,
                    notifications = preferences.notifications,
                )
            }
        }
    }

    fun bindConnections(userId: String) {
        val repository = trustedContactRepository ?: return
        driverConnectionsJob?.cancel()
        trustedConnectionsJob?.cancel()
        driverConnectionsJob = viewModelScope.launch {
            repository.observeForDriver(userId)
                .catch { state = state.copy(cloudConnectionErrorMessage = CLOUD_CONNECTION_ERROR) }
                .collect { connections ->
                    state = state.withDriverConnections(connections).copy(cloudConnectionErrorMessage = null)
                }
        }
        trustedConnectionsJob = viewModelScope.launch {
            repository.observeForTrustedContact(userId)
                .catch { state = state.copy(cloudConnectionErrorMessage = CLOUD_CONNECTION_ERROR) }
                .collect { connections ->
                    state = state.withTrustedConnections(connections).copy(cloudConnectionErrorMessage = null)
                    bindSharedRecordsForConnectedDrivers()
                }
        }
    }

    private fun bindSharedRecordsForConnectedDrivers() {
        val repository = sharedStage3Repository ?: return
        val activeDriverIds = state.connectedDrivers.mapTo(mutableSetOf()) { it.id }
        sharedRecordJobs.keys.filterNot(activeDriverIds::contains).forEach { removedId ->
            sharedRecordJobs.remove(removedId)?.cancel()
        }
        state.connectedDrivers.filterNot { sharedRecordJobs.containsKey(it.id) }.forEach { driver ->
            val driverId = driver.id
            val approvedAtEpochMillis = driver.connectedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            sharedRecordJobs[driverId] = viewModelScope.launch {
                repository.observeSharedRecords(driverId, approvedAtEpochMillis)
                    .catch { state = state.copy(cloudConnectionErrorMessage = CLOUD_CONNECTION_ERROR) }
                    .collect { records ->
                        state = state.copy(
                            connectedDrivers = state.connectedDrivers.map { driver ->
                                if (driver.id == driverId) driver.copy(
                                    sharedRecords = records.map { it.copy(sourceDriverName = driver.name) },
                                ) else driver
                            },
                        )
                    }
            }
        }
    }

    fun bindStageSyncRecords(userId: String) {
        val repository = stageSyncRepository ?: return
        syncRecordsJob?.cancel()
        syncRecordsJob = viewModelScope.launch {
            repository.observe(userId).collect { records -> state = state.copy(driverSyncRecords = records) }
        }
        repository.retryPending(userId)
    }

    fun setAccountEmail(email: String) {
        accountEmail = email.trim().lowercase()
    }

    fun setProfileDisplayName(displayName: String) {
        if (state.profileDisplayName.isBlank()) {
            state = state.copy(profileDisplayName = displayName.trim())
        }
    }

    fun chooseView(view: UserView) {
        state = state.copy(activeView = view)
        val userId = boundDriverUserId ?: return
        val repository = userProfileRepository ?: return
        val selectedRole = when (view) {
            UserView.DRIVER -> UserRole.DRIVER
            UserView.TRUSTED_CONTACT -> UserRole.TRUSTED_CONTACT
        }
        viewModelScope.launch {
            runCatching { repository.selectRole(userId, selectedRole) }
                .onSuccess { role -> state = state.copy(userRole = role) }
        }
    }

    fun completeDriverSetup() {
        state = state.copy(driverSetupComplete = true)
    }

    fun selectMonitoringScenario(scenario: MonitoringScenario) {
        state = state.copy(monitoringScenario = scenario)
    }

    fun setActiveWarningStage(stage: WarningStage?) {
        state = state.copy(activeWarningStage = stage)
    }

    fun reconnectDevice() {
        state = state.copy(
            deviceConnection = if (state.deviceConnection == DeviceConnectionState.CONNECTED) {
                DeviceConnectionState.RECONNECTING
            } else {
                DeviceConnectionState.CONNECTED
            },
        )
    }

    fun completeAlignmentCheck() {
        state = state.copy(alignmentReady = !state.alignmentReady)
    }

    fun completeCalibration() {
        state = state.copy(
            calibrationState = if (state.calibrationState == CalibrationState.READY) {
                CalibrationState.IN_PROGRESS
            } else {
                CalibrationState.READY
            },
        )
    }

    fun selectAlertFilter(stage: WarningStage?) {
        state = state.copy(alertFilter = stage)
    }

    fun updateSharingState(recordId: String, sharingState: SharingState) {
        state = state.copy(
            driverSyncRecords = state.driverSyncRecords.map { record ->
                if (record.id == recordId) record.copy(sharingState = sharingState) else record
            },
        )
    }

    fun sendConnectionRequest(targetView: UserView, email: String): String? {
        val normalized = email.trim().lowercase()
        if (!EMAIL_PATTERN.matches(normalized)) return "Enter a valid email address"
        if (normalized == accountEmail) return "Use a different email address"
        val allEmails = when (targetView) {
            UserView.DRIVER -> state.approvedContacts.map { it.email } +
                state.driverIncomingRequests.map { it.email } + state.driverOutgoingRequests.map { it.email }
            UserView.TRUSTED_CONTACT -> state.connectedDrivers.map { it.email } +
                state.trustedIncomingRequests.map { it.email } + state.trustedOutgoingRequests.map { it.email }
        }.map(String::lowercase)
        if (normalized in allEmails) return "A connection or request already exists for this email"

        val repository = trustedContactRepository
        if (repository != null) {
            val inviterRole = when (targetView) {
                UserView.DRIVER -> ConnectionParticipantRole.DRIVER
                UserView.TRUSTED_CONTACT -> ConnectionParticipantRole.TRUSTED_CONTACT
            }
            viewModelScope.launch {
                runCatching { repository.sendRequestByEmail(normalized, inviterRole) }
                    .onFailure { state = state.copy(cloudConnectionErrorMessage = "Connection request could not be sent. Check your internet connection and try again.") }
            }
            return null
        }
        val request = ConnectionRequest(
            id = UUID.randomUUID().toString(),
            name = normalized.substringBefore('@').replace('.', ' ').replaceFirstChar(Char::uppercase),
            email = normalized,
            direction = RequestDirection.OUTGOING,
            requestedAt = LocalDateTime.now(),
        )
        state = when (targetView) {
            UserView.DRIVER -> state.copy(driverOutgoingRequests = state.driverOutgoingRequests + request)
            UserView.TRUSTED_CONTACT -> state.copy(trustedOutgoingRequests = state.trustedOutgoingRequests + request)
        }
        return null
    }

    fun acceptRequest(view: UserView, requestId: String) {
        trustedContactRepository?.let { repository ->
            launchConnectionAction { repository.acceptRequest(requestId) }
            return
        }
        when (view) {
            UserView.DRIVER -> {
                val request = state.driverIncomingRequests.firstOrNull { it.id == requestId } ?: return
                state = state.copy(
                    driverIncomingRequests = state.driverIncomingRequests.filterNot { it.id == requestId },
                    approvedContacts = state.approvedContacts + Contact(
                        id = request.id,
                        name = request.name,
                        email = request.email,
                        approvedAt = LocalDateTime.now(),
                    ),
                )
            }
            UserView.TRUSTED_CONTACT -> {
                val request = state.trustedIncomingRequests.firstOrNull { it.id == requestId } ?: return
                state = state.copy(
                    trustedIncomingRequests = state.trustedIncomingRequests.filterNot { it.id == requestId },
                    connectedDrivers = state.connectedDrivers + ConnectedDriver(
                        id = request.id,
                        name = request.name,
                        email = request.email,
                        connectedAt = LocalDateTime.now(),
                        sharedRecords = emptyList(),
                    ),
                )
            }
        }
    }

    fun declineRequest(view: UserView, requestId: String) {
        trustedContactRepository?.let { repository ->
            launchConnectionAction { repository.declineRequest(requestId) }
            return
        }
        state = when (view) {
            UserView.DRIVER -> state.copy(driverIncomingRequests = state.driverIncomingRequests.filterNot { it.id == requestId })
            UserView.TRUSTED_CONTACT -> state.copy(trustedIncomingRequests = state.trustedIncomingRequests.filterNot { it.id == requestId })
        }
    }

    fun cancelRequest(view: UserView, requestId: String) {
        trustedContactRepository?.let { repository ->
            launchConnectionAction { repository.cancelRequest(requestId) }
            return
        }
        state = when (view) {
            UserView.DRIVER -> state.copy(driverOutgoingRequests = state.driverOutgoingRequests.filterNot { it.id == requestId })
            UserView.TRUSTED_CONTACT -> state.copy(trustedOutgoingRequests = state.trustedOutgoingRequests.filterNot { it.id == requestId })
        }
    }

    fun removeConnection(view: UserView, id: String) {
        trustedContactRepository?.let { repository ->
            launchConnectionAction { repository.revokeConnection(id) }
            return
        }
        state = when (view) {
            UserView.DRIVER -> state.copy(approvedContacts = state.approvedContacts.filterNot { it.id == id })
            UserView.TRUSTED_CONTACT -> state.copy(connectedDrivers = state.connectedDrivers.filterNot { it.id == id })
        }
    }

    private fun launchConnectionAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onFailure { state = state.copy(cloudConnectionErrorMessage = CLOUD_CONNECTION_ERROR) }
        }
    }

    fun selectWarningSound(sound: WarningSound) {
        state = state.copy(warningSound = sound)
        val userId = boundDriverUserId ?: return
        val repository = driverPreferenceRepository ?: return
        viewModelScope.launch { repository.setWarningSound(userId, sound) }
    }

    fun selectPreferredVolume(volume: PreferredVolume) {
        state = state.copy(preferredVolume = volume)
        val userId = boundDriverUserId ?: return
        val repository = driverPreferenceRepository ?: return
        viewModelScope.launch { repository.setPreferredVolume(userId, volume) }
    }

    fun updateNotifications(preferences: NotificationPreferences) {
        state = state.copy(notifications = preferences)
        val userId = boundDriverUserId ?: return
        val repository = driverPreferenceRepository ?: return
        viewModelScope.launch { repository.setNotifications(userId, preferences) }
    }

    fun clearForSignOut() {
        boundDriverUserId?.let { stageSyncRepository?.cancelPendingWork(it) }
        localDataJob?.cancel()
        localDataJob = null
        profileJob?.cancel()
        profileJob = null
        preferenceJob?.cancel()
        preferenceJob = null
        driverConnectionsJob?.cancel()
        driverConnectionsJob = null
        trustedConnectionsJob?.cancel()
        trustedConnectionsJob = null
        syncRecordsJob?.cancel()
        syncRecordsJob = null
        sharedRecordJobs.values.forEach(Job::cancel)
        sharedRecordJobs.clear()
        boundDriverUserId = null
        accountEmail = ""
        state = if (alertRepository == null) seedState() else PostAuthUiState()
    }

    companion object {
        private const val CLOUD_CONNECTION_ERROR = "Cloud connection data is unavailable. Check your internet connection and try again."
        private val EMAIL_PATTERN = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")

        fun seedState(today: LocalDate = LocalDate.now()): PostAuthUiState {
            fun at(daysAgo: Long, hour: Int, minute: Int) = LocalDateTime.of(today.minusDays(daysAgo), LocalTime.of(hour, minute))
            val driverAlerts = listOf(
                AlertEvent("driver-1", at(0, 19, 42), setOf(VisibleSign.PROLONGED_EYE_CLOSURE, VisibleSign.YAWNING), WarningStage.STAGE_3),
                AlertEvent("driver-2", at(0, 14, 18), setOf(VisibleSign.PROLONGED_EYE_CLOSURE), WarningStage.STAGE_2),
                AlertEvent("driver-3", at(0, 8, 12), setOf(VisibleSign.HEAD_NODDING), WarningStage.STAGE_1),
                AlertEvent("driver-4", at(1, 21, 5), setOf(VisibleSign.PROLONGED_EYE_CLOSURE), WarningStage.STAGE_3),
                AlertEvent("driver-5", at(3, 18, 18), setOf(VisibleSign.YAWNING), WarningStage.STAGE_2),
                AlertEvent("driver-6", at(5, 9, 5), setOf(VisibleSign.PROLONGED_EYE_CLOSURE), WarningStage.STAGE_1),
            )

            val driverSyncRecords = listOf(
                Stage3SyncRecord("sync-1", at(0, 19, 42), SyncRecordKind.STAGE_3_TRANSITION, setOf(VisibleSign.PROLONGED_EYE_CLOSURE, VisibleSign.YAWNING), SharingState.SHARED),
                Stage3SyncRecord("sync-2", at(1, 21, 5), SyncRecordKind.STAGE_3_PERSISTENCE, setOf(VisibleSign.PROLONGED_EYE_CLOSURE), SharingState.PENDING),
            )

            fun shared(id: String, driverId: String, driverName: String, daysAgo: Long, hour: Int, kind: SyncRecordKind, signs: Set<VisibleSign>) = Stage3SyncRecord(
                id = id,
                occurredAt = at(daysAgo, hour, 42),
                kind = kind,
                signs = signs,
                sharingState = SharingState.SHARED,
                sourceDriverId = driverId,
                sourceDriverName = driverName,
                receivedAt = at(daysAgo, hour, 44),
            )

            val adrianEvents = listOf(
                shared("adrian-1", "adrian", "Adrian Cruz", 0, 7, SyncRecordKind.STAGE_3_TRANSITION, setOf(VisibleSign.PROLONGED_EYE_CLOSURE)),
                shared("adrian-2", "adrian", "Adrian Cruz", 2, 18, SyncRecordKind.STAGE_3_PERSISTENCE, setOf(VisibleSign.HEAD_NODDING)),
                shared("adrian-3", "adrian", "Adrian Cruz", 4, 9, SyncRecordKind.STAGE_3_TRANSITION, setOf(VisibleSign.PROLONGED_EYE_CLOSURE, VisibleSign.YAWNING)),
            )
            val biancaEvents = listOf(
                shared("bianca-1", "bianca", "Bianca Ramos", 1, 20, SyncRecordKind.STAGE_3_TRANSITION, setOf(VisibleSign.YAWNING)),
            )

            return PostAuthUiState(
                driverAlerts = driverAlerts,
                driverSyncRecords = driverSyncRecords,
                approvedContacts = listOf(Contact("mara", "Mara Santos", "mara.santos@gmail.com", at(21, 10, 0))),
                driverIncomingRequests = listOf(ConnectionRequest("driver-in", "Paolo Reyes", "paolo.reyes@gmail.com", RequestDirection.INCOMING, at(0, 10, 15))),
                driverOutgoingRequests = listOf(ConnectionRequest("driver-out", "Lea Torres", "lea.torres@gmail.com", RequestDirection.OUTGOING, at(1, 16, 20))),
                connectedDrivers = listOf(
                    ConnectedDriver("adrian", "Adrian Cruz", "adrian.cruz@gmail.com", at(40, 9, 0), adrianEvents),
                    ConnectedDriver("bianca", "Bianca Ramos", "bianca.ramos@gmail.com", at(18, 14, 0), biancaEvents),
                ),
                trustedIncomingRequests = listOf(ConnectionRequest("trusted-in-1", "Nico Valdez", "nico.valdez@gmail.com", RequestDirection.INCOMING, at(0, 11, 0))),
                trustedOutgoingRequests = listOf(ConnectionRequest("trusted-out-1", "Elena Dizon", "elena.dizon@gmail.com", RequestDirection.OUTGOING, at(2, 13, 0))),
            )
        }
    }
}

class PostAuthViewModelFactory(
    private val alertRepository: AlertRepository,
    private val monitoringSessionRepository: MonitoringSessionRepository,
    private val userProfileRepository: UserProfileRepository,
    private val driverPreferenceRepository: DriverPreferenceRepository,
    private val trustedContactRepository: TrustedContactRepository,
    private val stageSyncRepository: StageSyncRepository,
    private val sharedStage3Repository: SharedStage3Repository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PostAuthViewModel::class.java))
        return PostAuthViewModel(
            alertRepository = alertRepository,
            monitoringSessionRepository = monitoringSessionRepository,
            userProfileRepository = userProfileRepository,
            driverPreferenceRepository = driverPreferenceRepository,
            trustedContactRepository = trustedContactRepository,
            stageSyncRepository = stageSyncRepository,
            sharedStage3Repository = sharedStage3Repository,
            initialState = PostAuthUiState(isLocalDataLoading = true),
        ) as T
    }
}

private fun PostAuthUiState.withDriverConnections(
    connections: List<TrustedContactConnection>,
): PostAuthUiState {
    val approved = connections.filter { it.status == ConnectionStatus.APPROVED }.map { connection ->
        Contact(
            id = connection.connectionId,
            name = connection.trustedContactName,
            email = connection.trustedContactEmail,
            approvedAt = connection.approvedAtEpochMillis.toLocalDateTimeOrNow(),
        )
    }
    val pending = connections.filter { it.status == ConnectionStatus.PENDING }
    val outgoing = pending.filter { it.requestedByUserId == it.driverUserId }.map { connection ->
        ConnectionRequest(
            id = connection.connectionId,
            name = connection.trustedContactName,
            email = connection.trustedContactEmail,
            direction = RequestDirection.OUTGOING,
            requestedAt = connection.requestedAtEpochMillis.toLocalDateTime(),
        )
    }
    return copy(
        approvedContacts = approved,
        driverIncomingRequests = pending.filter { it.requestedByUserId != it.driverUserId }.map { connection ->
            ConnectionRequest(
                id = connection.connectionId,
                name = connection.trustedContactName,
                email = connection.trustedContactEmail,
                direction = RequestDirection.INCOMING,
                requestedAt = connection.requestedAtEpochMillis.toLocalDateTime(),
            )
        },
        driverOutgoingRequests = outgoing,
    )
}

private fun PostAuthUiState.withTrustedConnections(
    connections: List<TrustedContactConnection>,
): PostAuthUiState {
    val drivers = connections.filter { it.status == ConnectionStatus.APPROVED }.map { connection ->
        ConnectedDriver(
            id = connection.driverUserId,
            name = connection.driverName,
            email = connection.driverEmail,
            connectedAt = connection.approvedAtEpochMillis.toLocalDateTimeOrNow(),
            sharedRecords = connectedDrivers.firstOrNull { it.id == connection.driverUserId }?.sharedRecords.orEmpty(),
            connectionId = connection.connectionId,
        )
    }
    val pending = connections.filter { it.status == ConnectionStatus.PENDING }
    val incoming = pending.filter { it.requestedByUserId != it.trustedContactUserId }.map { connection ->
        ConnectionRequest(
            id = connection.connectionId,
            name = connection.driverName,
            email = connection.driverEmail,
            direction = RequestDirection.INCOMING,
            requestedAt = connection.requestedAtEpochMillis.toLocalDateTime(),
        )
    }
    return copy(
        connectedDrivers = drivers,
        trustedIncomingRequests = incoming,
        trustedOutgoingRequests = pending.filter { it.requestedByUserId == it.trustedContactUserId }.map { connection ->
            ConnectionRequest(
                id = connection.connectionId,
                name = connection.driverName,
                email = connection.driverEmail,
                direction = RequestDirection.OUTGOING,
                requestedAt = connection.requestedAtEpochMillis.toLocalDateTime(),
            )
        },
    )
}

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

private fun Long?.toLocalDateTimeOrNow(): LocalDateTime = this?.toLocalDateTime() ?: LocalDateTime.now()
