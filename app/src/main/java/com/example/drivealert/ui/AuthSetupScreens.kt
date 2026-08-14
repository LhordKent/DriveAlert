package com.example.drivealert.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.drivealert.model.AccountRole
import com.example.drivealert.model.ConnectionStage
import com.example.drivealert.model.DriveAlertAppState
import com.example.drivealert.model.Route
import com.example.drivealert.ui.theme.Border
import com.example.drivealert.ui.theme.DriveRed
import com.example.drivealert.ui.theme.DriveRedSoft
import com.example.drivealert.ui.theme.Info
import com.example.drivealert.ui.theme.InfoSoft
import com.example.drivealert.ui.theme.Success
import com.example.drivealert.ui.theme.SuccessSoft
import com.example.drivealert.ui.theme.Surface
import com.example.drivealert.ui.theme.TextMuted
import com.example.drivealert.ui.theme.TextPrimary
import com.example.drivealert.ui.theme.TextSecondary
import com.example.drivealert.ui.theme.Warning
import com.example.drivealert.ui.theme.WarningSoft

@Composable
fun StarterScreen(navigate: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().background(com.example.drivealert.ui.theme.Ink).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(46.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            DriveAlertLogo()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Stay alert. Drive safer.", style = MaterialTheme.typography.headlineLarge)
                Text("A calm companion for device readiness, monitoring sessions, and confirmed warning history.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureLine("Offline monitoring interface")
                FeatureLine("Onboard warning speaker status")
                FeatureLine("Escalated records for Trusted Contacts")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Sign In") { navigate(Route.SignIn) }
            SecondaryButton("Create Account") { navigate(Route.SignUp) }
            SecondaryButton("Continue with Google") { navigate(Route.Role) }
            Text("Prototype only. No account or network connection is created.", style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(DriveRedSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CheckCircle, null, tint = DriveRed, modifier = Modifier.size(14.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
fun SignInScreen(navigate: (String) -> Unit, back: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    AppPage("Welcome back", "Sign in to continue", back) {
        Spacer(Modifier.height(26.dp)); DriveAlertLogo()
        Spacer(Modifier.height(14.dp))
        FormField(email, { email = it }, "Email", error = if (attempted && !email.contains("@")) "Enter a valid email address" else null)
        FormField(password, { password = it }, "Password", error = if (attempted && password.length < 4) "Enter at least 4 characters for this demo" else null)
        TextButton(onClick = { navigate(Route.ForgotPassword) }, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?", color = DriveRed) }
        PrimaryButton("Sign In") {
            attempted = true
            if (email.contains("@") && password.length >= 4) navigate(Route.Role)
        }
        SecondaryButton("Continue with Google") { navigate(Route.Role) }
        Spacer(Modifier.height(28.dp))
        Text("New to DriveAlert? Create an account", color = DriveRed, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
fun SignUpScreen(navigate: (String) -> Unit, back: () -> Unit) {
    var first by remember { mutableStateOf("") }; var middle by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }; var attempted by remember { mutableStateOf(false) }
    AppPage("Create account", "Your profile stays local in this prototype", back) {
        Spacer(Modifier.height(6.dp))
        FormField(first, { first = it }, "First name", error = if (attempted && first.isBlank()) "Required" else null)
        FormField(middle, { middle = it }, "Middle initial")
        FormField(last, { last = it }, "Last name", error = if (attempted && last.isBlank()) "Required" else null)
        FormField(phone, { phone = it }, "Phone number")
        FormField(email, { email = it }, "Email", error = if (attempted && !email.contains("@")) "Enter a valid email" else null)
        FormField(password, { password = it }, "Password", error = if (attempted && password.length < 4) "Use at least 4 characters for this demo" else null)
        FormField(confirm, { confirm = it }, "Confirm password", error = if (attempted && confirm != password) "Passwords do not match" else null)
        PrimaryButton("Create Account") {
            attempted = true
            if (first.isNotBlank() && last.isNotBlank() && email.contains("@") && password.length >= 4 && confirm == password) navigate(Route.Role)
        }
        SecondaryButton("Already have an account? Sign In") { navigate(Route.SignIn) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ForgotPasswordScreen(back: () -> Unit) {
    var email by remember { mutableStateOf("") }; var sent by remember { mutableStateOf(false) }
    AppPage("Forgot password", "Receive local demonstration feedback", back) {
        Spacer(Modifier.height(24.dp)); DriveAlertLogo()
        Text("Enter your registered email address. No message will actually be sent.", color = TextSecondary)
        FormField(email, { email = it; sent = false }, "Email", error = if (email.isNotBlank() && !email.contains("@")) "Enter a valid email" else null)
        if (sent) SectionCard { StatusPill("Reset instructions ready", Success, SuccessSoft, Icons.Rounded.CheckCircle); Text("Demo complete. Return to Sign In to continue.", color = TextSecondary) }
        PrimaryButton("Send Reset Link", onClick = { if (email.contains("@")) sent = true })
        SecondaryButton("Back to Sign In", onClick = back)
    }
}

@Composable
fun RoleScreen(state: DriveAlertAppState, navigate: (String) -> Unit, back: () -> Unit) {
    AppPage("How will you use DriveAlert?", "Choose a prototype view", back) {
        Spacer(Modifier.height(70.dp))
        RoleCard(Icons.Rounded.Person, "Continue as Driver", "Set up the DriveAlert device, start monitoring, and review your warning history.") {
            state.accountRole = AccountRole.Driver; navigate(Route.PowerOn)
        }
        RoleCard(Icons.Rounded.Groups, "Continue as Trusted Contact", "Review Escalated Alert Records shared by connected Drivers.") {
            state.accountRole = AccountRole.TrustedContact; navigate(Route.ConnectedDrivers)
        }
    }
}

@Composable
private fun RoleCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(DriveRedSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = DriveRed) }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text("Continue", color = DriveRed, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PowerOnScreen(state: DriveAlertAppState, navigate: (String) -> Unit, back: () -> Unit) {
    AppPage("Power on device", "First-time setup", back) {
        Spacer(Modifier.height(8.dp)); StepIndicator(1, 5)
        SetupHero(Icons.Rounded.PowerSettingsNew, "Power On DriveAlert Device", "Power on your DriveAlert device before continuing.")
        SectionCard {
            when (state.connectionStage) {
                ConnectionStage.Idle -> StatusPill("Ready to search", TextSecondary, com.example.drivealert.ui.theme.InkRaised, Icons.Rounded.Sensors)
                ConnectionStage.Searching -> { LoadingBlock("Searching for device...") }
                else -> StatusPill("Device found", Success, SuccessSoft, Icons.Rounded.CheckCircle)
            }
            Text("Keep the device nearby and connected to power.", color = TextSecondary)
        }
        PrimaryButton(if (state.connectionStage == ConnectionStage.Idle) "Search for Device" else "Continue") {
            if (state.connectionStage == ConnectionStage.Idle) state.connectionStage = ConnectionStage.Searching
            else { state.connectionStage = ConnectionStage.Found; navigate(Route.Connect) }
        }
        if (state.connectionStage == ConnectionStage.Searching) SecondaryButton("Device Found") { state.connectionStage = ConnectionStage.Found }
    }
}

@Composable
fun ConnectDeviceScreen(state: DriveAlertAppState, navigate: (String) -> Unit, back: () -> Unit) {
    val labels = mapOf(
        ConnectionStage.Idle to "Ready", ConnectionStage.Searching to "Searching for device",
        ConnectionStage.Found to "Device found", ConnectionStage.Configuring to "Sending Wi-Fi configuration",
        ConnectionStage.Connecting to "Connecting", ConnectionStage.Failed to "Connection failed",
        ConnectionStage.Connected to "Connected",
    )
    AppPage("Connect DriveAlert Device", "Simulated Wi-Fi provisioning", back) {
        StepIndicator(2, 5)
        SetupHero(Icons.Rounded.Wifi, "Secure local connection", "This demonstration never reads or sends Wi-Fi credentials.")
        SectionCard {
            StatusPill(
                labels[state.connectionStage] ?: "Ready",
                when (state.connectionStage) { ConnectionStage.Connected -> Success; ConnectionStage.Failed -> DriveRed; else -> Warning },
                when (state.connectionStage) { ConnectionStage.Connected -> SuccessSoft; ConnectionStage.Failed -> DriveRedSoft; else -> WarningSoft },
                when (state.connectionStage) { ConnectionStage.Connected -> Icons.Rounded.CheckCircle; ConnectionStage.Failed -> Icons.Rounded.Error; else -> Icons.Rounded.Wifi },
            )
            if (state.connectionStage !in listOf(ConnectionStage.Connected, ConnectionStage.Failed)) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DriveRed)
            Text("Tap Connect Device to advance each demonstration stage.", color = TextSecondary)
        }
        if (state.connectionStage == ConnectionStage.Failed) {
            PrimaryButton("Try Again") { state.completeConnection() }
        } else if (state.connectionStage == ConnectionStage.Connected) {
            PrimaryButton("Continue to Device Check") { navigate(Route.DeviceCheck) }
        } else {
            PrimaryButton("Connect Device") { state.advanceConnection() }
        }
        SecondaryButton("Cancel") { back() }
    }
}

@Composable
fun DeviceCheckScreen(navigate: (String) -> Unit, back: () -> Unit) {
    AppPage("Device check", "Everything needed for monitoring", back) {
        StepIndicator(3, 5)
        Spacer(Modifier.height(4.dp))
        CheckRow(Icons.Rounded.Devices, "DriveAlert Device", "Connected")
        CheckRow(Icons.Rounded.CameraAlt, "Camera", "Available")
        CheckRow(Icons.Rounded.Speaker, "Onboard Speaker", "Available")
        CheckRow(Icons.Rounded.Lightbulb, "IR / Low-Light Setup", "Ready")
        SectionCard {
            StatusPill("All checks passed", Success, SuccessSoft, Icons.Rounded.CheckCircle)
            Text("Your device is ready for camera alignment.", color = TextSecondary)
        }
        PrimaryButton("Continue to Camera Setup") { navigate(Route.CameraAlignment) }
    }
}

@Composable
private fun CheckRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, status: String) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = DriveRed, modifier = Modifier.size(24.dp)); Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            StatusPill(status, Success, SuccessSoft, Icons.Rounded.CheckCircle)
        }
    }
}

@Composable
fun CameraAlignmentScreen(state: DriveAlertAppState, navigate: (String) -> Unit, back: () -> Unit) {
    val feedback = listOf("Center your face", "Move closer", "Face detected", "Camera ready")
    AppPage("Camera alignment", "Position your face inside the Driver Zone", back) {
        StepIndicator(4, 5)
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(CardShape).background(Surface).border(1.dp, Border, CardShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Person, null, tint = TextMuted, modifier = Modifier.size(92.dp))
            Canvas(Modifier.fillMaxSize().padding(34.dp)) { drawOval(DriveRed, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx())) }
            Text("DRIVER ZONE", color = DriveRed, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        }
        SectionCard {
            StatusPill(feedback[state.faceFeedbackIndex], if (state.faceFeedbackIndex >= 2) Success else Warning, if (state.faceFeedbackIndex >= 2) SuccessSoft else WarningSoft, if (state.faceFeedbackIndex >= 2) Icons.Rounded.Visibility else Icons.Rounded.CameraAlt)
            Text("Adjust the camera until your full face remains inside the guide.", color = TextSecondary)
        }
        if (state.faceFeedbackIndex < feedback.lastIndex) PrimaryButton("Check Alignment") { state.faceFeedbackIndex++ }
        else PrimaryButton("Continue to Calibration") { navigate(Route.Calibration) }
        SecondaryButton("Move Camera") { state.faceFeedbackIndex = 0 }
    }
}

@Composable
fun CalibrationScreen(state: DriveAlertAppState, navigate: (String) -> Unit, back: () -> Unit) {
    AppPage("Driver calibration", "Personalize the monitoring profile", back) {
        StepIndicator(5, 5)
        SetupHero(Icons.Rounded.Sensors, "Driver Calibration", "DriveAlert uses calibration to establish personalized eye, mouth, and head-position measurements for the Driver.")
        SectionCard {
            val done = state.calibrationProgress == 100
            StatusPill(if (done) "Calibration Complete" else "Calibrating Driver Profile...", if (done) Success else Warning, if (done) SuccessSoft else WarningSoft, if (done) Icons.Rounded.CheckCircle else Icons.Rounded.Sensors)
            LinearProgressIndicator(progress = { state.calibrationProgress / 100f }, modifier = Modifier.fillMaxWidth(), color = if (done) Success else DriveRed)
            Text("${state.calibrationProgress}%", style = MaterialTheme.typography.headlineMedium)
            Text(if (done) "Your profile is ready." else "Sit naturally, look forward, and keep your face inside the Driver Zone.", color = TextSecondary)
        }
        if (state.calibrationProgress < 100) PrimaryButton(if (state.calibrationProgress == 0) "Start Calibration" else "Continue Calibration") { state.advanceCalibration() }
        else PrimaryButton("Continue to Driver Home") { navigate(Route.DriverHome) }
    }
}

@Composable
private fun SetupHero(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(DriveRedSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = DriveRed, modifier = Modifier.size(36.dp)) }
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center)
    }
}
