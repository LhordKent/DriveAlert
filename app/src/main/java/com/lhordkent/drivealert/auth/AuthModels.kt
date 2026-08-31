package com.lhordkent.drivealert.auth

object AuthRoutes {
    const val STARTER = "starter"
    const val SIGN_IN = "sign-in"
    const val CREATE_ACCOUNT = "create-account"
    const val FORGOT_PASSWORD = "forgot-password"
}

data class SignUpInput(
    val firstName: String,
    val middleInitial: String,
    val lastName: String,
    val phoneNumber: String,
    val email: String,
    val password: String,
    val confirmPassword: String,
)

data class AuthCallbacks(
    val onSignIn: (email: String, password: String) -> Unit = { _, _ -> },
    val onSignUp: (input: SignUpInput) -> Unit = {},
    val onResetPassword: (email: String) -> Unit = {},
    val onSignOut: () -> Unit = {},
)

data class AuthOperationState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

enum class AuthenticationEntry { NONE, FRESH, RESTORED }

data class AuthenticatedUser(
    val uid: String,
    val displayName: String,
    val email: String,
)

data class AuthSessionState(
    val isResolving: Boolean = true,
    val user: AuthenticatedUser? = null,
    val entry: AuthenticationEntry = AuthenticationEntry.NONE,
)

object AuthValidation {
    private val emailPattern = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")
    private val phonePattern = Regex("^[+() 0-9-]{7,20}$")

    fun requiredError(value: String, fieldName: String): String? =
        if (value.isBlank()) "$fieldName is required" else null

    fun emailError(value: String): String? = when {
        value.isBlank() -> "Email is required"
        !emailPattern.matches(value.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun signInPasswordError(value: String): String? =
        if (value.isBlank()) "Password is required" else null

    fun signUpPasswordError(value: String): String? = when {
        value.isBlank() -> "Password is required"
        value.length < 8 -> "Use at least 8 characters"
        else -> null
    }

    fun confirmationError(password: String, confirmation: String): String? = when {
        confirmation.isBlank() -> "Confirm your password"
        password != confirmation -> "Passwords do not match"
        else -> null
    }

    fun middleInitialError(value: String): String? =
        if (value.trim().length > 1) "Use one character" else null

    fun phoneError(value: String): String? = when {
        value.isBlank() -> null
        !phonePattern.matches(value.trim()) -> "Enter a valid phone number"
        else -> null
    }
}
