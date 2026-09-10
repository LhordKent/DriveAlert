package com.lhordkent.drivealert.data.profile

enum class UserRole {
    DRIVER,
    TRUSTED_CONTACT,
    BOTH,
}

enum class AccountStatus {
    ACTIVE,
    DEACTIVATED,
}

data class UserProfile(
    val uid: String,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
    val email: String,
    val phoneNumber: String?,
    val userRole: UserRole?,
    val accountStatus: AccountStatus,
    val registeredAtEpochMillis: Long?,
    val deactivatedAtEpochMillis: Long?,
    val connectionCode: String,
) {
    val fullName: String
        get() = listOf(firstName, middleName.orEmpty(), lastName).filter(String::isNotBlank).joinToString(" ")
}

data class NewUserProfile(
    val uid: String,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
    val email: String,
    val phoneNumber: String?,
)
