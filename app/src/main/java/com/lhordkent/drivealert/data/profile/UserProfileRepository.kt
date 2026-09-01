package com.lhordkent.drivealert.data.profile

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface UserProfileRepository {
    fun observe(uid: String): Flow<UserProfile?>
    suspend fun create(profile: NewUserProfile)
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
    )

    private fun Timestamp.toEpochMillis(): Long = seconds * 1_000L + nanoseconds / 1_000_000L

    companion object {
        const val USERS_COLLECTION = "users"
    }
}
