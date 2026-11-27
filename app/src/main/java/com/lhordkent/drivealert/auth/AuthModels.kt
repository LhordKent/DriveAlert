package com.lhordkent.drivealert.auth

object AuthRoutes {
    const val STARTER = "starter"
    const val SIGN_IN = "sign-in"
    const val CREATE_ACCOUNT = "create-account"
    const val FORGOT_PASSWORD = "forgot-password"
}

data class SignUpInput(
    val firstName: String,
    val middleName: String,
    val lastName: String,
    val phoneNumber: String,
    val email: String,
    val password: String,
    val confirmPassword: String,
    val suffix: String = "",
)

data class AuthCallbacks(
    val onSignIn: (email: String, password: String) -> Unit = { _, _ -> },
    val onSignUp: (input: SignUpInput) -> Unit = {},
    val onResetPassword: (email: String) -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onCheckVerification: () -> Unit = {},
    val onResendVerification: () -> Unit = {},
    val onChangeVerificationEmail: (String) -> Unit = {},
)

data class AuthOperationState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
)

enum class AuthenticationEntry { NONE, FRESH, ACCOUNT_CREATED, RESTORED }

data class AuthenticatedUser(
    val uid: String,
    val displayName: String,
    val email: String,
)

data class AuthSessionState(
    val isResolving: Boolean = true,
    val user: AuthenticatedUser? = null,
    val entry: AuthenticationEntry = AuthenticationEntry.NONE,
    val verificationEmail: String? = null,
)

object AuthValidation {
    const val SIGN_UP_PASSWORD_REQUIREMENTS =
        "Use at least 8 characters, including an uppercase letter, a lowercase letter, and a special character."

    private val emailPattern = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")
    private val phonePattern = Regex("^[0-9]{11}$")
    private val nameNumberPattern = Regex("\\p{N}")
    private const val firebaseSpecialCharacters = "^$*.[]{}()?\"!@#%&/\\,><':;|_~`-"

    fun removeNameNumbers(value: String): String = value.replace(nameNumberPattern, "")

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
        value.length < 8 ||
            value.none { it in 'A'..'Z' } ||
            value.none { it in 'a'..'z' } ||
            value.none { it in firebaseSpecialCharacters } -> SIGN_UP_PASSWORD_REQUIREMENTS
        else -> null
    }

    fun confirmationError(password: String, confirmation: String): String? = when {
        confirmation.isBlank() -> "Confirm your password"
        password != confirmation -> "Passwords do not match"
        else -> null
    }

    fun phoneError(value: String): String? = when {
        value.isBlank() -> "Phone number is required"
        !phonePattern.matches(value.trim()) -> "Enter exactly 11 digits"
        else -> null
    }
}
