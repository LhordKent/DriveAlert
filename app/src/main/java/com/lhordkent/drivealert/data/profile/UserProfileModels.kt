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
    val suffix: String? = null,
) {
    val fullName: String
        get() = formatProfileName(firstName, middleName, lastName, suffix)
}

data class NewUserProfile(
    val uid: String,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
    val email: String,
    val phoneNumber: String?,
    val suffix: String? = null,
)

data class UserProfileUpdate(
    val firstName: String,
    val middleName: String?,
    val lastName: String,
    val phoneNumber: String?,
) {
    fun fullName(suffix: String?): String = formatProfileName(firstName, middleName, lastName, suffix)
}

internal fun formatProfileName(firstName: String, middleName: String?, lastName: String, suffix: String?): String =
    listOfNotNull(firstName, middleName, lastName, suffix?.trim()).filter(String::isNotBlank).joinToString(" ")
