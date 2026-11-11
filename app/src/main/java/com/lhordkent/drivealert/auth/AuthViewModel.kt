package com.lhordkent.drivealert.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.lhordkent.drivealert.data.profile.UserProfileRepository
import com.lhordkent.drivealert.notification.TrustedContactDeviceRegistrar
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(
    private val userProfileRepository: UserProfileRepository? = null,
    private val deviceRegistrar: TrustedContactDeviceRegistrar? = null,
    private val pendingRegistrationStore: PendingRegistrationStore? = null,
) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private var pendingEntry: AuthenticationEntry? = null
    private var lastEmailSentAt = 0L
    private var signingUp = false
    var state by mutableStateOf(AuthOperationState())
        private set
    var sessionState by mutableStateOf(AuthSessionState())
        private set

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        when {
            user == null -> sessionState = AuthSessionState(isResolving = false)
            !user.isEmailVerified -> sessionState = AuthSessionState(isResolving = false, verificationEmail = user.email.orEmpty())
            !signingUp -> completeVerifiedSession()
        }
    }
    val callbacks = AuthCallbacks(
        onSignIn = ::signIn, onSignUp = ::signUp, onResetPassword = ::resetPassword, onSignOut = ::signOut,
        onCheckVerification = ::checkVerification, onResendVerification = { sendVerification() },
        onChangeVerificationEmail = { sendVerification(it.trim()) },
    )
    init { auth.addAuthStateListener(authStateListener) }
    fun clearMessages() { state = state.copy(errorMessage = null, successMessage = null) }

    private fun signIn(email: String, password: String) {
        if (state.isLoading) return
        state = AuthOperationState(isLoading = true)
        pendingEntry = AuthenticationEntry.FRESH
        viewModelScope.launch {
            try {
                val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
                if (user?.isEmailVerified != true) state = AuthOperationState()
            } catch (error: Exception) {
                pendingEntry = null
                state = AuthOperationState(errorMessage = error.toAuthMessage(isSignIn = true))
            }
        }
    }

    private fun signUp(input: SignUpInput) {
        if (state.isLoading) return
        state = AuthOperationState(isLoading = true)
        signingUp = true
        pendingEntry = AuthenticationEntry.ACCOUNT_CREATED
        viewModelScope.launch {
            try {
                val user = auth.createUserWithEmailAndPassword(input.email.trim(), input.password).await().user
                    ?: error("Account creation failed")
                pendingRegistrationStore?.save(user.uid, input)
                val displayName = listOf(input.firstName, input.middleName, input.lastName)
                    .filter(String::isNotBlank).joinToString(" ")
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build()).await()
                user.sendEmailVerification().await()
                lastEmailSentAt = android.os.SystemClock.elapsedRealtime()
                state = AuthOperationState(successMessage = "Verification email sent. Check your inbox and spam folder.")
            } catch (error: Exception) {
                state = AuthOperationState(errorMessage = error.toAuthMessage())
            } finally { signingUp = false }
        }
    }

    private fun completeVerifiedSession(forceTokenRefresh: Boolean = false) {
        val user = auth.currentUser ?: return
        if (!user.isEmailVerified) return
        viewModelScope.launch {
            try {
                val pendingProfile = pendingRegistrationStore?.read(user.uid, user.email.orEmpty())
                // Refresh after verification, while preserving offline restoration for verified drivers.
                if (forceTokenRefresh || pendingProfile != null) user.getIdToken(true).await()
                pendingProfile?.let { profile ->
                    userProfileRepository?.create(profile)
                    pendingRegistrationStore?.clear(user.uid)
                }
                if (auth.currentUser?.uid != user.uid) return@launch
                sessionState = AuthSessionState(isResolving = false,
                    user = AuthenticatedUser(user.uid, user.displayName.orEmpty(), user.email.orEmpty()),
                    entry = pendingEntry ?: AuthenticationEntry.RESTORED)
                pendingEntry = null
                state = AuthOperationState()
            } catch (error: Exception) {
                sessionState = AuthSessionState(isResolving = false, verificationEmail = user.email.orEmpty())
                state = AuthOperationState(errorMessage = "Could not finish account setup. Check your connection and try again.")
            }
        }
    }

    private fun checkVerification() {
        if (state.isLoading) return
        val user = auth.currentUser ?: return
        state = AuthOperationState(isLoading = true)
        viewModelScope.launch {
            try {
                user.reload().await()
                if (auth.currentUser?.uid != user.uid) return@launch
                if (user.isEmailVerified) completeVerifiedSession(forceTokenRefresh = true)
                else state = AuthOperationState(errorMessage = "Your email is not verified yet. Open the link in your inbox, then try again.")
            } catch (error: Exception) { state = AuthOperationState(errorMessage = error.toAuthMessage()) }
        }
    }

    private fun sendVerification(newEmail: String? = null) {
        if (state.isLoading) return
        val user = auth.currentUser ?: return
        if (newEmail != null) AuthValidation.emailError(newEmail)?.let {
            state = AuthOperationState(errorMessage = it)
            return
        }
        if (lastEmailSentAt != 0L && android.os.SystemClock.elapsedRealtime() - lastEmailSentAt < 60_000) {
            state = AuthOperationState(errorMessage = "Please wait a minute before requesting another email.")
            return
        }
        state = AuthOperationState(isLoading = true)
        viewModelScope.launch {
            try {
                if (newEmail == null) user.sendEmailVerification().await()
                else user.verifyBeforeUpdateEmail(newEmail).await()
                lastEmailSentAt = android.os.SystemClock.elapsedRealtime()
                state = AuthOperationState(successMessage = if (newEmail == null)
                    "Verification email sent. Check your inbox and spam folder."
                    else "Check $newEmail for a link to confirm the new address. Then return and check verification.")
            } catch (error: Exception) { state = AuthOperationState(errorMessage = error.toAuthMessage()) }
        }
    }

    private fun resetPassword(email: String) {
        if (state.isLoading) return
        state = AuthOperationState(isLoading = true)
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email.trim()).await()
                state = AuthOperationState(successMessage = "Password reset email sent. Check your inbox.")
            } catch (error: Exception) { state = AuthOperationState(errorMessage = error.toAuthMessage()) }
        }
    }
    private fun signOut() {
        if (state.isLoading) return
        viewModelScope.launch {
            runCatching { deviceRegistrar?.unregisterCurrentDevice() }
            auth.signOut()
            pendingEntry = null
            lastEmailSentAt = 0L
            state = AuthOperationState()
        }
    }
    override fun onCleared() { auth.removeAuthStateListener(authStateListener); super.onCleared() }
}

class AuthViewModelFactory(
    private val userProfileRepository: UserProfileRepository,
    private val deviceRegistrar: TrustedContactDeviceRegistrar? = null,
    private val pendingRegistrationStore: PendingRegistrationStore? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AuthViewModel::class.java))
        return AuthViewModel(userProfileRepository, deviceRegistrar, pendingRegistrationStore) as T
    }
}

private fun Throwable?.toAuthMessage(isSignIn: Boolean = false): String = when (this) {
    is FirebaseAuthInvalidCredentialsException,
    is FirebaseAuthInvalidUserException,
        -> if (isSignIn) "Email or password is incorrect." else "The email address is invalid."
    is FirebaseAuthUserCollisionException -> "An account already exists for this email."
    is FirebaseAuthWeakPasswordException -> reason ?: "Choose a stronger password."
    is FirebaseAuthRecentLoginRequiredException -> "Sign out and sign in again, then retry the email change."
    is FirebaseNetworkException -> "Check your internet connection and try again."
    is FirebaseTooManyRequestsException -> "Too many attempts. Wait a moment and try again."
    else -> "Authentication couldn’t be completed. Please try again."
}
