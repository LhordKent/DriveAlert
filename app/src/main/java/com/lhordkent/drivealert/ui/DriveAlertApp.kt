package com.lhordkent.drivealert.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.lhordkent.drivealert.R
import com.lhordkent.drivealert.DriveAlertApplication
import com.lhordkent.drivealert.auth.AuthCallbacks
import com.lhordkent.drivealert.auth.AuthOperationState
import com.lhordkent.drivealert.auth.AuthRoutes
import com.lhordkent.drivealert.auth.AuthSessionState
import com.lhordkent.drivealert.auth.AuthViewModel
import com.lhordkent.drivealert.auth.AuthViewModelFactory
import com.lhordkent.drivealert.auth.AuthenticationEntry
import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.DriverVisionViewModel
import com.lhordkent.drivealert.detection.model.MonitoringDetectionResult
import com.lhordkent.drivealert.detection.model.TemporalState
import com.lhordkent.drivealert.postauth.MonitoringScenario
import com.lhordkent.drivealert.provisioning.ProvisioningStage
import com.lhordkent.drivealert.notification.TrustedContactDeviceRegistrationWorker
import com.lhordkent.drivealert.provisioning.WifiProvisioningViewModel
import com.lhordkent.drivealert.postauth.PostAuthUiState
import com.lhordkent.drivealert.postauth.PostAuthViewModel
import com.lhordkent.drivealert.postauth.PostAuthViewModelFactory
import com.lhordkent.drivealert.postauth.UserView
import com.lhordkent.drivealert.data.profile.UserRole
import com.lhordkent.drivealert.ui.auth.CreateAccountScreen
import com.lhordkent.drivealert.ui.auth.ForgotPasswordScreen
import com.lhordkent.drivealert.ui.auth.SignInScreen
import com.lhordkent.drivealert.ui.auth.StarterScreen
import com.lhordkent.drivealert.ui.postauth.AboutDriveAlertScreen
import com.lhordkent.drivealert.ui.postauth.AccountScreen
import com.lhordkent.drivealert.ui.postauth.AlertDetailScreen
import com.lhordkent.drivealert.ui.postauth.AlertHistoryScreen
import com.lhordkent.drivealert.ui.postauth.AppNavItem
import com.lhordkent.drivealert.ui.postauth.ChooseViewScreen
import com.lhordkent.drivealert.ui.postauth.CalibrationScreen
import com.lhordkent.drivealert.ui.postauth.CameraAlignmentScreen
import com.lhordkent.drivealert.ui.postauth.DeviceConnectionScreen
import com.lhordkent.drivealert.ui.postauth.DriverContactsScreen
import com.lhordkent.drivealert.ui.postauth.DriverHomeScreen
import com.lhordkent.drivealert.ui.postauth.InviteConnectionScreen
import com.lhordkent.drivealert.ui.postauth.MonitoringPreviewScreen
import com.lhordkent.drivealert.ui.postauth.VisibilityAcknowledgementDialog
import com.lhordkent.drivealert.monitoring.DriverAccessoryMode
import com.lhordkent.drivealert.ui.postauth.NotificationSettingsScreen
import com.lhordkent.drivealert.ui.postauth.PostAuthScaffold
import com.lhordkent.drivealert.ui.postauth.RequestsScreen
import com.lhordkent.drivealert.ui.postauth.RootContent
import com.lhordkent.drivealert.ui.postauth.SettingsScreen
import com.lhordkent.drivealert.ui.postauth.SetupDeviceScreen
import com.lhordkent.drivealert.ui.postauth.Stage3SyncRecordDetailScreen
import com.lhordkent.drivealert.ui.postauth.SharedRecordsScreen
import com.lhordkent.drivealert.ui.postauth.TrustedDriversScreen
import com.lhordkent.drivealert.ui.postauth.WarningSoundScreen
import com.lhordkent.drivealert.ui.theme.Ink

object PostAuthRoutes {
    const val ROLE_CHOICE = "post-auth/choose-view"
    const val DRIVER_SETUP = "driver/setup"
    const val DRIVER_DEVICE_CONNECTION = "driver/setup/connection"
    const val DRIVER_ALIGNMENT = "driver/setup/alignment"
    const val DRIVER_CALIBRATION = "driver/setup/calibration"
    const val DRIVER_HOME = "driver/home"
    const val DRIVER_MONITORING = "driver/monitoring"
    const val DRIVER_ALERTS = "driver/alerts"
    const val DRIVER_ALERT_DETAIL = "driver/alerts/{eventId}"
    const val DRIVER_CONTACTS = "driver/contacts"
    const val DRIVER_INVITE = "driver/contacts/invite"
    const val DRIVER_SETTINGS = "driver/settings"
    const val TRUSTED_DRIVERS = "trusted/drivers"
    const val TRUSTED_SHARED = "trusted/drivers/{driverId}"
    const val TRUSTED_ALERT_DETAIL = "trusted/drivers/{driverId}/alerts/{eventId}"
    const val TRUSTED_REQUESTS = "trusted/requests"
    const val TRUSTED_INVITE = "trusted/requests/invite"
    const val TRUSTED_SETTINGS = "trusted/settings"
    const val ACCOUNT = "post-auth/account"
    const val NOTIFICATIONS = "post-auth/notifications"
    const val WARNING_SOUND = "post-auth/warning-sound"
    const val ABOUT = "post-auth/about"

    fun driverAlert(eventId: String) = "driver/alerts/$eventId"
    fun trustedDriver(driverId: String) = "trusted/drivers/$driverId"
    fun trustedAlert(driverId: String, eventId: String) = "trusted/drivers/$driverId/alerts/$eventId"
}

internal fun restoredRoleDestination(role: UserRole?, driverSetupComplete: Boolean): String = when (role) {
    UserRole.TRUSTED_CONTACT -> PostAuthRoutes.TRUSTED_DRIVERS
    UserRole.DRIVER, UserRole.BOTH -> if (driverSetupComplete) PostAuthRoutes.DRIVER_HOME else PostAuthRoutes.DRIVER_SETUP
    null -> PostAuthRoutes.ROLE_CHOICE
}

private val driverNavItems = listOf(
    AppNavItem(PostAuthRoutes.DRIVER_HOME, "Home", Icons.Rounded.Home),
    AppNavItem(PostAuthRoutes.DRIVER_ALERTS, "Alerts", Icons.Rounded.History),
    AppNavItem(PostAuthRoutes.DRIVER_CONTACTS, "Contacts", Icons.Rounded.People),
    AppNavItem(PostAuthRoutes.DRIVER_SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun DriveAlertApp() {
    val application = LocalContext.current.applicationContext as DriveAlertApplication
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(
            application.container.userProfileRepository,
            application.container.trustedContactDeviceRegistrar,
        ),
    )
    val postAuthViewModel: PostAuthViewModel = viewModel(
        factory = PostAuthViewModelFactory(
            application.container.alertRepository,
            application.container.monitoringSessionRepository,
            application.container.userProfileRepository,
            application.container.driverPreferenceRepository,
            application.container.trustedContactRepository,
            application.container.stageSyncRepository,
            application.container.sharedStage3Repository,
        ),
    )
    val provisioningViewModel: WifiProvisioningViewModel = viewModel()
    val visionViewModel: DriverVisionViewModel = viewModel()
    val visionState by visionViewModel.state.collectAsState()
    val provisioningState by provisioningViewModel.state.collectAsState()
    LaunchedEffect(authViewModel.sessionState.user?.uid) {
        if (authViewModel.sessionState.user != null) {
            TrustedContactDeviceRegistrationWorker.schedule(application)
        }
        authViewModel.sessionState.user?.uid?.let(postAuthViewModel::bindLocalData)
        authViewModel.sessionState.user?.let { user ->
            postAuthViewModel.bindCloudProfile(user.uid, user.displayName, user.email)
        }
        authViewModel.sessionState.user?.uid?.let(postAuthViewModel::bindPreferences)
        authViewModel.sessionState.user?.uid?.let(postAuthViewModel::bindConnections)
        authViewModel.sessionState.user?.uid?.let(postAuthViewModel::bindStageSyncRecords)
        provisioningViewModel.bindDriver(authViewModel.sessionState.user?.uid)
        visionViewModel.bindDriver(authViewModel.sessionState.user?.uid)
    }
    LaunchedEffect(authViewModel.sessionState.user?.uid, postAuthViewModel.state.userRole) {
        if (authViewModel.sessionState.user != null &&
            postAuthViewModel.state.userRole in setOf(UserRole.TRUSTED_CONTACT, UserRole.BOTH)
        ) requestNotificationPermission()
    }
    LaunchedEffect(provisioningState.stage) {
        if (provisioningState.stage == ProvisioningStage.PROVISIONED) visionViewModel.startVision()
    }
    DriveAlertApp(
        callbacks = authViewModel.callbacks,
        operationState = authViewModel.state,
        sessionState = authViewModel.sessionState,
        postAuthState = postAuthViewModel.state,
        visionState = visionState,
        previewFrames = visionViewModel.previewFrame,
        provisioningViewModel = provisioningViewModel,
        onStartVision = visionViewModel::startVision,
        onConfirmAlignment = visionViewModel::confirmAlignment,
        onStartCalibration = visionViewModel::startCalibration,
        onBeginCalibrationPhase = visionViewModel::beginCalibrationPhase,
        onRepeatCalibrationPhase = visionViewModel::repeatCalibrationPhase,
        onCancelCalibration = visionViewModel::cancelCalibration,
        onStartMonitoring = { sound, volume, notificationsEnabled, accessoryMode ->
            if (notificationsEnabled) requestNotificationPermission()
            visionViewModel.startMonitoring(sound, volume, notificationsEnabled, accessoryMode)
        },
        onContinueDegradedMonitoring = visionViewModel::continueWithDegradedMonitoring,
        onDismissVisibilityAcknowledgement = visionViewModel::dismissVisibilityAcknowledgement,
        onTestWarning = visionViewModel::testWarningOutput,
        onStopMonitoring = { visionViewModel.stopMonitoring() },
        onDisconnectDevice = {
            visionViewModel.disconnectCurrentSession(provisioningViewModel::resetAfterManualDisconnect)
        },
        onPrepareSignOut = { onComplete ->
            visionViewModel.prepareForAccountChange {
                provisioningViewModel.resetAfterManualDisconnect()
                onComplete()
            }
        },
        onNavigate = authViewModel::clearMessages,
        onSetAccountEmail = postAuthViewModel::setAccountEmail,
        onSetProfileName = postAuthViewModel::setProfileDisplayName,
        onUpdateProfile = postAuthViewModel::updateProfile,
        onChooseView = postAuthViewModel::chooseView,
        onCompleteSetup = postAuthViewModel::completeDriverSetup,
        onFilterChange = postAuthViewModel::selectAlertFilter,
        onLookupConnectionCode = postAuthViewModel::lookupConnectionCode,
        onSendTrustedContactRequest = postAuthViewModel::sendTrustedContactRequest,
        onSendDriverRequest = postAuthViewModel::sendDriverRequest,
        onClearConnectionInvite = postAuthViewModel::clearConnectionInvite,
        onAcceptDriverIncomingRequest = postAuthViewModel::acceptDriverIncomingRequest,
        onAcceptTrustedIncomingRequest = postAuthViewModel::acceptTrustedIncomingRequest,
        onDeclineDriverIncomingRequest = postAuthViewModel::declineDriverIncomingRequest,
        onDeclineTrustedIncomingRequest = postAuthViewModel::declineTrustedIncomingRequest,
        onCancelDriverOutgoingRequest = postAuthViewModel::cancelDriverOutgoingRequest,
        onCancelTrustedOutgoingRequest = postAuthViewModel::cancelTrustedOutgoingRequest,
        onRevokeDriverContact = postAuthViewModel::revokeDriverContact,
        onDisconnectDriver = postAuthViewModel::disconnectDriver,
        onMarkSharedRecordsViewed = postAuthViewModel::markSharedRecordsViewed,
        onWarningSoundChange = postAuthViewModel::selectWarningSound,
        onVolumeChange = postAuthViewModel::selectPreferredVolume,
        onNotificationsChange = postAuthViewModel::updateNotifications,
        onRequestNotificationPermission = requestNotificationPermission,
        onClearPostAuth = postAuthViewModel::clearForSignOut,
    )
}

@Composable
fun DriveAlertApp(
    callbacks: AuthCallbacks,
    operationState: AuthOperationState = AuthOperationState(),
    sessionState: AuthSessionState = AuthSessionState(isResolving = false),
    postAuthState: PostAuthUiState = PostAuthViewModel.seedState(),
    visionState: DriverVisionUiState = DriverVisionUiState(),
    previewFrames: kotlinx.coroutines.flow.StateFlow<com.lhordkent.drivealert.detection.frame.SharedBitmapFrame?>? = null,
    provisioningViewModel: WifiProvisioningViewModel? = null,
    onStartVision: () -> Unit = {},
    onConfirmAlignment: () -> Unit = {},
    onStartCalibration: () -> Unit = {},
    onBeginCalibrationPhase: () -> Unit = {},
    onRepeatCalibrationPhase: () -> Unit = {},
    onCancelCalibration: () -> Unit = {},
    onStartMonitoring: (com.lhordkent.drivealert.postauth.WarningSound, com.lhordkent.drivealert.postauth.PreferredVolume, Boolean, DriverAccessoryMode) -> Unit = { _, _, _, _ -> },
    onContinueDegradedMonitoring: () -> Unit = {},
    onDismissVisibilityAcknowledgement: () -> Unit = {},
    onTestWarning: (com.lhordkent.drivealert.postauth.WarningStage, com.lhordkent.drivealert.postauth.WarningSound, com.lhordkent.drivealert.postauth.PreferredVolume) -> Unit = { _, _, _ -> },
    onStopMonitoring: () -> Unit = {},
    onDisconnectDevice: () -> Unit = {},
    onPrepareSignOut: (() -> Unit) -> Unit = { onComplete -> onComplete() },
    onNavigate: () -> Unit = {},
    onSetAccountEmail: (String) -> Unit = {},
    onSetProfileName: (String) -> Unit = {},
    onUpdateProfile: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onChooseView: (UserView) -> Unit = {},
    onCompleteSetup: () -> Unit = {},
    onFilterChange: (com.lhordkent.drivealert.postauth.WarningStage?) -> Unit = {},
    onLookupConnectionCode: (String) -> Unit = {},
    onSendTrustedContactRequest: () -> Unit = {},
    onSendDriverRequest: () -> Unit = {},
    onClearConnectionInvite: () -> Unit = {},
    onAcceptDriverIncomingRequest: (String) -> Unit = {},
    onAcceptTrustedIncomingRequest: (String) -> Unit = {},
    onDeclineDriverIncomingRequest: (String) -> Unit = {},
    onDeclineTrustedIncomingRequest: (String) -> Unit = {},
    onCancelDriverOutgoingRequest: (String) -> Unit = {},
    onCancelTrustedOutgoingRequest: (String) -> Unit = {},
    onRevokeDriverContact: (String) -> Unit = {},
    onDisconnectDriver: (String) -> Unit = {},
    onMarkSharedRecordsViewed: (String) -> Unit = {},
    onWarningSoundChange: (com.lhordkent.drivealert.postauth.WarningSound) -> Unit = {},
    onVolumeChange: (com.lhordkent.drivealert.postauth.PreferredVolume) -> Unit = {},
    onNotificationsChange: (com.lhordkent.drivealert.postauth.NotificationPreferences) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onClearPostAuth: () -> Unit = {},
) {
    if (sessionState.isResolving) {
        SessionLoadingScreen()
        return
    }

    val navController = rememberNavController()
    val user = sessionState.user

    LaunchedEffect(user?.uid, sessionState.entry, postAuthState.userRole, postAuthState.isProfileLoading, postAuthState.driverSetupComplete) {
        if (user == null) {
            navController.navigate(AuthRoutes.STARTER) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            onSetAccountEmail(user.email)
            onSetProfileName(user.displayName)
            val destination = when (sessionState.entry) {
                AuthenticationEntry.ACCOUNT_CREATED -> PostAuthRoutes.ROLE_CHOICE
                AuthenticationEntry.FRESH, AuthenticationEntry.RESTORED, AuthenticationEntry.NONE ->
                    if (postAuthState.isProfileLoading) null else restoredRoleDestination(postAuthState.userRole, postAuthState.driverSetupComplete)
            }
            destination?.let { navController.navigate(it) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            } }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        NavHost(navController = navController, startDestination = AuthRoutes.STARTER) {
            authGraph(navController, callbacks, operationState, onNavigate)

            composable(PostAuthRoutes.ROLE_CHOICE) {
                ChooseViewScreen(
                    onDriver = {
                        onChooseView(UserView.DRIVER)
                        navController.navigate(if (postAuthState.driverSetupComplete) PostAuthRoutes.DRIVER_HOME else PostAuthRoutes.DRIVER_SETUP)
                    },
                    onTrustedContact = {
                        onChooseView(UserView.TRUSTED_CONTACT)
                        navController.navigate(PostAuthRoutes.TRUSTED_DRIVERS)
                    },
                )
            }
            composable(PostAuthRoutes.DRIVER_SETUP) {
                SetupDeviceScreen(
                    onContinue = {
                        onCompleteSetup()
                        navController.navigate(PostAuthRoutes.DRIVER_HOME) {
                            popUpTo(PostAuthRoutes.DRIVER_SETUP) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onDeviceConnection = { navController.navigate(PostAuthRoutes.DRIVER_DEVICE_CONNECTION) },
                    onCameraAlignment = { navController.navigate(PostAuthRoutes.DRIVER_ALIGNMENT) },
                    onCalibration = { navController.navigate(PostAuthRoutes.DRIVER_CALIBRATION) },
                    onBack = if (postAuthState.driverSetupComplete) ({ navController.popBackStack() }) else null,
                    vision = visionState,
                )
            }
            composable(PostAuthRoutes.DRIVER_DEVICE_CONNECTION) {
                DeviceConnectionScreen(
                    onBack = navController::popBackStack,
                    provisioningViewModel = provisioningViewModel,
                    vision = visionState,
                    onDisconnect = onDisconnectDevice,
                )
            }
            composable(PostAuthRoutes.DRIVER_ALIGNMENT) {
                CameraAlignmentScreen(
                    vision = visionState,
                    previewFrames = previewFrames,
                    onStartVision = onStartVision,
                    onConfirmAlignment = onConfirmAlignment,
                    onBack = navController::popBackStack,
                )
            }
            composable(PostAuthRoutes.DRIVER_CALIBRATION) {
                CalibrationScreen(
                    vision = visionState,
                    previewFrames = previewFrames,
                    onStartVision = onStartVision,
                    onStartCalibration = onStartCalibration,
                    onBeginPhase = onBeginCalibrationPhase,
                    onRepeatPhase = onRepeatCalibrationPhase,
                    onCancel = onCancelCalibration,
                    onBack = navController::popBackStack,
                )
            }
            composable(PostAuthRoutes.DRIVER_HOME) {
                DriverRoot(navController, PostAuthRoutes.DRIVER_HOME, "Driver Home") {
                    DriverHomeScreen(
                        state = postAuthState,
                        vision = visionState,
                        onStartMonitoring = { accessoryMode ->
                            when {
                                visionState.streamState != com.lhordkent.drivealert.detection.frame.StreamConnectionState.CONNECTED ->
                                    navController.navigate(PostAuthRoutes.DRIVER_DEVICE_CONNECTION)
                                visionState.activeCalibration == null -> navController.navigate(PostAuthRoutes.DRIVER_CALIBRATION)
                                else -> onStartMonitoring(
                                    postAuthState.warningSound,
                                    postAuthState.preferredVolume,
                                    postAuthState.notifications.warningAlerts,
                                    accessoryMode,
                                )
                            }
                        },
                        onStopMonitoring = onStopMonitoring,
                        onOpenMonitoringActivity = { navController.navigate(PostAuthRoutes.DRIVER_MONITORING) },
                        onSetupDevice = { navController.navigate(PostAuthRoutes.DRIVER_SETUP) },
                        onOpenAlerts = { navigateRoot(navController, PostAuthRoutes.DRIVER_ALERTS, PostAuthRoutes.DRIVER_HOME) },
                    )
                }
            }
            composable(PostAuthRoutes.DRIVER_MONITORING) {
                LaunchedEffect(Unit) { onStartVision() }
                MonitoringPreviewScreen(
                    scenario = visionState.detection.toMonitoringScenario(),
                    monitoring = visionState.monitoring,
                    onExit = navController::popBackStack,
                    detectionResult = visionState.detection,
                    faceAttributes = visionState.faceAttributes,
                    accessoryMode = visionState.accessoryMode,
                )
            }
            composable(PostAuthRoutes.DRIVER_ALERTS) {
                DriverRoot(navController, PostAuthRoutes.DRIVER_ALERTS, "Alert History") {
                    AlertHistoryScreen(
                        postAuthState.driverAlerts,
                        postAuthState.driverSyncRecords,
                        postAuthState.alertFilter,
                        onFilterChange,
                        insights = postAuthState.alertInsights,
                        sessions = postAuthState.monitoringSessions,
                        errorMessage = postAuthState.localDataErrorMessage,
                        isLoading = postAuthState.isLocalDataLoading,
                    ) {
                        navController.navigate(PostAuthRoutes.driverAlert(it))
                    }
                }
            }
            composable(
                route = PostAuthRoutes.DRIVER_ALERT_DETAIL,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType }),
            ) { entry ->
                val eventId = entry.arguments?.getString("eventId")
                val event = postAuthState.driverAlerts.firstOrNull { it.id == eventId }
                if (event != null) AlertDetailScreen(
                    event = event,
                    session = postAuthState.monitoringSessions.firstOrNull { it.id == event.sessionId },
                    syncRecords = postAuthState.driverSyncRecords.filter { it.sessionId != null && it.sessionId == event.sessionId },
                    onBack = navController::popBackStack,
                )
            }
            composable(PostAuthRoutes.DRIVER_CONTACTS) {
                DriverRoot(navController, PostAuthRoutes.DRIVER_CONTACTS, "Trusted Contacts") {
                    DriverContactsScreen(
                        contacts = postAuthState.approvedContacts,
                        incoming = postAuthState.driverIncomingRequests,
                        outgoing = postAuthState.driverOutgoingRequests,
                        onInvite = { navController.navigate(PostAuthRoutes.DRIVER_INVITE) },
                        onAccept = onAcceptDriverIncomingRequest,
                        onDecline = onDeclineDriverIncomingRequest,
                        onCancel = onCancelDriverOutgoingRequest,
                        onRemove = onRevokeDriverContact,
                        isLoading = postAuthState.driverConnectionsLoading,
                        actionInProgressIds = postAuthState.connectionActionInProgressIds,
                        cloudErrorMessage = postAuthState.cloudConnectionErrorMessage,
                    )
                }
            }
            composable(PostAuthRoutes.DRIVER_INVITE) {
                InviteConnectionScreen(
                    targetView = UserView.DRIVER,
                    inviteState = postAuthState.connectionInvite,
                    onLookup = onLookupConnectionCode,
                    onSend = onSendTrustedContactRequest,
                    onClear = onClearConnectionInvite,
                    onBack = navController::popBackStack,
                )
            }
            composable(PostAuthRoutes.DRIVER_SETTINGS) {
                DriverRoot(navController, PostAuthRoutes.DRIVER_SETTINGS, "Driver Settings") {
                    SettingsScreen(
                        view = UserView.DRIVER,
                        onSwitchView = {
                            onChooseView(UserView.TRUSTED_CONTACT)
                            navController.navigate(PostAuthRoutes.TRUSTED_DRIVERS) {
                                popUpTo(PostAuthRoutes.DRIVER_HOME) { inclusive = true }
                            }
                        },
                        onAccount = { navController.navigate(PostAuthRoutes.ACCOUNT) },
                        onSetupDevice = { navController.navigate(PostAuthRoutes.DRIVER_SETUP) },
                        onNotifications = { navController.navigate(PostAuthRoutes.NOTIFICATIONS) },
                        onWarningSound = { navController.navigate(PostAuthRoutes.WARNING_SOUND) },
                        onAbout = { navController.navigate(PostAuthRoutes.ABOUT) },
                        onLogout = {
                            onPrepareSignOut {
                                onClearPostAuth()
                                callbacks.onSignOut()
                            }
                        },
                    )
                }
            }
            composable(PostAuthRoutes.TRUSTED_DRIVERS) {
                TrustedRoot(navController, postAuthState, PostAuthRoutes.TRUSTED_DRIVERS, "Connected Drivers") {
                    TrustedDriversScreen(
                        drivers = postAuthState.connectedDrivers,
                        onDriverSelected = { navController.navigate(PostAuthRoutes.trustedDriver(it)) },
                        onInvite = { navController.navigate(PostAuthRoutes.TRUSTED_INVITE) },
                        onRemove = onDisconnectDriver,
                        isLoading = postAuthState.trustedConnectionsLoading,
                        actionInProgressIds = postAuthState.connectionActionInProgressIds,
                        cloudErrorMessage = postAuthState.cloudConnectionErrorMessage,
                    )
                }
            }
            composable(
                route = PostAuthRoutes.TRUSTED_SHARED,
                arguments = listOf(navArgument("driverId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "drivealert://trusted/drivers/{driverId}" }),
            ) { entry ->
                val driverId = entry.arguments?.getString("driverId")
                val driver = postAuthState.connectedDrivers.firstOrNull { it.id == driverId }
                LaunchedEffect(driverId) { driverId?.let(onMarkSharedRecordsViewed) }
                if (driver != null) SharedRecordsScreen(
                    driver = driver,
                    onEventSelected = { navController.navigate(PostAuthRoutes.trustedAlert(driver.id, it)) },
                    onBack = navController::popBackStack,
                )
            }
            composable(
                route = PostAuthRoutes.TRUSTED_ALERT_DETAIL,
                arguments = listOf(
                    navArgument("driverId") { type = NavType.StringType },
                    navArgument("eventId") { type = NavType.StringType },
                ),
            ) { entry ->
                val driver = postAuthState.connectedDrivers.firstOrNull { it.id == entry.arguments?.getString("driverId") }
                val record = driver?.sharedRecords?.firstOrNull { it.id == entry.arguments?.getString("eventId") }
                if (record != null) Stage3SyncRecordDetailScreen(record, onBack = navController::popBackStack)
            }
            composable(PostAuthRoutes.TRUSTED_REQUESTS) {
                TrustedRoot(navController, postAuthState, PostAuthRoutes.TRUSTED_REQUESTS, "Requests") {
                    RequestsScreen(
                        incoming = postAuthState.trustedIncomingRequests,
                        outgoing = postAuthState.trustedOutgoingRequests,
                        onAccept = onAcceptTrustedIncomingRequest,
                        onDecline = onDeclineTrustedIncomingRequest,
                        onCancel = onCancelTrustedOutgoingRequest,
                        isLoading = postAuthState.trustedConnectionsLoading,
                        actionInProgressIds = postAuthState.connectionActionInProgressIds,
                        cloudErrorMessage = postAuthState.cloudConnectionErrorMessage,
                    )
                }
            }
            composable(PostAuthRoutes.TRUSTED_INVITE) {
                InviteConnectionScreen(
                    targetView = UserView.TRUSTED_CONTACT,
                    inviteState = postAuthState.connectionInvite,
                    onLookup = onLookupConnectionCode,
                    onSend = onSendDriverRequest,
                    onClear = onClearConnectionInvite,
                    onBack = navController::popBackStack,
                )
            }
            composable(PostAuthRoutes.TRUSTED_SETTINGS) {
                TrustedRoot(navController, postAuthState, PostAuthRoutes.TRUSTED_SETTINGS, "Trusted Contact Settings") {
                    SettingsScreen(
                        view = UserView.TRUSTED_CONTACT,
                        onSwitchView = {
                            onChooseView(UserView.DRIVER)
                            navController.navigate(if (postAuthState.driverSetupComplete) PostAuthRoutes.DRIVER_HOME else PostAuthRoutes.DRIVER_SETUP) {
                                popUpTo(PostAuthRoutes.TRUSTED_DRIVERS) { inclusive = true }
                            }
                        },
                        onAccount = { navController.navigate(PostAuthRoutes.ACCOUNT) },
                        onSetupDevice = {},
                        onNotifications = { navController.navigate(PostAuthRoutes.NOTIFICATIONS) },
                        onWarningSound = {},
                        onAbout = { navController.navigate(PostAuthRoutes.ABOUT) },
                        onLogout = {
                            onPrepareSignOut {
                                onClearPostAuth()
                                callbacks.onSignOut()
                            }
                        },
                    )
                }
            }
            composable(PostAuthRoutes.ACCOUNT) {
                AccountScreen(
                    firstName = postAuthState.profileFirstName,
                    middleName = postAuthState.profileMiddleName,
                    lastName = postAuthState.profileLastName,
                    phoneNumber = postAuthState.profilePhoneNumber,
                    displayName = postAuthState.profileDisplayName.ifBlank { user?.displayName.orEmpty() },
                    email = user?.email.orEmpty(),
                    connectionCode = postAuthState.connectionCode,
                    profileErrorMessage = postAuthState.profileErrorMessage,
                    isProfileUpdating = postAuthState.isProfileUpdating,
                    profileUpdateSuccessMessage = postAuthState.profileUpdateSuccessMessage,
                    profileUpdateErrorMessage = postAuthState.profileUpdateErrorMessage,
                    onUpdateProfile = onUpdateProfile,
                    onBack = navController::popBackStack,
                )
            }
            composable(PostAuthRoutes.NOTIFICATIONS) {
                NotificationSettingsScreen(
                    postAuthState.activeView,
                    postAuthState.notifications,
                    onPreferencesChange = onNotificationsChange,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onBack = navController::popBackStack,
                )
            }
            composable(PostAuthRoutes.WARNING_SOUND) {
                WarningSoundScreen(
                    selectedSound = postAuthState.warningSound,
                    selectedVolume = postAuthState.preferredVolume,
                    onSoundSelected = onWarningSoundChange,
                    onVolumeSelected = onVolumeChange,
                    onBack = navController::popBackStack,
                    debugDelivery = visionState.debugWarningDelivery,
                    onTestWarning = { stage -> onTestWarning(stage, postAuthState.warningSound, postAuthState.preferredVolume) },
                    onPreviewSelection = { sound, volume -> onTestWarning(com.lhordkent.drivealert.postauth.WarningStage.STAGE_1, sound, volume) },
                )
            }
            composable(PostAuthRoutes.ABOUT) { AboutDriveAlertScreen(navController::popBackStack) }
        }
    }
    visionState.visibilityAcknowledgementRequired?.let { issue ->
        VisibilityAcknowledgementDialog(
            issue = issue,
            onContinueLimited = onContinueDegradedMonitoring,
            onRestoreVisibility = onDismissVisibilityAcknowledgement,
        )
    }
}

private fun MonitoringDetectionResult?.toMonitoringScenario(): MonitoringScenario = when {
    this == null || !faceDetected -> MonitoringScenario.FACE_TRACKING_UNAVAILABLE
    listOf(eye, yawn, head).count { it.state != TemporalState.UNAVAILABLE } < 2 -> MonitoringScenario.EYE_AND_YAWNING_UNAVAILABLE
    eye.state == TemporalState.UNAVAILABLE -> MonitoringScenario.EYE_UNAVAILABLE
    yawn.state == TemporalState.UNAVAILABLE -> MonitoringScenario.YAWNING_UNAVAILABLE
    else -> MonitoringScenario.NORMAL
}

private fun androidx.navigation.NavGraphBuilder.authGraph(
    navController: NavHostController,
    callbacks: AuthCallbacks,
    operationState: AuthOperationState,
    onNavigate: () -> Unit,
) {
    composable(AuthRoutes.STARTER) {
        StarterScreen(
            state = operationState,
            onSignIn = { onNavigate(); navController.navigate(AuthRoutes.SIGN_IN) { launchSingleTop = true } },
            onCreateAccount = { onNavigate(); navController.navigate(AuthRoutes.CREATE_ACCOUNT) { launchSingleTop = true } },
        )
    }
    composable(AuthRoutes.SIGN_IN) {
        SignInScreen(
            callbacks = callbacks,
            state = operationState,
            onForgotPassword = { onNavigate(); navController.navigate(AuthRoutes.FORGOT_PASSWORD) { launchSingleTop = true } },
            onCreateAccount = { onNavigate(); navController.navigate(AuthRoutes.CREATE_ACCOUNT) { launchSingleTop = true } },
        )
    }
    composable(AuthRoutes.CREATE_ACCOUNT) {
        CreateAccountScreen(
            callbacks = callbacks,
            state = operationState,
            onSignIn = { onNavigate(); navController.navigate(AuthRoutes.SIGN_IN) { launchSingleTop = true } },
        )
    }
    composable(AuthRoutes.FORGOT_PASSWORD) {
        ForgotPasswordScreen(
            callbacks = callbacks,
            state = operationState,
            onBackToSignIn = {
                onNavigate()
                if (!navController.popBackStack(AuthRoutes.SIGN_IN, inclusive = false)) {
                    navController.navigate(AuthRoutes.SIGN_IN) { launchSingleTop = true }
                }
            },
        )
    }
}

@Composable
private fun DriverRoot(
    navController: NavHostController,
    selectedRoute: String,
    title: String,
    content: @Composable () -> Unit,
) {
    PostAuthScaffold(title, driverNavItems, selectedRoute, { navigateRoot(navController, it, PostAuthRoutes.DRIVER_HOME) }) { padding ->
        RootContent(padding) { content() }
    }
}

@Composable
private fun TrustedRoot(
    navController: NavHostController,
    state: PostAuthUiState,
    selectedRoute: String,
    title: String,
    content: @Composable () -> Unit,
) {
    val navItems = listOf(
        AppNavItem(PostAuthRoutes.TRUSTED_DRIVERS, "Drivers", Icons.Rounded.Groups),
        AppNavItem(PostAuthRoutes.TRUSTED_REQUESTS, "Requests", Icons.Rounded.Mail, state.trustedIncomingRequests.size),
        AppNavItem(PostAuthRoutes.TRUSTED_SETTINGS, "Settings", Icons.Rounded.Settings),
    )
    PostAuthScaffold(title, navItems, selectedRoute, { navigateRoot(navController, it, PostAuthRoutes.TRUSTED_DRIVERS) }) { padding ->
        RootContent(padding) { content() }
    }
}

private fun navigateRoot(navController: NavHostController, route: String, graphStart: String) {
    navController.navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graphStart) { saveState = true }
    }
}

@Composable
private fun SessionLoadingScreen() {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.drivealert_logo),
            contentDescription = "DriveAlert",
            modifier = Modifier.size(72.dp),
        )
    }
}
