package com.lhordkent.drivealert.data.sync

import com.google.firebase.auth.FirebaseAuth
import com.lhordkent.drivealert.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CloudflareStageNotificationRemoteDataSource(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val baseUrl: String = BuildConfig.NOTIFICATION_WORKER_URL,
) : StageNotificationRemoteDataSource {
    override suspend fun dispatch(chunk: NotificationDispatchChunk): NotificationDispatchResult {
        if (baseUrl.isBlank()) return NotificationDispatchResult.Disabled
        val user = auth.currentUser ?: return NotificationDispatchResult.Retryable("AUTH_UNAVAILABLE")
        var token = user.getIdToken(false).await().token
            ?: return NotificationDispatchResult.Retryable("AUTH_TOKEN_UNAVAILABLE")
        var response = send(token, chunk)
        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            token = user.getIdToken(true).await().token
                ?: return NotificationDispatchResult.Retryable("AUTH_TOKEN_UNAVAILABLE")
            response = send(token, chunk)
        }
        return when (response.code) {
            HttpURLConnection.HTTP_OK -> if (response.status == "NO_RECIPIENTS") {
                NotificationDispatchResult.Terminal("NO_RECIPIENTS")
            } else NotificationDispatchResult.Delivered
            HttpURLConnection.HTTP_ACCEPTED -> NotificationDispatchResult.AwaitingMore
            HttpURLConnection.HTTP_BAD_REQUEST, HttpURLConnection.HTTP_FORBIDDEN,
            HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_CONFLICT ->
                NotificationDispatchResult.Terminal(response.status.ifBlank { "REJECTED_${response.code}" })
            HttpURLConnection.HTTP_UNAUTHORIZED, 429 ->
                NotificationDispatchResult.Retryable(response.status.ifBlank { "HTTP_${response.code}" })
            else -> NotificationDispatchResult.Retryable(response.status.ifBlank { "HTTP_${response.code}" })
        }
    }

    private suspend fun send(token: String, chunk: NotificationDispatchChunk): Response = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("dispatchGroupId", chunk.dispatchGroupId)
            .put("batchId", chunk.batchId)
            .put("chunkIndex", chunk.chunkIndex)
            .put("chunkCount", chunk.chunkCount)
            .put("recordIds", JSONArray(chunk.recordIds))
            .toString()
            .toByteArray(Charsets.UTF_8)
        val connection = URL("${baseUrl.trimEnd('/')}/v1/stage3-notifications").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val text = runCatching {
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }.getOrDefault("")
            val status = runCatching { JSONObject(text).optString("status") }.getOrDefault("")
            Response(code, status)
        } finally {
            connection.disconnect()
        }
    }

    private data class Response(val code: Int, val status: String)
}
