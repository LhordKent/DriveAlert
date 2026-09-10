package com.lhordkent.drivealert.postauth

import java.time.LocalDateTime
import com.lhordkent.drivealert.data.profile.UserRole

enum class UserView { DRIVER, TRUSTED_CONTACT }

enum class WarningStage(val label: String, val explanation: String) {
    STAGE_1("Stage 1", "Initial Warning"),
    STAGE_2("Stage 2", "Stronger Warning"),
    STAGE_3("Stage 3", "Maximum Warning Stage"),
}

enum class VisibleSign(val label: String) {
    PROLONGED_EYE_CLOSURE("Prolonged Eye Closure"),
    YAWNING("Yawning"),
    HEAD_NODDING("Head Nodding"),
}

enum class SharingState(val label: String) {
    NOT_ELIGIBLE("Not eligible"),
    NO_CONTACT("No approved contact"),
    PENDING("Pending"),
    SHARED("Shared"),
    FAILED("Failed"),
}

enum class SyncRecordKind(val label: String) {
    STAGE_3_TRANSITION("Stage 3 started"),
    STAGE_3_PERSISTENCE("Stage 3 continued"),
}

enum class MonitoringScenario(val label: String) {
    NORMAL("Normal monitoring"),
    EYE_UNAVAILABLE("Eye unavailable"),
    YAWNING_UNAVAILABLE("Yawning unavailable"),
    EYE_AND_YAWNING_UNAVAILABLE("Eye + yawning unavailable"),
    FACE_TRACKING_UNAVAILABLE("Face tracking unavailable"),
}

data class MonitoringPresentation(
    val headline: String,
    val guidance: String,
    val eyeAvailable: Boolean,
    val yawningAvailable: Boolean,
    val headAvailable: Boolean,
)

fun MonitoringScenario.presentation(): MonitoringPresentation = when (this) {
    MonitoringScenario.NORMAL -> MonitoringPresentation(
        headline = "Monitoring active",
        guidance = "All selected visible signs are available.",
        eyeAvailable = true,
        yawningAvailable = true,
        headAvailable = true,
    )
    MonitoringScenario.EYE_UNAVAILABLE -> MonitoringPresentation(
        headline = "Monitoring limited",
        guidance = "Eye monitoring is unavailable. Reposition the camera so both eyes are visible.",
        eyeAvailable = false,
        yawningAvailable = true,
        headAvailable = true,
    )
    MonitoringScenario.YAWNING_UNAVAILABLE -> MonitoringPresentation(
        headline = "Monitoring limited",
        guidance = "Yawning monitoring is unavailable. Restore a clear view of the mouth.",
        eyeAvailable = true,
        yawningAvailable = false,
        headAvailable = true,
    )
    MonitoringScenario.EYE_AND_YAWNING_UNAVAILABLE -> MonitoringPresentation(
        headline = "Monitoring paused",
        guidance = "Insufficient facial information is available. Restore a clear view of the eyes and mouth.",
        eyeAvailable = false,
        yawningAvailable = false,
        headAvailable = true,
    )
    MonitoringScenario.FACE_TRACKING_UNAVAILABLE -> MonitoringPresentation(
        headline = "Monitoring paused",
        guidance = "Face tracking is unavailable. Reposition the camera and place your face inside the alignment area.",
        eyeAvailable = false,
        yawningAvailable = false,
        headAvailable = false,
    )
}

data class AlertEvent(
    val id: String,
    val occurredAt: LocalDateTime,
    val signs: Set<VisibleSign>,
    val stage: WarningStage,
    val sessionId: String? = null,
    val alarmTriggered: Boolean? = null,
    val alarmTriggeredAt: LocalDateTime? = null,
)

data class MonitoringSessionSummary(
    val id: String,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val status: String,
    val highestStage: WarningStage?,
)

data class Stage3SyncRecord(
    val id: String,
    val occurredAt: LocalDateTime,
    val kind: SyncRecordKind,
    val signs: Set<VisibleSign>,
    val sharingState: SharingState,
    val sourceDriverId: String? = null,
    val sourceDriverName: String? = null,
    val receivedAt: LocalDateTime? = null,
    val sessionId: String? = null,
)

enum class AlertTrend(val label: String) {
    HIGHER("Higher than previous 7 days"),
    LOWER("Lower than previous 7 days"),
    UNCHANGED("Same as previous 7 days"),
}

data class AlertHistoryInsights(
    val alertsToday: Int = 0,
    val weeklyAlerts: Int = 0,
    val mostFrequentSign: VisibleSign? = null,
    val highestStage: WarningStage? = null,
    val trend: AlertTrend = AlertTrend.UNCHANGED,
)

data class Contact(
    val id: String,
    val name: String,
    val email: String,
    val approvedAt: LocalDateTime,
)

fun Stage3SyncRecord.isEligibleFor(contact: Contact): Boolean =
    !occurredAt.isBefore(contact.approvedAt)

enum class RequestDirection { INCOMING, OUTGOING }

data class ConnectionRequest(
    val id: String,
    val name: String,
    val email: String,
    val direction: RequestDirection,
    val requestedAt: LocalDateTime,
)

data class ConnectedDriver(
    val id: String,
    val name: String,
    val email: String,
    val connectedAt: LocalDateTime,
    val sharedRecords: List<Stage3SyncRecord>,
    val connectionId: String = id,
)

enum class WarningSound(val label: String) {
    ROOSTER_CALL("Rooster Call"),
    ALARM_CLOCK("Alarm Clock"),
    DIGITAL_BEEP("Digital Beep"),
    SIREN_PULSE("Siren Pulse"),
    BELL_CHIME("Bell Chime"),
}

enum class PreferredVolume(val label: String) {
    MINIMUM("Minimum"),
    MEDIUM("Medium"),
    HIGH("High"),
}

data class NotificationPreferences(
    val warningAlerts: Boolean = true,
    val setupAndDeviceReminders: Boolean = true,
    val driverRequestUpdates: Boolean = true,
    val trustedRequestUpdates: Boolean = true,
    val sharedStage3Records: Boolean = true,
)

data class ConnectionInviteUiState(
    val isLookingUp: Boolean = false,
    val targetUserId: String? = null,
    val targetDisplayName: String? = null,
    val normalizedCode: String? = null,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val requestSent: Boolean = false,
)

data class PostAuthUiState(
    val activeView: UserView = UserView.DRIVER,
    val driverSetupComplete: Boolean = false,
    val monitoringScenario: MonitoringScenario = MonitoringScenario.NORMAL,
    val activeWarningStage: WarningStage? = null,
    val profileDisplayName: String = "",
    val connectionCode: String = "",
    val profileErrorMessage: String? = null,
    val userRole: UserRole? = null,
    val alertFilter: WarningStage? = null,
    val driverAlerts: List<AlertEvent> = emptyList(),
    val alertInsights: AlertHistoryInsights = AlertHistoryInsights(),
    val monitoringSessions: List<MonitoringSessionSummary> = emptyList(),
    val isLocalDataLoading: Boolean = false,
    val localDataErrorMessage: String? = null,
    val driverSyncRecords: List<Stage3SyncRecord> = emptyList(),
    val approvedContacts: List<Contact> = emptyList(),
    val driverIncomingRequests: List<ConnectionRequest> = emptyList(),
    val driverOutgoingRequests: List<ConnectionRequest> = emptyList(),
    val connectedDrivers: List<ConnectedDriver> = emptyList(),
    val trustedIncomingRequests: List<ConnectionRequest> = emptyList(),
    val trustedOutgoingRequests: List<ConnectionRequest> = emptyList(),
    val cloudConnectionErrorMessage: String? = null,
    val connectionInvite: ConnectionInviteUiState = ConnectionInviteUiState(),
    val warningSound: WarningSound = WarningSound.DIGITAL_BEEP,
    val preferredVolume: PreferredVolume = PreferredVolume.MEDIUM,
    val notifications: NotificationPreferences = NotificationPreferences(),
)
