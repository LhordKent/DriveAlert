package com.lhordkent.drivealert.data.profile

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lhordkent.drivealert.data.connection.ConnectionCode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface UserProfileRepository {
    fun observe(uid: String): Flow<UserProfile?>
    suspend fun create(profile: NewUserProfile)
    suspend fun ensureProfile(uid: String, displayName: String, email: String): String
    suspend fun selectRole(uid: String, selectedRole: UserRole): UserRole
}

class FirestoreUserProfileRepository(
    private val firestore: FirebaseFirestore,
) : UserProfileRepository {
    private val users = firestore.collection(USERS_COLLECTION)

    override fun observe(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = users.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.takeIf(DocumentSnapshot::exists)?.toProfile())
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun create(profile: NewUserProfile) {
        val values = mutableMapOf<String, Any?>(
            "uid" to profile.uid,
            "firstName" to profile.firstName,
            "lastName" to profile.lastName,
            "email" to profile.email,
            "accountStatus" to AccountStatus.ACTIVE.name,
            "registeredAt" to FieldValue.serverTimestamp(),
            "deactivatedAt" to null,
        )
        profile.middleName?.takeIf(String::isNotBlank)?.let { values["middleName"] = it }
        profile.phoneNumber?.takeIf(String::isNotBlank)?.let { values["phoneNumber"] = it }
        users.document(profile.uid).set(values, SetOptions.merge()).await()
        ensureProfile(profile.uid, profile.firstName + " " + profile.lastName, profile.email)
    }

    override suspend fun ensureProfile(uid: String, displayName: String, email: String): String {
        repeat(MAX_CODE_ATTEMPTS) {
            val proposedCode = ConnectionCode.generate()
            try {
                return firestore.runTransaction { transaction ->
                    val userReference = users.document(uid)
                    val userSnapshot = transaction.get(userReference)
                    val existingCode = userSnapshot.getString("connectionCode")
                        ?.let(ConnectionCode::normalize)
                        ?.takeIf(ConnectionCode::isValid)
                    val code = existingCode ?: proposedCode
                    val codeReference = firestore.collection(CONNECTION_CODES_COLLECTION).document(code)
                    val codeSnapshot = transaction.get(codeReference)
                    check(!codeSnapshot.exists() || codeSnapshot.getString("ownerUserId") == uid) {
                        CODE_COLLISION_MESSAGE
                    }

                    val fallbackName = displayName.trim().ifBlank {
                        email.substringBefore('@').replace('.', ' ').replaceFirstChar(Char::uppercase)
                    }
                    val profileValues = mutableMapOf<String, Any?>(
                        "uid" to uid,
                        "email" to email.trim().lowercase(),
                        "connectionCode" to code,
                    )
                    if (!userSnapshot.exists()) {
                        profileValues["accountStatus"] = AccountStatus.ACTIVE.name
                        profileValues["firstName"] = fallbackName
                        profileValues["lastName"] = ""
                        profileValues["registeredAt"] = FieldValue.serverTimestamp()
                        profileValues["deactivatedAt"] = null
                    }
                    transaction.set(userReference, profileValues, SetOptions.merge())
                    val codeValues = mutableMapOf<String, Any?>(
                        "ownerUserId" to uid,
                        "displayName" to userSnapshot.toDisplayName().ifBlank { fallbackName },
                        "active" to true,
                    )
                    if (!codeSnapshot.exists()) codeValues["createdAt"] = FieldValue.serverTimestamp()
                    transaction.set(codeReference, codeValues, SetOptions.merge())
                    code
                }.await()
            } catch (error: IllegalStateException) {
                if (error.message != CODE_COLLISION_MESSAGE) throw error
            }
        }
        error("A unique connection code could not be created. Please try again.")
    }

    override suspend fun selectRole(uid: String, selectedRole: UserRole): UserRole {
        require(selectedRole != UserRole.BOTH) { "BOTH is derived after both capabilities are used." }
        return firestore.runTransaction { transaction ->
            val reference = users.document(uid)
            val current = transaction.get(reference).getString("userRole")?.let(UserRole::valueOf)
            val next = when {
                current == null -> selectedRole
                current == selectedRole -> current
                else -> UserRole.BOTH
            }
            transaction.set(reference, mapOf("uid" to uid, "userRole" to next.name), SetOptions.merge())
            next
        }.await()
    }

    private fun DocumentSnapshot.toProfile(): UserProfile = UserProfile(
        uid = getString("uid") ?: id,
        firstName = getString("firstName").orEmpty(),
        middleName = getString("middleName"),
        lastName = getString("lastName").orEmpty(),
        email = getString("email").orEmpty(),
        phoneNumber = getString("phoneNumber"),
        userRole = getString("userRole")?.let(UserRole::valueOf),
        accountStatus = getString("accountStatus")?.let(AccountStatus::valueOf) ?: AccountStatus.ACTIVE,
        registeredAtEpochMillis = getTimestamp("registeredAt")?.toEpochMillis(),
        deactivatedAtEpochMillis = getTimestamp("deactivatedAt")?.toEpochMillis(),
        connectionCode = getString("connectionCode").orEmpty(),
    )

    private fun DocumentSnapshot.toDisplayName(): String =
        listOf(getString("firstName").orEmpty(), getString("middleName").orEmpty(), getString("lastName").orEmpty())
            .filter(String::isNotBlank)
            .joinToString(" ")

    private fun Timestamp.toEpochMillis(): Long = seconds * 1_000L + nanoseconds / 1_000_000L

    companion object {
        const val USERS_COLLECTION = "users"
        const val CONNECTION_CODES_COLLECTION = "connectionCodes"
        private const val MAX_CODE_ATTEMPTS = 5
        private const val CODE_COLLISION_MESSAGE = "connection-code-collision"
    }
}
