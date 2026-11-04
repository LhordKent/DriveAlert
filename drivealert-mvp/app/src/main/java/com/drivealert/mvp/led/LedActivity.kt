package com.drivealert.mvp.led

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivealert.mvp.data.DeviceAvailability
import com.drivealert.mvp.data.FirebaseAuthRepository
import com.drivealert.mvp.data.FirebaseLedRepository
import com.drivealert.mvp.login.LoginActivity
import com.drivealert.mvp.ui.AppPage
import com.drivealert.mvp.ui.BrandHeader
import com.drivealert.mvp.ui.FeedbackBanner
import com.drivealert.mvp.ui.GroupSurface
import com.drivealert.mvp.ui.KeyValueRow
import com.drivealert.mvp.ui.PrimaryButton
import com.drivealert.mvp.ui.ScreenTitle
import com.drivealert.mvp.ui.SecondaryButton
import com.drivealert.mvp.ui.StatusPill
import com.drivealert.mvp.ui.StatusTone
import com.drivealert.mvp.ui.theme.DriveAlertTheme
import com.drivealert.mvp.ui.theme.DriveRed
import com.drivealert.mvp.ui.theme.TextPrimary
import com.drivealert.mvp.ui.theme.TextSecondary

class LedActivity : ComponentActivity(), LedContract.View {
    private val presenter: LedContract.Presenter = LedPresenter(
        authRepository = FirebaseAuthRepository(),
        ledRepository = FirebaseLedRepository(),
        heartbeatScheduler = MainThreadHeartbeatScheduler(),
    )
    private var state by mutableStateOf(LedUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter.attach(this)
        setContent {
            DriveAlertTheme {
                LedDeviceContent(
                    state = state,
                    onTurnOn = presenter::onTurnOn,
                    onTurnOff = presenter::onTurnOff,
                    onLogout = presenter::onLogout,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        presenter.start()
    }

    override fun onStop() {
        presenter.stop()
        super.onStop()
    }

    override fun showAccount(email: String) {
        state = state.copy(email = email)
    }

    override fun showInitialLoading(isLoading: Boolean) {
        state = state.copy(initialLoading = isLoading)
    }

    override fun showCommandLoading(isLoading: Boolean) {
        state = state.copy(commandLoading = isLoading)
    }

    override fun showDeviceState(
        requestedState: String,
        actualState: String,
        availability: DeviceAvailability,
    ) {
        state = state.copy(
            requestedState = requestedState,
            actualState = actualState,
            availability = availability,
        )
    }

    override fun showFeedback(message: String?, isError: Boolean) {
        state = state.copy(feedback = message, feedbackIsError = isError)
    }

    override fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }
}

private data class LedUiState(
    val email: String = "",
    val initialLoading: Boolean = true,
    val commandLoading: Boolean = false,
    val requestedState: String = "Not set",
    val actualState: String = "Unknown",
    val availability: DeviceAvailability = DeviceAvailability.UNAVAILABLE,
    val feedback: String? = null,
    val feedbackIsError: Boolean = false,
)

@Composable
private fun LedDeviceContent(
    state: LedUiState,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onLogout: () -> Unit,
) {
    AppPage(horizontalPadding = 20) {
        BrandHeader()
        Spacer(Modifier.height(32.dp))
        ScreenTitle("LED device control", "Send a command, then verify what the ESP32 reports.")
        Spacer(Modifier.height(20.dp))

        GroupSurface {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ESP32 status", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(state.availability.label, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                StatusPill(
                    label = state.availability.label,
                    tone = when (state.availability) {
                        DeviceAvailability.ONLINE -> StatusTone.SUCCESS
                        DeviceAvailability.OFFLINE -> StatusTone.WARNING
                        DeviceAvailability.UNAVAILABLE -> StatusTone.ERROR
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            if (state.initialLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = DriveRed, strokeWidth = 2.dp)
                    Text("Loading device state…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            } else {
                KeyValueRow("Requested LED state", state.requestedState)
                KeyValueRow("Actual LED state", state.actualState)
            }
        }

        Spacer(Modifier.height(20.dp))
        state.feedback?.let {
            FeedbackBanner(it, state.feedbackIsError)
            Spacer(Modifier.height(16.dp))
        }
        if (state.commandLoading) {
            Text("Sending command…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
        }
        PrimaryButton("TURN LED ON", enabled = !state.commandLoading, onClick = onTurnOn)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("TURN LED OFF", enabled = !state.commandLoading, onClick = onTurnOff)

        Spacer(Modifier.height(32.dp))
        Text("Signed-in account", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text(state.email.ifBlank { "Unknown account" }, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        Spacer(Modifier.height(14.dp))
        SecondaryButton("Logout", enabled = !state.commandLoading, onClick = onLogout)
        Spacer(Modifier.height(24.dp))
    }
}
