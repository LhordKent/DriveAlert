package com.drivealert.mvp.data

import com.google.firebase.auth.FirebaseAuth

interface AuthRepository {
    fun currentUserEmail(): String?
    fun signIn(email: String, password: String, callback: (Result<String>) -> Unit)
    fun logout()
}

class FirebaseAuthRepository : AuthRepository {
    private val auth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    override fun currentUserEmail(): String? = auth?.currentUser?.email

    override fun signIn(email: String, password: String, callback: (Result<String>) -> Unit) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            callback(Result.failure(IllegalStateException(CONFIGURATION_ERROR)))
            return
        }
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val signedInEmail = firebaseAuth.currentUser?.email ?: email
                callback(Result.success(signedInEmail))
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    override fun logout() {
        auth?.signOut()
    }

    private companion object {
        const val CONFIGURATION_ERROR =
            "Firebase is not configured. Add this project's google-services.json to app/."
    }
}
