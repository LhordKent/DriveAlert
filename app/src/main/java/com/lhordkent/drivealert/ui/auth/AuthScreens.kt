package com.lhordkent.drivealert.ui.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lhordkent.drivealert.auth.AuthCallbacks
import com.lhordkent.drivealert.auth.AuthOperationState
import com.lhordkent.drivealert.auth.AuthValidation
import com.lhordkent.drivealert.auth.SignUpInput
import com.lhordkent.drivealert.ui.theme.DriveRed
import com.lhordkent.drivealert.ui.theme.TextPrimary
import com.lhordkent.drivealert.ui.theme.TextSecondary

@Composable
fun StarterScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    state: AuthOperationState = AuthOperationState(),
) {
    AuthPage {
        Spacer(Modifier.height(42.dp))
        BrandHeader(prominent = true)
        Spacer(Modifier.height(46.dp))
        Text(
            text = "Stay alert. Drive safer.",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "A calm companion for building safer driving habits.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
        Spacer(Modifier.height(48.dp))
        AuthStatusBanner(state.errorMessage, state.successMessage)
        if (state.errorMessage != null || state.successMessage != null) Spacer(Modifier.height(16.dp))
        PrimaryAuthButton(
            text = "Sign In",
            onClick = onSignIn,
            loading = state.isLoading,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryAuthButton(
            text = "Create Account",
            onClick = onCreateAccount,
            enabled = !state.isLoading,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SignInScreen(
    callbacks: AuthCallbacks,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    state: AuthOperationState = AuthOperationState(),
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val emailError = if (attempted) AuthValidation.emailError(email) else null
    val passwordError = if (attempted) AuthValidation.signInPasswordError(password) else null

    fun submit() {
        attempted = true
        if (AuthValidation.emailError(email) == null && AuthValidation.signInPasswordError(password) == null) {
            focusManager.clearFocus()
            callbacks.onSignIn(email.trim(), password)
        }
    }

    AuthPage {
        BrandHeader()
        Spacer(Modifier.height(52.dp))
        ScreenTitle(
            title = "Welcome back",
            description = "Sign in to continue to DriveAlert.",
        )
        Spacer(Modifier.height(24.dp))
        AuthStatusBanner(state.errorMessage, state.successMessage)
        if (state.errorMessage != null || state.successMessage != null) Spacer(Modifier.height(16.dp))
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            error = emailError,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.testTag(AuthTestTags.EMAIL),
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            error = passwordError,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.testTag(AuthTestTags.PASSWORD),
        )
        TextButton(
            onClick = onForgotPassword,
            enabled = !state.isLoading,
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            Text("Forgot password?", color = DriveRed)
        }
        Spacer(Modifier.height(6.dp))
        PrimaryAuthButton(
            text = "Sign In",
            onClick = { submit() },
            loading = state.isLoading,
            modifier = Modifier.testTag(AuthTestTags.PRIMARY_ACTION),
        )
        Spacer(Modifier.height(32.dp))
        InlineNavigationPrompt(
            prompt = "New to DriveAlert?",
            action = "Create account",
            onClick = onCreateAccount,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun CreateAccountScreen(
    callbacks: AuthCallbacks,
    onSignIn: () -> Unit,
    state: AuthOperationState = AuthOperationState(),
) {
    var firstName by rememberSaveable { mutableStateOf("") }
    var middleInitial by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val firstNameError = if (attempted) AuthValidation.requiredError(firstName, "First name") else null
    val middleError = if (attempted) AuthValidation.middleInitialError(middleInitial) else null
    val lastNameError = if (attempted) AuthValidation.requiredError(lastName, "Last name") else null
    val phoneError = if (attempted) AuthValidation.phoneError(phone) else null
    val emailError = if (attempted) AuthValidation.emailError(email) else null
    val passwordError = if (attempted) AuthValidation.signUpPasswordError(password) else null
    val confirmationError = if (attempted) AuthValidation.confirmationError(password, confirmation) else null

    fun submit() {
        attempted = true
        val isValid = listOf(
            AuthValidation.requiredError(firstName, "First name"),
            AuthValidation.middleInitialError(middleInitial),
            AuthValidation.requiredError(lastName, "Last name"),
            AuthValidation.phoneError(phone),
            AuthValidation.emailError(email),
            AuthValidation.signUpPasswordError(password),
            AuthValidation.confirmationError(password, confirmation),
        ).all { it == null }
        if (isValid) {
            focusManager.clearFocus()
            callbacks.onSignUp(
                SignUpInput(
                    firstName = firstName.trim(),
                    middleInitial = middleInitial.trim(),
                    lastName = lastName.trim(),
                    phoneNumber = phone.trim(),
                    email = email.trim(),
                    password = password,
                    confirmPassword = confirmation,
                ),
            )
        }
    }

    val nextAction = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
    AuthPage {
        BrandHeader()
        Spacer(Modifier.height(36.dp))
        ScreenTitle(
            title = "Create your account",
            description = "Create your profile and get ready for safer drives.",
        )
        Spacer(Modifier.height(24.dp))
        AuthStatusBanner(state.errorMessage, state.successMessage)
        if (state.errorMessage != null || state.successMessage != null) Spacer(Modifier.height(16.dp))
        AuthTextField(
            firstName, { firstName = it }, "First name", Modifier.testTag(AuthTestTags.FIRST_NAME),
            firstNameError, !state.isLoading,
            KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words, imeAction = ImeAction.Next), nextAction,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            middleInitial, { middleInitial = it.take(2) }, "Middle initial (optional)", Modifier.testTag(AuthTestTags.MIDDLE_INITIAL),
            middleError, !state.isLoading,
            KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters, imeAction = ImeAction.Next), nextAction,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            lastName, { lastName = it }, "Last name", Modifier.testTag(AuthTestTags.LAST_NAME),
            lastNameError, !state.isLoading,
            KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words, imeAction = ImeAction.Next), nextAction,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            phone, { phone = it }, "Phone number (optional)", Modifier.testTag(AuthTestTags.PHONE),
            phoneError, !state.isLoading,
            KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next), nextAction,
        )
        Spacer(Modifier.height(12.dp))
        AuthTextField(
            email, { email = it }, "Email", Modifier.testTag(AuthTestTags.EMAIL),
            emailError, !state.isLoading,
            KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), nextAction,
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(
            password, { password = it }, "Password", Modifier.testTag(AuthTestTags.PASSWORD),
            passwordError, !state.isLoading,
            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next), nextAction,
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(
            confirmation, { confirmation = it }, "Confirm password", Modifier.testTag(AuthTestTags.CONFIRM_PASSWORD),
            confirmationError, !state.isLoading,
            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            KeyboardActions(onDone = { submit() }),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryAuthButton(
            text = "Create Account",
            onClick = { submit() },
            loading = state.isLoading,
            modifier = Modifier.testTag(AuthTestTags.PRIMARY_ACTION),
        )
        Spacer(Modifier.height(24.dp))
        InlineNavigationPrompt(
            prompt = "Already have an account?",
            action = "Sign in",
            onClick = onSignIn,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun ForgotPasswordScreen(
    callbacks: AuthCallbacks,
    onBackToSignIn: () -> Unit,
    state: AuthOperationState = AuthOperationState(),
) {
    var email by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val emailError = if (attempted) AuthValidation.emailError(email) else null

    fun submit() {
        attempted = true
        if (AuthValidation.emailError(email) == null) {
            focusManager.clearFocus()
            callbacks.onResetPassword(email.trim())
        }
    }

    AuthPage {
        BrandHeader()
        Spacer(Modifier.height(72.dp))
        ScreenTitle(
            title = "Forgot password?",
            description = "Enter the email associated with your account.",
        )
        Spacer(Modifier.height(24.dp))
        AuthStatusBanner(state.errorMessage, state.successMessage)
        if (state.errorMessage != null || state.successMessage != null) Spacer(Modifier.height(16.dp))
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            error = emailError,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.testTag(AuthTestTags.EMAIL),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryAuthButton(
            text = "Send Reset Link",
            onClick = { submit() },
            loading = state.isLoading,
            modifier = Modifier.testTag(AuthTestTags.PRIMARY_ACTION),
        )
        Spacer(Modifier.height(12.dp))
        SecondaryAuthButton(
            text = "Back to Sign In",
            onClick = onBackToSignIn,
            enabled = !state.isLoading,
        )
        Spacer(Modifier.height(24.dp))
    }
}
