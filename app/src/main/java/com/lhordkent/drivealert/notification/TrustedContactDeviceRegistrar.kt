package com.lhordkent.drivealert.notification

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.lhordkent.drivealert.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TrustedContactDeviceRegistrar(
    context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val baseUrl: String = BuildConfig.NOTIFICATION_WORKER_URL,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    val installationId: String = preferences.getString(INSTALLATION_ID, null)
        ?: UUID.randomUUID().toString().also { preferences.edit().putString(INSTALLATION_ID, it).apply() }

    suspend fun registerCurrentDevice(fcmToken: String? = null): Boolean {
        if (baseUrl.isBlank()) return false
        val user = auth.currentUser ?: return false
        val idToken = user.getIdToken(false).await().token ?: return false
        val token = fcmToken ?: FirebaseMessaging.getInstance().token.await()
        val response = request(
            method = "PUT",
            path = "/v1/devices",
            idToken = idToken,
            body = JSONObject().put("installationId", installationId).put("fcmToken", token).toString(),
        )
        if (response == HttpURLConnection.HTTP_UNAUTHORIZED) {
            val refreshed = user.getIdToken(true).await().token ?: return false
            return request("PUT", "/v1/devices", refreshed, JSONObject()
                .put("installationId", installationId).put("fcmToken", token).toString()) in 200..299
        }
        return response in 200..299
    }

    suspend fun unregisterCurrentDevice(): Boolean {
        if (baseUrl.isBlank()) return true
        val user = auth.currentUser ?: return true
        val idToken = user.getIdToken(false).await().token ?: return false
        val response = request("DELETE", "/v1/devices/$installationId", idToken, null)
        if (response == HttpURLConnection.HTTP_UNAUTHORIZED) {
            val refreshed = user.getIdToken(true).await().token ?: return false
            return request("DELETE", "/v1/devices/$installationId", refreshed, null) in 200..299
        }
        return response in 200..299
    }

    private suspend fun request(method: String, path: String, idToken: String, body: String?): Int =
        withContext(Dispatchers.IO) {
            val connection = URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 5_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Authorization", "Bearer $idToken")
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        private const val PREFERENCES = "trusted_contact_device"
        private const val INSTALLATION_ID = "installation_id"
    }
}
