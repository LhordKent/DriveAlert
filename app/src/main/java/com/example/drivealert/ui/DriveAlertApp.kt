package com.example.drivealert.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.drivealert.model.DriveAlertAppState
import com.example.drivealert.model.Route

@Composable
fun DriveAlertApp(state: DriveAlertAppState = viewModel(), navController: NavHostController = rememberNavController()) {
    val navigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            launchSingleTop = true
            restoreState = true
        }
    }
    val back: () -> Unit = { navController.popBackStack() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    NavHost(navController = navController, startDestination = Route.Starter) {
        composable(Route.Starter) { StarterScreen(navigate) }
        composable(Route.SignIn) { SignInScreen(navigate, back) }
        composable(Route.SignUp) { SignUpScreen(navigate, back) }
        composable(Route.ForgotPassword) { ForgotPasswordScreen(back) }
        composable(Route.Role) { RoleScreen(state, navigate, back) }
        composable(Route.PowerOn) { PowerOnScreen(state, navigate, back) }
        composable(Route.Connect) { ConnectDeviceScreen(state, navigate, back) }
        composable(Route.DeviceCheck) { DeviceCheckScreen(navigate, back) }
        composable(Route.CameraAlignment) { CameraAlignmentScreen(state, navigate, back) }
        composable(Route.Calibration) { CalibrationScreen(state, navigate, back) }
        composable(Route.DriverHome) { DriverHomeScreen(state, navigate) }
        composable(Route.Monitoring) { MonitoringScreen(state, navigate) }
        composable(Route.Alerts) { AlertsScreen(state, navigate) }
        composable(Route.AlertDetail) { AlertDetailScreen(state, navigate) }
        composable(Route.Insights) { InsightsScreen(navigate) }
        composable(Route.Contacts) { ContactsScreen(state, navigate) }
        composable(Route.AddContact) { AddContactScreen(state, navigate) }
        composable(Route.DeviceManagement) { DeviceManagementScreen(state, navigate) }
        composable(Route.DriverAccess) { DriverAccessScreen(navigate) }
        composable(Route.AddDriver) { AddDriverScreen(state, navigate) }
        composable(Route.Subscription) { SubscriptionScreen(navigate) }
        composable(Route.DriverSettings) { DriverSettingsScreen(navigate) }
        composable(Route.Retention) { RetentionScreen(state, navigate) }
        composable(Route.WarningSound) { WarningSoundScreen(state, navigate) }
        composable(Route.Notifications) { NotificationsScreen(state, navigate) }
        composable(Route.About) { AboutScreen(navigate) }
        composable(Route.Profile) { ProfileScreen(state, navigate) }
        composable(Route.ConnectedDrivers) { ConnectedDriversScreen(state, navigate) }
        composable(Route.PendingRequests) { PendingRequestsScreen(state, navigate) }
        composable(Route.EscalatedRecords) { EscalatedRecordsScreen(state, navigate) }
        composable(Route.EscalatedDetail) { EscalatedDetailScreen(state, navigate) }
        composable(Route.ContactSettings) { ContactSettingsScreen(state, navigate) }
    }
    }
}
