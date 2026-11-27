package com.lhordkent.drivealert.auth

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.lhordkent.drivealert.data.profile.FirestoreUserProfileRepository
import com.lhordkent.drivealert.data.profile.UserProfileUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class RegistrationBackendTest {
    @Test
    fun verificationCreatesProfilesWithAndWithoutSuffixAndProfileEditsPreserveIt() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Requires the isolated Android and Firebase emulators",
            InstrumentationRegistry.getArguments().getString("auditSerial") == "emulator-5556"
                && Build.HARDWARE == "ranchu",
        )
        val auth = FirebaseAuth.getInstance().apply { useEmulator("10.0.2.2", 9195) }
        val firestore = FirebaseFirestore.getInstance().apply { useEmulator("10.0.2.2", 8185) }
        val profiles = FirestoreUserProfileRepository(firestore)
        val context = instrumentation.targetContext
        val preferencesName = "registration-backend-test"
        val pending = PendingRegistrationStore(context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE))
        val factory = AuthViewModelFactory(profiles, pendingRegistrationStore = pending)
        val store = ViewModelStore()
        auth.signOut()

        try {
            for ((enteredSuffix, savedSuffix) in listOf(" 3rd " to "3rd", " " to null)) {
                val email = "android-signup-${UUID.randomUUID()}@example.test"
                val signup = SignUpInput("Mára", "Anne-Marie", "O'Neil", "12345678901", email, "Safe-drive", "Safe-drive", enteredSuffix)
                val viewModel = withContext(Dispatchers.Main) {
                    ViewModelProvider(store, factory)[AuthViewModel::class.java]
                }
                withContext(Dispatchers.Main) {
                    viewModel.callbacks.onSignUp(signup.copy(password = "safe-drive", confirmPassword = "safe-drive"))
                    assertEquals(AuthValidation.SIGN_UP_PASSWORD_REQUIREMENTS, viewModel.state.errorMessage)
                    assertNull(auth.currentUser)
                    viewModel.callbacks.onSignUp(signup)
                }
                viewModel.awaitState { state.successMessage?.startsWith("Verification email sent") == true }
                val user = checkNotNull(auth.currentUser)
                assertFalse(user.isEmailVerified)
                assertEquals(savedSuffix, checkNotNull(pending.read(user.uid, email)).suffix)

                val restored = withContext(Dispatchers.Main) {
                    store.clear()
                    ViewModelProvider(store, factory)[AuthViewModel::class.java]
                }
                auth.applyActionCode(emailVerificationCode(email)).await()
                withContext(Dispatchers.Main) { restored.callbacks.onCheckVerification() }
                restored.awaitState { sessionState.user?.uid == user.uid }

                val profileRef = firestore.collection("users").document(user.uid)
                val profile = profileRef.get(Source.SERVER).await()
                assertEquals("O'Neil", profile.getString("lastName"))
                assertEquals(savedSuffix, profile.getString("suffix"))
                assertEquals(savedSuffix != null, profile.contains("suffix"))
                assertNull(pending.read(user.uid, email))
                val code = checkNotNull(profile.getString("connectionCode"))
                val codeRef = firestore.collection("connectionCodes").document(code)
                assertEquals(
                    listOfNotNull("Mára Anne-Marie O'Neil", savedSuffix).joinToString(" "),
                    codeRef.get(Source.SERVER).await().getString("displayName"),
                )

                profiles.update(user.uid, UserProfileUpdate("Mára", "Anne-Marie", "Reyes", "12345678901"))
                assertEquals(savedSuffix, profileRef.get(Source.SERVER).await().getString("suffix"))
                assertEquals(
                    listOfNotNull("Mára Anne-Marie Reyes", savedSuffix).joinToString(" "),
                    codeRef.get(Source.SERVER).await().getString("displayName"),
                )
                withContext(Dispatchers.Main) { store.clear() }
                auth.signOut()
            }
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            auth.signOut()
            context.deleteSharedPreferences(preferencesName)
        }
    }

    private suspend fun AuthViewModel.awaitState(isReady: AuthViewModel.() -> Boolean) {
        withTimeout(25_000) {
            while (!withContext(Dispatchers.Main) {
                assertNull(state.errorMessage)
                isReady()
            }) {
                delay(100)
            }
        }
    }

    private fun emailVerificationCode(email: String): String {
        val url = URL("http://10.0.2.2:9195/emulator/v1/projects/drivealert-lhordkent/oobCodes")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            assertEquals(200, connection.responseCode)
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val codes = JSONObject(response).getJSONArray("oobCodes")
            val code = (0 until codes.length()).map(codes::getJSONObject).lastOrNull {
                it.getString("email") == email && it.getString("requestType") == "VERIFY_EMAIL"
            }
            assertNotNull("Firebase emulator did not generate a verification code", code)
            return checkNotNull(code).getString("oobCode")
        } finally {
            connection.disconnect()
        }
    }
}
