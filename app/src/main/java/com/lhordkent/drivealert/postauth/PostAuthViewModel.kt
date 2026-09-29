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
import com.lhordkent.drivealert.data.profile.UserProfileUpdate
import com.lhordkent.drivealert.data.profile.UserRole
import com.lhordkent.drivealert.data.connection.TrustedContactRepository
import com.lhordkent.drivealert.data.connection.TrustedContactConnection
import com.lhordkent.drivealert.data.connection.ConnectionCode
import com.lhordkent.drivealert.data.connection.ConnectionCodeNotFoundException
import com.lhordkent.drivealert.data.connection.ExistingConnectionException
import com.lhordkent.drivealert.data.connection.SelfConnectionException
import com.lhordkent.drivealert.data.local.entity.ConnectionStatus
import com.lhordkent.drivealert.data.sync.StageSyncRepository
import com.lhordkent.drivealert.data.sync.SharedStage3Repository
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
    private val todayProvider: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private var accountEmail: String = ""
    private var boundDriverUserId: String? = null
    private var boundProfileUserId: String? = null
    private var boundConnectionUserId: String? = null
    private var localDataJob: Job? = null
    private var profileJob: Job? = null
    private var preferenceJob: Job? = null
    private var driverConnectionsJob: Job? = null
    private var trustedConnectionsJob: Job? = null
    private var syncRecordsJob: Job? = null
    private val sharedRecordJobs = mutableMapOf<String, Job>()
    private val sharedViewStateJobs = mutableMapOf<String, Job>()

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
                        alertInsights = calculateAlertHistoryInsights(driverAlerts, todayProvider()),
                        monitoringSessions = monitoringSessions,
                        isLocalDataLoading = false,
                        localDataErrorMessage = null,
                    )
                }
        }
    }

    fun bindCloudProfile(userId: String, displayName: String, email: String) {
        val repository = userProfileRepository ?: return
        boundProfileUserId = userId
        profileJob?.cancel()
        state = state.copy(isProfileLoading = true)
        profileJob = viewModelScope.launch {
            launch {
                repository.observe(userId)
                    .catch {
                        state = state.copy(
                            isProfileLoading = false,
                            profileErrorMessage = "Your saved profile is unavailable. Connect once, then try again.",
                        )
                    }
                    .collect { profile ->
                        if (profile != null) {
                            state = state.copy(
                                profileDisplayName = profile.fullName.ifBlank { state.profileDisplayName },
                                profileFirstName = profile.firstName,
                                profileMiddleName = profile.middleName.orEmpty(),
                                profileLastName = profile.lastName,
                                profilePhoneNumber = profile.phoneNumber.orEmpty(),
                                userRole = profile.userRole,
                                activeView = if (profile.userRole == UserRole.TRUSTED_CONTACT) UserView.TRUSTED_CONTACT else UserView.DRIVER,
                                connectionCode = profile.connectionCode,
                                isProfileLoading = false,
                                profileErrorMessage = null,
                            )
                        }
                    }
            }
            launch {
                runCatching { repository.ensureProfile(userId, displayName, email) }
                    .onFailure {
                        if (state.userRole == null) {
                            state = state.copy(
                                profileErrorMessage = "Your connection code is unavailable while offline.",
                            )
                        }
                    }
            }
        }
    }

    fun updateProfile(firstName: String, middleName: String, lastName: String, phoneNumber: String) {
        val userId = boundProfileUserId ?: return
        val repository = userProfileRepository ?: return
        val update = UserProfileUpdate(
            firstName = firstName.trim(),
            middleName = middleName.trim().takeIf(String::isNotBlank),
            lastName = lastName.trim(),
            phoneNumber = phoneNumber.trim().takeIf(String::isNotBlank),
        )
        if (update.firstName.isBlank() || update.lastName.isBlank()) {
            state = state.copy(
                profileUpdateSuccessMessage = null,
                profileUpdateErrorMessage = "First name and last name are required.",
            )
            return
        }

        state = state.copy(
            isProfileUpdating = true,
            profileUpdateSuccessMessage = null,
            profileUpdateErrorMessage = null,
        )
        viewModelScope.launch {
            runCatching { repository.update(userId, update) }
                .onSuccess {
                    state = state.copy(
                        profileDisplayName = update.fullName,
                        profileFirstName = update.firstName,
                        profileMiddleName = update.middleName.orEmpty(),
                        profileLastName = update.lastName,
                        profilePhoneNumber = update.phoneNumber.orEmpty(),
                        isProfileUpdating = false,
                        profileUpdateSuccessMessage = "Profile updated.",
                        profileUpdateErrorMessage = null,
                    )
                }
                .onFailure {
                    state = state.copy(
                        isProfileUpdating = false,
                        profileUpdateSuccessMessage = null,
                        profileUpdateErrorMessage = "Your profile could not be updated. Check your connection and try again.",
                    )
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
        boundConnectionUserId = userId
        driverConnectionsJob?.cancel()
        trustedConnectionsJob?.cancel()
        state = state.copy(driverConnectionsLoading = true, trustedConnectionsLoading = true, cloudConnectionErrorMessage = null)
        driverConnectionsJob = viewModelScope.launch {
            repository.observeForDriver(userId)
                .catch { state = state.copy(driverConnectionsLoading = false, cloudConnectionErrorMessage = CLOUD_CONNECTION_ERROR) }
                .collect { connections ->
                    state = state.withDriverConnections(connections).copy(driverConnectionsLoading = false)
                }
        }
        trustedConnectionsJob = viewModelScope.launch {
            repository.observeForTrustedContact(userId)
                .catch { state = state.copy(trustedConnectionsLoading = false, cloudConnectionErrorMessage = CLOUD_CONNECTION_ERROR) }
                .collect { connections ->
                    state = state.withTrustedConnections(connections).copy(trustedConnectionsLoading = false)
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
        sharedViewStateJobs.keys.filterNot(activeDriverIds::contains).forEach { removedId ->
            sharedViewStateJobs.remove(removedId)?.cancel()
        }
        state.connectedDrivers.filterNot { sharedRecordJobs.containsKey(it.id) }.forEach { driver ->
            val driverId = driver.id
            state = state.copy(connectedDrivers = state.connectedDrivers.map {
                if (it.id == driverId) it.copy(sharedRecordsLoading = true, sharedRecordsErrorMessage = null) else it
            })
            val approvedAtEpochMillis = driver.connectedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            sharedRecordJobs[driverId] = viewModelScope.launch {
                repository.observeSharedRecords(driverId, approvedAtEpochMillis)
                    .catch {
                        state = state.copy(connectedDrivers = state.connectedDrivers.map { driver ->
                            if (driver.id == driverId) driver.copy(sharedRecordsLoading = false, sharedRecordsErrorMessage = CLOUD_CONNECTION_ERROR) else driver
                        })
                    }
                    .collect { records ->
                        state = state.copy(
                            connectedDrivers = state.connectedDrivers.map { driver ->
                                if (driver.id == driverId) driver.copy(
                                    sharedRecords = records.map { it.copy(sourceDriverName = driver.name) },
                                    unreadRecordCount = unreadCount(records, driver.lastViewedAt),
                                    sharedRecordsLoading = false,
                                    sharedRecordsErrorMessage = null,
                                ) else driver
                            },
                        )
                    }
            }
            val trustedUserId = boundConnectionUserId ?: return@forEach
            sharedViewStateJobs[driverId] = viewModelScope.launch {
                repository.observeViewState(trustedUserId, driverId)
                    .catch { emit(com.lhordkent.drivealert.data.sync.TrustedDriverViewState()) }
                    .collect { viewState ->
                        val lastViewedAt = viewState.lastViewedAtEpochMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()
                        }
                        state = state.copy(connectedDrivers = state.connectedDrivers.map { current ->
                            if (current.id == driverId) current.copy(
                                lastViewedAt = lastViewedAt,
                                unreadRecordCount = unreadCount(current.sharedRecords, lastViewedAt),
                            ) else current
                        })
                    }
            }
        }
    }

    fun markSharedRecordsViewed(driverUserId: String) {
        val trustedUserId = boundConnectionUserId ?: return
        val repository = sharedStage3Repository ?: return
        viewModelScope.launch { runCatching { repository.markViewed(trustedUserId, driverUserId) } }
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

    fun lookupConnectionCode(code: String) {
        val normalized = ConnectionCode.normalize(code)
        if (!ConnectionCode.isValid(normalized)) {
            state = state.copy(connectionInvite = ConnectionInviteUiState(errorMessage = "Enter a complete DriveAlert connection code"))
            return
        }
        val repository = trustedContactRepository ?: return
        val currentUserId = boundDriverUserId ?: return
        state = state.copy(connectionInvite = ConnectionInviteUiState(isLookingUp = true))
        viewModelScope.launch {
            runCatching { repository.resolveConnectionCode(normalized) }
                .onSuccess { target ->
                    state = state.copy(connectionInvite = when {
                        target == null -> ConnectionInviteUiState(errorMessage = CODE_NOT_FOUND_MESSAGE)
                        target.userId == currentUserId -> ConnectionInviteUiState(errorMessage = "Use another person's connection code")
                        else -> ConnectionInviteUiState(
                            targetUserId = target.userId,
                            targetDisplayName = target.displayName,
                            normalizedCode = target.normalizedCode,
                        )
                    })
                }
                .onFailure {
                    state = state.copy(connectionInvite = ConnectionInviteUiState(errorMessage = LOOKUP_ERROR_MESSAGE))
                }
        }
    }

    fun clearConnectionInvite() {
        state = state.copy(connectionInvite = ConnectionInviteUiState())
    }

    fun sendTrustedContactRequest() = sendConnectionRequest(RequesterSide.DRIVER)

    fun sendDriverRequest() = sendConnectionRequest(RequesterSide.TRUSTED_CONTACT)

    private fun sendConnectionRequest(requesterSide: RequesterSide) {
        val repository = trustedContactRepository ?: return
        val userId = boundDriverUserId ?: return
        val code = state.connectionInvite.normalizedCode ?: return
        state = state.copy(connectionInvite = state.connectionInvite.copy(isSending = true, errorMessage = null))
        viewModelScope.launch {
            runCatching {
                val name = state.profileDisplayName.ifBlank { "DriveAlert user" }
                when (requesterSide) {
                    RequesterSide.DRIVER -> repository.sendTrustedContactRequest(userId, name, accountEmail, code)
                    RequesterSide.TRUSTED_CONTACT -> repository.sendDriverRequest(userId, name, accountEmail, code)
                }
            }.onSuccess {
                state = state.copy(connectionInvite = state.connectionInvite.copy(isSending = false, requestSent = true))
            }.onFailure { error ->
                val message = when (error) {
                    is ConnectionCodeNotFoundException -> CODE_NOT_FOUND_MESSAGE
                    is SelfConnectionException -> "Use another person's connection code"
                    is ExistingConnectionException -> "A connection or pending request already exists"
                    else -> "Connection request could not be sent. Check your internet connection and try again."
                }
                state = state.copy(connectionInvite = state.connectionInvite.copy(isSending = false, errorMessage = message))
            }
        }
    }

    fun acceptDriverIncomingRequest(requestId: String) {
        if (state.driverIncomingRequests.none { it.id == requestId }) return
        trustedContactRepository?.let { repository ->
            launchConnectionAction(requestId) { repository.acceptRequest(requestId) }
            return
        }
        val request = state.driverIncomingRequests.first { it.id == requestId }
        state = state.copy(
            driverIncomingRequests = state.driverIncomingRequests.filterNot { it.id == requestId },
            approvedContacts = state.approvedContacts + Contact(
                request.id, request.name, request.email, LocalDateTime.now(),
            ),
        )
    }

    fun acceptTrustedIncomingRequest(requestId: String) {
        if (state.trustedIncomingRequests.none { it.id == requestId }) return
        trustedContactRepository?.let { repository ->
            launchConnectionAction(requestId) { repository.acceptRequest(requestId) }
            return
        }
        val request = state.trustedIncomingRequests.firstOrNull { it.id == requestId } ?: return
        state = state.copy(
            trustedIncomingRequests = state.trustedIncomingRequests.filterNot { it.id == requestId },
            connectedDrivers = state.connectedDrivers + ConnectedDriver(
                id = request.id, name = request.name, email = request.email,
                connectedAt = LocalDateTime.now(), sharedRecords = emptyList(),
            ),
        )
    }

    fun declineDriverIncomingRequest(requestId: String) {
        if (state.driverIncomingRequests.none { it.id == requestId }) return
        trustedContactRepository?.let { repository ->
            launchConnectionAction(requestId) { repository.declineRequest(requestId) }
            return
        }
        state = state.copy(driverIncomingRequests = state.driverIncomingRequests.filterNot { it.id == requestId })
    }

    fun declineTrustedIncomingRequest(requestId: String) {
        if (state.trustedIncomingRequests.none { it.id == requestId }) return
        trustedContactRepository?.let { repository ->
            launchConnectionAction(requestId) { repository.declineRequest(requestId) }
            return
        }
        state = state.copy(trustedIncomingRequests = state.trustedIncomingRequests.filterNot { it.id == requestId })
    }

    fun cancelDriverOutgoingRequest(requestId: String) {
        if (state.driverOutgoingRequests.none { it.id == requestId }) return
        trustedContactRepository?.let { repository ->
            launchConnectionAction(requestId) { repository.cancelRequest(requestId) }
            return
        }
        state = state.copy(driverOutgoingRequests = state.driverOutgoingRequests.filterNot { it.id == requestId })
    }

    fun cancelTrustedOutgoingRequest(requestId: String) {
        if (state.trustedOutgoingRequests.none { it.id == requestId }) return
        trustedContactRepository?.let { repository ->
            launchConnectionAction(requestId) { repository.cancelRequest(requestId) }
            return
        }
        state = state.copy(trustedOutgoingRequests = state.trustedOutgoingRequests.filterNot { it.id == requestId })
    }

    fun revokeDriverContact(id: String) {
        trustedContactRepository?.let { repository ->
            launchConnectionAction(id) { repository.revokeConnection(id) }
            return
        }
        state = state.copy(approvedContacts = state.approvedContacts.filterNot { it.id == id })
    }

    fun disconnectDriver(id: String) {
        trustedContactRepository?.let { repository ->
            launchConnectionAction(id) { repository.revokeConnection(id) }
            return
        }
        state = state.copy(connectedDrivers = state.connectedDrivers.filterNot { it.connectionId == id })
    }

    private fun launchConnectionAction(id: String, action: suspend () -> Unit) {
        state = state.copy(connectionActionInProgressIds = state.connectionActionInProgressIds + id)
        viewModelScope.launch {
            runCatching { action() }
                .onFailure { state = state.copy(cloudConnectionErrorMessage = CLOUD_CONNECTION_ERROR) }
            state = state.copy(connectionActionInProgressIds = state.connectionActionInProgressIds - id)
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
        sharedViewStateJobs.values.forEach(Job::cancel)
        sharedViewStateJobs.clear()
        boundDriverUserId = null
        boundProfileUserId = null
        boundConnectionUserId = null
        accountEmail = ""
        state = if (alertRepository == null) seedState() else PostAuthUiState()
    }

    companion object {
        private const val CLOUD_CONNECTION_ERROR = "Cloud connection data is unavailable. Check your internet connection and try again."
        private const val CODE_NOT_FOUND_MESSAGE = "No active DriveAlert account was found for this code"
        private const val LOOKUP_ERROR_MESSAGE = "The connection code could not be checked. Connect to the internet and try again."

        fun seedState(today: LocalDate = LocalDate.now()): PostAuthUiState {
            fun at(daysAgo: Long, hour: Int, minute: Int) = LocalDateTime.of(today.minusDays(daysAgo), LocalTime.of(hour, minute))
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
                approvedContacts = listOf(Contact("mara", "Mara Santos", "mara.santos@gmail.com", at(21, 10, 0))),
                driverOutgoingRequests = listOf(ConnectionRequest("driver-out", "Lea Torres", "lea.torres@gmail.com", RequestDirection.OUTGOING, at(1, 16, 20))),
                connectedDrivers = listOf(
                    ConnectedDriver("adrian", "Adrian Cruz", "adrian.cruz@gmail.com", at(40, 9, 0), adrianEvents),
                    ConnectedDriver("bianca", "Bianca Ramos", "bianca.ramos@gmail.com", at(18, 14, 0), biancaEvents),
                ),
                trustedIncomingRequests = listOf(ConnectionRequest("trusted-in-1", "Nico Valdez", "nico.valdez@gmail.com", RequestDirection.INCOMING, at(0, 11, 0))),
                isProfileLoading = false,
                driverConnectionsLoading = false,
                trustedConnectionsLoading = false,
            )
        }
    }

    private enum class RequesterSide { DRIVER, TRUSTED_CONTACT }
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

private fun unreadCount(records: List<Stage3SyncRecord>, lastViewedAt: LocalDateTime?): Int {
    val viewedMillis = lastViewedAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
    return records.count { record ->
        val receivedMillis = record.receivedAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        receivedMillis == null || receivedMillis > viewedMillis
    }
}

internal fun PostAuthUiState.withDriverConnections(
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
    val outgoing = connections.filter {
        it.status == ConnectionStatus.PENDING && it.requestedByUserId == it.driverUserId
    }.map { connection ->
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
        driverIncomingRequests = connections.filter {
            it.status == ConnectionStatus.PENDING && it.requestedByUserId == it.trustedContactUserId
        }.map { connection ->
            ConnectionRequest(
                connection.connectionId, connection.trustedContactName, connection.trustedContactEmail,
                RequestDirection.INCOMING, connection.requestedAtEpochMillis.toLocalDateTime(),
            )
        },
        driverOutgoingRequests = outgoing,
    )
}

internal fun PostAuthUiState.withTrustedConnections(
    connections: List<TrustedContactConnection>,
): PostAuthUiState {
    val drivers = connections.filter { it.status == ConnectionStatus.APPROVED }.map { connection ->
        val previous = connectedDrivers.firstOrNull { it.id == connection.driverUserId }
        ConnectedDriver(
            id = connection.driverUserId,
            name = connection.driverName,
            email = connection.driverEmail,
            connectedAt = connection.approvedAtEpochMillis.toLocalDateTimeOrNow(),
            sharedRecords = previous?.sharedRecords.orEmpty(),
            connectionId = connection.connectionId,
            sharedRecordsLoading = previous?.sharedRecordsLoading ?: true,
            sharedRecordsErrorMessage = previous?.sharedRecordsErrorMessage,
            lastViewedAt = previous?.lastViewedAt,
            unreadRecordCount = previous?.unreadRecordCount ?: 0,
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
                connection.connectionId, connection.driverName, connection.driverEmail,
                RequestDirection.OUTGOING, connection.requestedAtEpochMillis.toLocalDateTime(),
            )
        },
    )
}

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

private fun Long?.toLocalDateTimeOrNow(): LocalDateTime = this?.toLocalDateTime() ?: LocalDateTime.now()
