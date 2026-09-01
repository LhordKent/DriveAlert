package com.lhordkent.drivealert.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lhordkent.drivealert.R
import com.lhordkent.drivealert.DriveAlertApplication
import com.lhordkent.drivealert.auth.AuthCallbacks
import com.lhordkent.drivealert.auth.AuthOperationState
import com.lhordkent.drivealert.auth.AuthRoutes
import com.lhordkent.drivealert.auth.AuthSessionState
import com.lhordkent.drivealert.auth.AuthViewModel
import com.lhordkent.drivealert.auth.AuthViewModelFactory
import com.lhordkent.drivealert.auth.AuthenticationEntry
import com.lhordkent.drivealert.postauth.PostAuthUiState
import com.lhordkent.drivealert.postauth.PostAuthViewModel
import com.lhordkent.drivealert.postauth.PostAuthViewModelFactory
import com.lhordkent.drivealert.postauth.UserView
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

private val driverNavItems = listOf(
    AppNavItem(PostAuthRoutes.DRIVER_HOME, "Home", Icons.Rounded.Home),
    AppNavItem(PostAuthRoutes.DRIVER_ALERTS, "Alerts", Icons.Rounded.History),
    AppNavItem(PostAuthRoutes.DRIVER_CONTACTS, "Contacts", Icons.Rounded.People),
    AppNavItem(PostAuthRoutes.DRIVER_SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun DriveAlertApp() {
    val application = LocalContext.current.applicationContext as DriveAlertApplication
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(application.container.userProfileRepository),
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
    LaunchedEffect(authViewModel.sessionState.user?.uid) {
        authViewModel.sessionState.user?.uid?.let(postAuthViewModel::bindLocalData)
        authViewModel.sessionState.user?.uid?.let(postAuthViewModel::bindCloudProfile)
        authViewModel.sessionState.user?.uid?.let(postAuthViewModel::bindPreferences)
        authViewModel.sessionState.user?.uid?.let(postAuthViewModel::bindConnections)
        authViewModel.sessionState.user?.uid?.let(postAuthViewModel::bindStageSyncRecords)
    }
    DriveAlertApp(
        callbacks = authViewModel.callbacks,
        operationState = authViewModel.state,
        sessionState = authViewModel.sessionState,
        postAuthState = postAuthViewModel.state,
        onNavigate = authViewModel::clearMessages,
        onSetAccountEmail = postAuthViewModel::setAccountEmail,
        onSetProfileName = postAuthViewModel::setProfileDisplayName,
        onChooseView = postAuthViewModel::chooseView,
        onCompleteSetup = postAuthViewModel::completeDriverSetup,
        onReconnectDevice = postAuthViewModel::reconnectDevice,
        onCompleteAlignment = postAuthViewModel::completeAlignmentCheck,
        onCompleteCalibration = postAuthViewModel::completeCalibration,
        onFilterChange = postAuthViewModel::selectAlertFilter,
        onSendRequest = postAuthViewModel::sendConnectionRequest,
        onAcceptRequest = postAuthViewModel::acceptRequest,
        onDeclineRequest = postAuthViewModel::declineRequest,
        onCancelRequest = postAuthViewModel::cancelRequest,
        onRemoveConnection = postAuthViewModel::removeConnection,
        onWarningSoundChange = postAuthViewModel::selectWarningSound,
        onVolumeChange = postAuthViewModel::selectPreferredVolume,
        onNotificationsChange = postAuthViewModel::updateNotifications,
        onClearPostAuth = postAuthViewModel::clearForSignOut,
    )
}

@Composable
fun DriveAlertApp(
    callbacks: AuthCallbacks,
    operationState: AuthOperationState = AuthOperationState(),
    sessionState: AuthSessionState = AuthSessionState(isResolving = false),
    postAuthState: PostAuthUiState = PostAuthViewModel.seedState(),
    onNavigate: () -> Unit = {},
    onSetAccountEmail: (String) -> Unit = {},
    onSetProfileName: (String) -> Unit = {},
    onChooseView: (UserView) -> Unit = {},
    onCompleteSetup: () -> Unit = {},
    onReconnectDevice: () -> Unit = {},
    onCompleteAlignment: () -> Unit = {},
    onCompleteCalibration: () -> Unit = {},
    onFilterChange: (com.lhordkent.drivealert.postauth.WarningStage?) -> Unit = {},
    onSendRequest: (UserView, String) -> String? = { _, _ -> null },
    onAcceptRequest: (UserView, String) -> Unit = { _, _ -> },
    onDeclineRequest: (UserView, String) -> Unit = { _, _ -> },
    onCancelRequest: (UserView, String) -> Unit = { _, _ -> },
    onRemoveConnection: (UserView, String) -> Unit = { _, _ -> },
    onWarningSoundChange: (com.lhordkent.drivealert.postauth.WarningSound) -> Unit = {},
    onVolumeChange: (com.lhordkent.drivealert.postauth.PreferredVolume) -> Unit = {},
    onNotificationsChange: (com.lhordkent.drivealert.postauth.NotificationPreferences) -> Unit = {},
    onClearPostAuth: () -> Unit = {},
) {
    if (sessionState.isResolving) {
        SessionLoadingScreen()
        return
    }

    val navController = rememberNavController()
    val user = sessionState.user

    LaunchedEffect(user?.uid, sessionState.entry) {
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
                    if (postAuthState.driverSetupComplete) PostAuthRoutes.DRIVER_HOME else PostAuthRoutes.DRIVER_SETUP
            }
            navController.navigate(destination) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
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
                )
            }
            composable(PostAuthRoutes.DRIVER_DEVICE_CONNECTION) {
                DeviceConnectionScreen(postAuthState.deviceConnection, onReconnectDevice, navController::popBackStack)
            }
            composable(PostAuthRoutes.DRIVER_ALIGNMENT) {
                CameraAlignmentScreen(postAuthState.alignmentReady, onCompleteAlignment, navController::popBackStack)
            }
            composable(PostAuthRoutes.DRIVER_CALIBRATION) {
                CalibrationScreen(postAuthState.calibrationState, onCompleteCalibration, navController::popBackStack)
            }
            composable(PostAuthRoutes.DRIVER_HOME) {
                DriverRoot(navController, PostAuthRoutes.DRIVER_HOME, "Driver Home") {
                    DriverHomeScreen(
                        state = postAuthState,
                        onPreviewMonitoring = { navController.navigate(PostAuthRoutes.DRIVER_MONITORING) },
                        onSetupDevice = { navController.navigate(PostAuthRoutes.DRIVER_SETUP) },
                        onOpenAlerts = { navigateRoot(navController, PostAuthRoutes.DRIVER_ALERTS, PostAuthRoutes.DRIVER_HOME) },
                    )
                }
            }
            composable(PostAuthRoutes.DRIVER_MONITORING) {
                MonitoringPreviewScreen(
                    scenario = postAuthState.monitoringScenario,
                    activeWarningStage = postAuthState.activeWarningStage,
                    confirmedEvents = postAuthState.driverAlerts.count { it.occurredAt.toLocalDate() == java.time.LocalDate.now() },
                    latestEvent = postAuthState.driverAlerts.maxByOrNull { it.occurredAt }?.occurredAt,
                    onExit = navController::popBackStack,
                )
            }
            composable(PostAuthRoutes.DRIVER_ALERTS) {
                DriverRoot(navController, PostAuthRoutes.DRIVER_ALERTS, "Alert History") {
                    AlertHistoryScreen(
                        postAuthState.driverAlerts,
                        postAuthState.driverSyncRecords,
                        postAuthState.alertFilter,
                        onFilterChange,
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
                if (event != null) AlertDetailScreen(event = event, onBack = navController::popBackStack)
            }
            composable(PostAuthRoutes.DRIVER_CONTACTS) {
                DriverRoot(navController, PostAuthRoutes.DRIVER_CONTACTS, "Trusted Contacts") {
                    DriverContactsScreen(
                        contacts = postAuthState.approvedContacts,
                        incoming = postAuthState.driverIncomingRequests,
                        outgoing = postAuthState.driverOutgoingRequests,
                        onInvite = { navController.navigate(PostAuthRoutes.DRIVER_INVITE) },
                        onAccept = { onAcceptRequest(UserView.DRIVER, it) },
                        onDecline = { onDeclineRequest(UserView.DRIVER, it) },
                        onCancel = { onCancelRequest(UserView.DRIVER, it) },
                        onRemove = { onRemoveConnection(UserView.DRIVER, it) },
                        cloudErrorMessage = postAuthState.cloudConnectionErrorMessage,
                    )
                }
            }
            composable(PostAuthRoutes.DRIVER_INVITE) {
                InviteConnectionScreen(UserView.DRIVER, { onSendRequest(UserView.DRIVER, it) }, navController::popBackStack)
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
                        onLogout = { onClearPostAuth(); callbacks.onSignOut() },
                    )
                }
            }
            composable(PostAuthRoutes.TRUSTED_DRIVERS) {
                TrustedRoot(navController, postAuthState, PostAuthRoutes.TRUSTED_DRIVERS, "Connected Drivers") {
                    TrustedDriversScreen(
                        drivers = postAuthState.connectedDrivers,
                        onDriverSelected = { navController.navigate(PostAuthRoutes.trustedDriver(it)) },
                        onInvite = { navController.navigate(PostAuthRoutes.TRUSTED_INVITE) },
                        onRemove = { onRemoveConnection(UserView.TRUSTED_CONTACT, it) },
                        cloudErrorMessage = postAuthState.cloudConnectionErrorMessage,
                    )
                }
            }
            composable(
                route = PostAuthRoutes.TRUSTED_SHARED,
                arguments = listOf(navArgument("driverId") { type = NavType.StringType }),
            ) { entry ->
                val driverId = entry.arguments?.getString("driverId")
                val driver = postAuthState.connectedDrivers.firstOrNull { it.id == driverId }
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
                        onAccept = { onAcceptRequest(UserView.TRUSTED_CONTACT, it) },
                        onDecline = { onDeclineRequest(UserView.TRUSTED_CONTACT, it) },
                        onCancel = { onCancelRequest(UserView.TRUSTED_CONTACT, it) },
                        cloudErrorMessage = postAuthState.cloudConnectionErrorMessage,
                    )
                }
            }
            composable(PostAuthRoutes.TRUSTED_INVITE) {
                InviteConnectionScreen(UserView.TRUSTED_CONTACT, { onSendRequest(UserView.TRUSTED_CONTACT, it) }, navController::popBackStack)
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
                        onLogout = { onClearPostAuth(); callbacks.onSignOut() },
                    )
                }
            }
            composable(PostAuthRoutes.ACCOUNT) {
                AccountScreen(
                    displayName = postAuthState.profileDisplayName.ifBlank { user?.displayName.orEmpty() },
                    email = user?.email.orEmpty(),
                    onBack = navController::popBackStack,
                )
            }
            composable(PostAuthRoutes.NOTIFICATIONS) {
                NotificationSettingsScreen(postAuthState.activeView, postAuthState.notifications, onNotificationsChange, navController::popBackStack)
            }
            composable(PostAuthRoutes.WARNING_SOUND) {
                WarningSoundScreen(
                    postAuthState.warningSound,
                    postAuthState.preferredVolume,
                    onWarningSoundChange,
                    onVolumeChange,
                    navController::popBackStack,
                )
            }
            composable(PostAuthRoutes.ABOUT) { AboutDriveAlertScreen(navController::popBackStack) }
        }
    }
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
