package com.lhordkent.drivealert.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private var hasResolvedInitialSession = false
    private var freshAuthenticationPending = false

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val firebaseUser = firebaseAuth.currentUser
        val entry = when {
            firebaseUser == null -> AuthenticationEntry.NONE
            freshAuthenticationPending -> AuthenticationEntry.FRESH
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
        if (firebaseUser != null) freshAuthenticationPending = false
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
        freshAuthenticationPending = true
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                state = if (task.isSuccessful) {
                    AuthOperationState(successMessage = "Signed in successfully.")
                } else {
                    freshAuthenticationPending = false
                    AuthOperationState(errorMessage = task.exception.toAuthMessage(isSignIn = true))
                }
            }
    }

    private fun signUp(input: SignUpInput) {
        beginOperation()
        freshAuthenticationPending = true
        auth.createUserWithEmailAndPassword(input.email, input.password)
            .addOnCompleteListener { createTask ->
                if (!createTask.isSuccessful) {
                    freshAuthenticationPending = false
                    state = AuthOperationState(errorMessage = createTask.exception.toAuthMessage())
                    return@addOnCompleteListener
                }

                val displayName = listOf(input.firstName, input.middleInitial, input.lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()

                auth.currentUser?.updateProfile(profile)?.addOnCompleteListener { profileTask ->
                    auth.currentUser?.let { currentUser ->
                        sessionState = sessionState.copy(
                            user = AuthenticatedUser(
                                uid = currentUser.uid,
                                displayName = currentUser.displayName.orEmpty(),
                                email = currentUser.email.orEmpty(),
                            ),
                        )
                    }
                    state = if (profileTask.isSuccessful) {
                        AuthOperationState(successMessage = "Account created. You’re signed in.")
                    } else {
                        AuthOperationState(
                            successMessage = "Account created and signed in. Profile details can be completed later.",
                        )
                    }
                } ?: run {
                    state = AuthOperationState(successMessage = "Account created. You’re signed in.")
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
        freshAuthenticationPending = false
        state = AuthOperationState()
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authStateListener)
        super.onCleared()
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
