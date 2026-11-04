package com.drivealert.mvp.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.drivealert.mvp.data.FirebaseAuthRepository
import com.drivealert.mvp.led.LedActivity
import com.drivealert.mvp.ui.AppPage
import com.drivealert.mvp.ui.BrandHeader
import com.drivealert.mvp.ui.FeedbackBanner
import com.drivealert.mvp.ui.PrimaryButton
import com.drivealert.mvp.ui.ScreenTitle
import com.drivealert.mvp.ui.theme.Border
import com.drivealert.mvp.ui.theme.DriveAlertTheme
import com.drivealert.mvp.ui.theme.DriveRed
import com.drivealert.mvp.ui.theme.Ink
import com.drivealert.mvp.ui.theme.TextMuted
import com.drivealert.mvp.ui.theme.TextPrimary
import com.drivealert.mvp.ui.theme.TextSecondary

class LoginActivity : ComponentActivity(), LoginContract.View {
    private val presenter: LoginContract.Presenter = LoginPresenter(FirebaseAuthRepository())
    private var state by mutableStateOf(LoginUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter.attach(this)
        setContent {
            DriveAlertTheme {
                if (state.showStarter) {
                    StarterContent(
                        isLoading = state.isLoading,
                        onSignIn = presenter::onSignInSelected,
                    )
                } else {
                    LoginContent(
                        state = state,
                        onSignIn = presenter::onSignIn,
                        onBack = { state = state.copy(showStarter = true, errorMessage = null) },
                    )
                }
            }
        }
        presenter.onViewReady()
    }

    override fun showLoginForm() {
        state = state.copy(showStarter = false)
    }

    override fun showLoading(isLoading: Boolean) {
        state = state.copy(isLoading = isLoading)
    }

    override fun showError(message: String?) {
        state = state.copy(errorMessage = message)
    }

    override fun openDeviceControl() {
        startActivity(Intent(this, LedActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        presenter.detach()
        super.onDestroy()
    }
}

private data class LoginUiState(
    val showStarter: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@Composable
private fun StarterContent(isLoading: Boolean, onSignIn: () -> Unit) {
    AppPage {
        Spacer(Modifier.height(42.dp))
        BrandHeader(prominent = true)
        Spacer(Modifier.height(46.dp))
        Text("Stay connected. Stay in control.", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            "Control an external LED and see the state reported by your ESP32.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(Modifier.height(48.dp))
        PrimaryButton("Sign In", enabled = !isLoading, onClick = onSignIn)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onSignIn: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun submit() {
        focusManager.clearFocus()
        onSignIn(email, password)
    }

    AppPage {
        BrandHeader()
        Spacer(Modifier.height(52.dp))
        ScreenTitle("Welcome back", "Sign in to control your DriveAlert LED device.")
        Spacer(Modifier.height(24.dp))
        state.errorMessage?.let {
            FeedbackBanner(it, isError = true)
            Spacer(Modifier.height(16.dp))
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            enabled = !state.isLoading,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            colors = authFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            enabled = !state.isLoading,
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = authFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Sign In", loading = state.isLoading, onClick = ::submit)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Back", color = DriveRed)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    disabledTextColor = TextMuted,
    focusedContainerColor = Ink,
    unfocusedContainerColor = Ink,
    disabledContainerColor = Ink,
    focusedBorderColor = DriveRed,
    unfocusedBorderColor = Border,
    focusedLabelColor = DriveRed,
    unfocusedLabelColor = TextSecondary,
    cursorColor = DriveRed,
)
