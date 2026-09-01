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
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.lhordkent.drivealert.data.profile.NewUserProfile
import com.lhordkent.drivealert.data.profile.UserProfileRepository
import kotlinx.coroutines.launch

class AuthViewModel(
    private val userProfileRepository: UserProfileRepository? = null,
) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private var hasResolvedInitialSession = false
    private var pendingAuthenticationEntry: AuthenticationEntry? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val firebaseUser = firebaseAuth.currentUser
        val entry = when {
            firebaseUser == null -> AuthenticationEntry.NONE
            pendingAuthenticationEntry != null -> pendingAuthenticationEntry!!
            !hasResolvedInitialSession -> AuthenticationEntry.RESTORED
            else -> sessionState.entry
        }
        sessionState = AuthSessionState(
            isResolving = false,
            user = firebaseUser?.let {
                AuthenticatedUser(
                    uid = it.uid,
                    displayName = it.displayName.orEmpty(),
                    email = it.email.orEmpty(),
                )
            },
            entry = entry,
        )
        hasResolvedInitialSession = true
        if (firebaseUser != null) pendingAuthenticationEntry = null
    }

    var state by mutableStateOf(AuthOperationState())
        private set

    var sessionState by mutableStateOf(AuthSessionState())
        private set

    val callbacks = AuthCallbacks(
        onSignIn = ::signIn,
        onSignUp = ::signUp,
        onResetPassword = ::resetPassword,
        onSignOut = ::signOut,
    )

    init {
        auth.addAuthStateListener(authStateListener)
    }

    fun clearMessages() {
        state = state.copy(errorMessage = null, successMessage = null)
    }

    private fun signIn(email: String, password: String) {
        beginOperation()
        pendingAuthenticationEntry = AuthenticationEntry.FRESH
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                state = if (task.isSuccessful) {
                    AuthOperationState(successMessage = "Signed in successfully.")
                } else {
                    pendingAuthenticationEntry = null
                    AuthOperationState(errorMessage = task.exception.toAuthMessage(isSignIn = true))
                }
            }
    }

    private fun signUp(input: SignUpInput) {
        beginOperation()
        pendingAuthenticationEntry = AuthenticationEntry.ACCOUNT_CREATED
        auth.createUserWithEmailAndPassword(input.email, input.password)
            .addOnCompleteListener { createTask ->
                if (!createTask.isSuccessful) {
                    pendingAuthenticationEntry = null
                    state = AuthOperationState(errorMessage = createTask.exception.toAuthMessage())
                    return@addOnCompleteListener
                }

                val displayName = listOf(input.firstName, input.middleName, input.lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val firebaseProfile = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()

                auth.currentUser?.updateProfile(firebaseProfile)?.addOnCompleteListener { profileTask ->
                    auth.currentUser?.let { currentUser ->
                        sessionState = sessionState.copy(
                            user = AuthenticatedUser(
                                uid = currentUser.uid,
                                displayName = displayName,
                                email = currentUser.email.orEmpty(),
                            ),
                        )
                    }
                    persistCloudProfile(input, profileTask.isSuccessful)
                } ?: persistCloudProfile(input, firebaseDisplayNameSaved = false)
            }
    }

    private fun persistCloudProfile(input: SignUpInput, firebaseDisplayNameSaved: Boolean) {
        val currentUser = auth.currentUser
        val repository = userProfileRepository
        if (currentUser == null || repository == null) {
            state = AuthOperationState(
                successMessage = if (firebaseDisplayNameSaved) {
                    "Account created. You’re signed in."
                } else {
                    "Account created and signed in. Profile details can be completed later."
                },
            )
            return
        }
        viewModelScope.launch {
            state = try {
                repository.create(
                    NewUserProfile(
                        uid = currentUser.uid,
                        firstName = input.firstName,
                        middleName = input.middleName.ifBlank { null },
                        lastName = input.lastName,
                        email = currentUser.email ?: input.email,
                        phoneNumber = input.phoneNumber.ifBlank { null },
                    ),
                )
                AuthOperationState(successMessage = "Account created. Choose how you will use DriveAlert.")
            } catch (_: Exception) {
                AuthOperationState(
                    successMessage = "Account created and signed in.",
                    errorMessage = "Profile details could not be saved. Check your connection and try again later.",
                )
            }
        }
    }

    private fun resetPassword(email: String) {
        beginOperation()
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                state = if (task.isSuccessful) {
                    AuthOperationState(successMessage = "Password reset email sent. Check your inbox.")
                } else {
                    AuthOperationState(errorMessage = task.exception.toAuthMessage())
                }
            }
    }

    private fun beginOperation() {
        state = AuthOperationState(isLoading = true)
    }

    private fun signOut() {
        auth.signOut()
        pendingAuthenticationEntry = null
        state = AuthOperationState()
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        super.onCleared()
    }
}

class AuthViewModelFactory(
    private val userProfileRepository: UserProfileRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AuthViewModel::class.java))
        return AuthViewModel(userProfileRepository) as T
    }
}

private fun Throwable?.toAuthMessage(isSignIn: Boolean = false): String = when (this) {
    is FirebaseAuthInvalidCredentialsException,
    is FirebaseAuthInvalidUserException,
        -> if (isSignIn) "Email or password is incorrect." else "The email address is invalid."
    is FirebaseAuthUserCollisionException -> "An account already exists for this email."
    is FirebaseAuthWeakPasswordException -> reason ?: "Choose a stronger password."
    is FirebaseNetworkException -> "Check your internet connection and try again."
    is FirebaseTooManyRequestsException -> "Too many attempts. Wait a moment and try again."
    else -> "Authentication couldn’t be completed. Please try again."
}
