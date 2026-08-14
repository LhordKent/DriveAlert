package com.example.drivealert.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

object Route {
    const val Starter = "starter"
    const val SignIn = "sign-in"
    const val SignUp = "sign-up"
    const val ForgotPassword = "forgot-password"
    const val Role = "role"
    const val PowerOn = "power-on"
    const val Connect = "connect"
    const val DeviceCheck = "device-check"
    const val CameraAlignment = "camera-alignment"
    const val Calibration = "calibration"
    const val DriverHome = "driver-home"
    const val Monitoring = "monitoring"
    const val Alerts = "alerts"
    const val AlertDetail = "alert-detail"
    const val Insights = "insights"
    const val Contacts = "contacts"
    const val AddContact = "add-contact"
    const val DeviceManagement = "device-management"
    const val DriverAccess = "driver-access"
    const val AddDriver = "add-driver"
    const val Subscription = "subscription"
    const val DriverSettings = "driver-settings"
    const val Retention = "retention"
    const val WarningSound = "warning-sound"
    const val Notifications = "notifications"
    const val About = "about"
    const val Profile = "profile"
    const val ConnectedDrivers = "connected-drivers"
    const val PendingRequests = "pending-requests"
    const val EscalatedRecords = "escalated-records"
    const val EscalatedDetail = "escalated-detail"
    const val ContactSettings = "contact-settings"
}

enum class AccountRole { Driver, TrustedContact }
enum class AlertSeverity { Normal, Initial, Repeated, Escalated }
enum class SyncStatus { NotEligible, Pending, Synchronized, Failed }
enum class DetectedSign(val label: String) {
    EyeClosure("Prolonged Eye Closure"),
    Yawning("Yawning"),
    HeadNodding("Head Nodding")
}
enum class ConnectionStage { Idle, Searching, Found, Configuring, Connecting, Failed, Connected }
enum class AlertFilter { All, Initial, Repeated, Escalated }

data class AlertRecord(
    val id: String,
    val date: String,
    val time: String,
    val session: String,
    val signs: List<DetectedSign>,
    val severity: AlertSeverity,
    val recurrence: String,
    val syncStatus: SyncStatus,
    val syncTime: String? = null,
)

data class DriverProfile(
    val name: String,
    val email: String,
    val roleLabel: String,
    val escalatedAlerts: Int,
    val latestAlert: String,
    val latestSync: String,
)

data class ContactRequest(val name: String, val email: String)

object MockData {
    val alerts = listOf(
        AlertRecord(
            "a1", "August 14, 2026", "10:42 AM", "Morning Drive",
            listOf(DetectedSign.Yawning), AlertSeverity.Initial,
            "Alert 1 within current window", SyncStatus.NotEligible
        ),
        AlertRecord(
            "a2", "August 14, 2026", "10:44 AM", "Morning Drive",
            listOf(DetectedSign.EyeClosure, DetectedSign.Yawning), AlertSeverity.Repeated,
            "Alert 2 within 5 minutes", SyncStatus.Pending
        ),
        AlertRecord(
            "a3", "August 14, 2026", "10:46 AM", "Morning Drive",
            listOf(DetectedSign.Yawning, DetectedSign.EyeClosure, DetectedSign.HeadNodding),
            AlertSeverity.Escalated, "3 confirmed alerts within 5 minutes",
            SyncStatus.Synchronized, "Today, 10:47 AM"
        ),
        AlertRecord(
            "a4", "August 13, 2026", "6:18 PM", "Evening Drive",
            listOf(DetectedSign.HeadNodding), AlertSeverity.Initial,
            "Alert 1 within current window", SyncStatus.NotEligible
        ),
    )

    val drivers = listOf(
        DriverProfile("Lhord", "lhord@example.com", "Primary Driver", 2, "Today, 10:46 AM", "Today, 10:47 AM"),
        DriverProfile("Gabriel", "gabriel@example.com", "Additional Driver", 1, "Yesterday, 8:20 PM", "Yesterday, 8:21 PM"),
        DriverProfile("Princess", "princess@example.com", "Additional Driver", 0, "No escalated alerts", "August 12, 4:05 PM"),
    )
}

class DriveAlertAppState : ViewModel() {
    var accountRole by mutableStateOf<AccountRole?>(null)
    var connectionStage by mutableStateOf(ConnectionStage.Idle)
    var deviceConnected by mutableStateOf(true)
    var monitoring by mutableStateOf(false)
    var calibrationProgress by mutableIntStateOf(0)
    var selectedAlertId by mutableStateOf("a3")
    var alertFilter by mutableStateOf(AlertFilter.All)
    var retention by mutableStateOf("30 Days")
    var warningSound by mutableStateOf("Digital Beep")
    var volume by mutableStateOf("Medium")
    var soundPreviewing by mutableStateOf<String?>(null)
    var contactRequestSent by mutableStateOf(false)
    var driverInviteSent by mutableStateOf(false)
    var notificationsEnabled by mutableStateOf(true)
    var escalationNotifications by mutableStateOf(true)
    var profileEditing by mutableStateOf(false)
    var profileSaved by mutableStateOf(false)
    var showForgetDialog by mutableStateOf(false)
    var showStopDialog by mutableStateOf(false)
    var faceFeedbackIndex by mutableIntStateOf(0)
    val pendingRequests = mutableStateListOf(
        ContactRequest("Nico Valdez", "nico.valdez@example.com"),
        ContactRequest("Elena Dizon", "elena.dizon@example.com"),
    )

    val selectedAlert: AlertRecord
        get() = MockData.alerts.firstOrNull { it.id == selectedAlertId } ?: MockData.alerts.first()

    val filteredAlerts: List<AlertRecord>
        get() = MockData.alerts.filter {
            alertFilter == AlertFilter.All || it.severity.name == alertFilter.name
        }

    fun advanceConnection() {
        connectionStage = when (connectionStage) {
            ConnectionStage.Idle -> ConnectionStage.Searching
            ConnectionStage.Searching -> ConnectionStage.Found
            ConnectionStage.Found -> ConnectionStage.Configuring
            ConnectionStage.Configuring -> ConnectionStage.Connecting
            ConnectionStage.Connecting -> ConnectionStage.Failed
            ConnectionStage.Failed -> ConnectionStage.Searching
            ConnectionStage.Connected -> ConnectionStage.Connected
        }
    }

    fun completeConnection() {
        connectionStage = ConnectionStage.Connected
        deviceConnected = true
    }

    fun advanceCalibration() {
        calibrationProgress = when (calibrationProgress) { 0 -> 35; 35 -> 70; else -> 100 }
    }

    fun resetDevice() {
        deviceConnected = false
        connectionStage = ConnectionStage.Idle
        calibrationProgress = 0
        showForgetDialog = false
    }
}
