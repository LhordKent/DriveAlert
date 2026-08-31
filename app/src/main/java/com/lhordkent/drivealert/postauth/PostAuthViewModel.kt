package com.lhordkent.drivealert.postauth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class PostAuthViewModel : ViewModel() {
    private var accountEmail: String = ""

    var state by mutableStateOf(seedState())
        private set

    fun setAccountEmail(email: String) {
        accountEmail = email.trim().lowercase()
    }

    fun setProfileDisplayName(displayName: String) {
        if (state.profileDisplayName.isBlank()) {
            state = state.copy(profileDisplayName = displayName.trim())
        }
    }

    fun updateProfileDisplayName(displayName: String) {
        state = state.copy(profileDisplayName = displayName.trim())
    }

    fun chooseView(view: UserView) {
        state = state.copy(activeView = view)
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
        state = when (view) {
            UserView.DRIVER -> state.copy(driverIncomingRequests = state.driverIncomingRequests.filterNot { it.id == requestId })
            UserView.TRUSTED_CONTACT -> state.copy(trustedIncomingRequests = state.trustedIncomingRequests.filterNot { it.id == requestId })
        }
    }

    fun cancelRequest(view: UserView, requestId: String) {
        state = when (view) {
            UserView.DRIVER -> state.copy(driverOutgoingRequests = state.driverOutgoingRequests.filterNot { it.id == requestId })
            UserView.TRUSTED_CONTACT -> state.copy(trustedOutgoingRequests = state.trustedOutgoingRequests.filterNot { it.id == requestId })
        }
    }

    fun removeConnection(view: UserView, id: String) {
        state = when (view) {
            UserView.DRIVER -> state.copy(approvedContacts = state.approvedContacts.filterNot { it.id == id })
            UserView.TRUSTED_CONTACT -> state.copy(connectedDrivers = state.connectedDrivers.filterNot { it.id == id })
        }
    }

    fun selectWarningSound(sound: WarningSound) {
        state = state.copy(warningSound = sound)
    }

    fun selectPreferredVolume(volume: PreferredVolume) {
        state = state.copy(preferredVolume = volume)
    }

    fun updateNotifications(preferences: NotificationPreferences) {
        state = state.copy(notifications = preferences)
    }

    fun resetDemoData() {
        val retainedView = state.activeView
        val retainedSetup = state.driverSetupComplete
        state = seedState().copy(activeView = retainedView, driverSetupComplete = retainedSetup)
    }

    fun clearForSignOut() {
        accountEmail = ""
        state = seedState()
    }

    companion object {
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
