package com.lhordkent.drivealert.data.connection

import com.google.firebase.auth.FirebaseAuth
import com.lhordkent.drivealert.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class TrustedContactEmailMatch(val displayName: String, val email: String, val matchId: String)
data class ConnectionContactDetails(val email: String?, val phoneNumber: String?)
class ResolvedDeviceDriver(val userId: String, val connectionCode: String)

class TrustedContactEmailException(val status: String) : Exception(status)

interface TrustedContactEmailDirectory {
    suspend fun lookup(driverUid: String, email: String): TrustedContactEmailMatch
    suspend fun invite(driverUid: String, matchId: String)
}

class CloudflareTrustedContactEmailDirectory(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val baseUrl: String = BuildConfig.NOTIFICATION_WORKER_URL,
) : TrustedContactEmailDirectory {
    override suspend fun lookup(driverUid: String, email: String): TrustedContactEmailMatch {
        val result = request(driverUid, "/v1/trusted-contact-email-lookup", JSONObject().put("email", email), "EMAIL_MATCH")
        return TrustedContactEmailMatch(
            displayName = result.getString("displayName"),
            email = result.getString("email"),
            matchId = result.getString("matchId"),
        )
    }

    override suspend fun invite(driverUid: String, matchId: String) {
        request(driverUid, "/v1/trusted-contact-email-invites", JSONObject().put("matchId", matchId), "INVITATION_SENT")
    }

    suspend fun lookupDeviceDriver(ownerUid: String, deviceId: String, email: String): TrustedContactEmailMatch {
        val body = JSONObject().put("deviceId", deviceId).put("email", email)
        val result = request(ownerUid, "/v1/device-driver-email-lookup", body, "EMAIL_MATCH")
        return TrustedContactEmailMatch(result.getString("displayName"), result.getString("email"), result.getString("matchId"))
    }

    suspend fun resolveDeviceDriver(ownerUid: String, deviceId: String, matchId: String): ResolvedDeviceDriver {
        val body = JSONObject().put("deviceId", deviceId).put("matchId", matchId)
        val result = request(ownerUid, "/v1/device-driver-email-target", body, "DEVICE_INVITE_TARGET")
        return ResolvedDeviceDriver(result.getString("targetUid"), result.getString("connectionCode"))
    }

    suspend fun deviceDriverPhoneNumbers(ownerUid: String, accessIds: List<String>): Map<String, String> {
        val numbers = mutableMapOf<String, String>()
        for (batch in accessIds.chunked(20)) {
            val body = JSONObject().put("accessIds", JSONArray(batch))
            val rows = request(ownerUid, "/v1/device-driver-details", body).getJSONArray("details")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val id = row.getString("accessId")
                if (id in batch && !row.isNull("phoneNumber")) numbers[id] = row.getString("phoneNumber")
            }
        }
        return numbers
    }

    suspend fun connectionContactDetails(userId: String, connectionIds: List<String>): Map<String, ConnectionContactDetails> {
        val details = mutableMapOf<String, ConnectionContactDetails>()
        for (batch in connectionIds.chunked(20)) {
            val body = JSONObject().put("connectionIds", JSONArray(batch))
            val rows = request(userId, "/v1/connection-contact-details", body).getJSONArray("details")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val id = row.getString("connectionId")
                if (id in batch) {
                    details[id] = ConnectionContactDetails(
                        email = if (row.isNull("email")) null else row.getString("email"),
                        phoneNumber = if (row.isNull("phoneNumber")) null else row.getString("phoneNumber"),
                    )
                }
            }
        }
        return details
    }

    private suspend fun request(driverUid: String, path: String, body: JSONObject, expectedStatus: String? = null): JSONObject {
        if (baseUrl.isBlank()) throw TrustedContactEmailException("EMAIL_LOOKUP_UNAVAILABLE")
        val user = auth.currentUser?.takeIf { it.uid == driverUid }
            ?: throw TrustedContactEmailException("DRIVER_REQUIRED")
        val token = user.getIdToken(false).await().token ?: throw TrustedContactEmailException("AUTH_REQUIRED")
        var response = send(path, token, body)
        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED && auth.currentUser?.uid == driverUid) {
            val refreshed = user.getIdToken(true).await().token ?: throw TrustedContactEmailException("AUTH_REQUIRED")
            response = send(path, refreshed, body)
        }
        val status = response.body.optString("status")
        if (response.code != HttpURLConnection.HTTP_OK || (expectedStatus != null && status != expectedStatus)) {
            throw TrustedContactEmailException(status.ifBlank { "EMAIL_LOOKUP_UNAVAILABLE" })
        }
        return response.body
    }

    private suspend fun send(path: String, token: String, body: JSONObject): Response = withContext(Dispatchers.IO) {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val connection = URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(code, runCatching { JSONObject(text) }.getOrElse { JSONObject() })
        } finally {
            connection.disconnect()
        }
    }

    private data class Response(val code: Int, val body: JSONObject)
}
