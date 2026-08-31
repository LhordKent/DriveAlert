package com.lhordkent.drivealert.postauth

import java.time.LocalDateTime

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

enum class DeviceConnectionState(val label: String) {
    CONNECTED("Connected"),
    RECONNECTING("Reconnecting"),
    DISCONNECTED("Disconnected"),
}

enum class CalibrationState(val label: String) {
    NOT_STARTED("Not calibrated"),
    IN_PROGRESS("Calibration in progress"),
    READY("Calibrated"),
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
)

enum class WarningSound(val label: String) {
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
    val setupAndDeviceReminders: Boolean = true,
    val driverRequestUpdates: Boolean = true,
    val trustedRequestUpdates: Boolean = true,
    val sharedStage3Records: Boolean = true,
)

data class PostAuthUiState(
    val activeView: UserView = UserView.DRIVER,
    val driverSetupComplete: Boolean = false,
    val monitoringScenario: MonitoringScenario = MonitoringScenario.NORMAL,
    val activeWarningStage: WarningStage? = null,
    val deviceConnection: DeviceConnectionState = DeviceConnectionState.CONNECTED,
    val alignmentReady: Boolean = true,
    val calibrationState: CalibrationState = CalibrationState.READY,
    val profileDisplayName: String = "",
    val alertFilter: WarningStage? = null,
    val driverAlerts: List<AlertEvent> = emptyList(),
    val driverSyncRecords: List<Stage3SyncRecord> = emptyList(),
    val approvedContacts: List<Contact> = emptyList(),
    val driverIncomingRequests: List<ConnectionRequest> = emptyList(),
    val driverOutgoingRequests: List<ConnectionRequest> = emptyList(),
    val connectedDrivers: List<ConnectedDriver> = emptyList(),
    val trustedIncomingRequests: List<ConnectionRequest> = emptyList(),
    val trustedOutgoingRequests: List<ConnectionRequest> = emptyList(),
    val warningSound: WarningSound = WarningSound.DIGITAL_BEEP,
    val preferredVolume: PreferredVolume = PreferredVolume.MEDIUM,
    val notifications: NotificationPreferences = NotificationPreferences(),
)
