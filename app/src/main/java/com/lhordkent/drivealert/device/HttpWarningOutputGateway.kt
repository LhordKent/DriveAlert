package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import com.lhordkent.drivealert.monitoring.WarningDeliveryStatus
import com.lhordkent.drivealert.monitoring.WarningOutputCommand
import com.lhordkent.drivealert.monitoring.WarningOutputGateway
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class HttpWarningOutputGateway internal constructor(
    private val deviceProvider: () -> ProvisionedDevice?,
    private val endpointResolver: DriveAlertEndpointResolver,
    private val onDeviceResolved: suspend (ProvisionedDevice) -> Unit = {},
    private val controlPort: Int = CONTROL_PORT,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) : WarningOutputGateway {
    override suspend fun activate(command: WarningOutputCommand): WarningDeliveryStatus {
        val device = deviceProvider() ?: return WarningDeliveryStatus.FAILED
        return withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
            runCatching {
                val resolved = endpointResolver.resolve(device) ?: error("DriveAlert device is unavailable.")
                if (resolved != device) onDeviceResolved(resolved)
                send(resolved.lastKnownIp, command)
            }.getOrElse { WarningDeliveryStatus.FAILED }
        } ?: WarningDeliveryStatus.FAILED
    }

    private suspend fun send(host: String, command: WarningOutputCommand): WarningDeliveryStatus =
        withContext(Dispatchers.IO) {
            val payload = command.toWarningWirePayload().toByteArray(Charsets.UTF_8)
            val connection = URL(warningUrl(host, controlPort)).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.useCaches = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", CONTENT_TYPE)
                connection.setRequestProperty("Accept", "application/json")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext WarningDeliveryStatus.FAILED
                }
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (response.trim() == ACCEPTED_RESPONSE) {
                    WarningDeliveryStatus.DELIVERED
                } else {
                    WarningDeliveryStatus.FAILED
                }
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        private const val CONTROL_PORT = 80
        private const val CONNECT_TIMEOUT_MS = 1_500
        private const val READ_TIMEOUT_MS = 1_500
        private const val OVERALL_TIMEOUT_MS = 4_000L
        const val WARNING_PATH = "/drivealert/warning"
        const val CONTENT_TYPE = "application/x-www-form-urlencoded; charset=utf-8"
        const val ACCEPTED_RESPONSE = "{\"status\":\"accepted\"}"

        internal fun warningUrl(host: String, port: Int = CONTROL_PORT): String =
            "http://$host:$port$WARNING_PATH"
    }
}

internal fun WarningOutputCommand.toWarningWirePayload(): String = buildString {
    append("stage=")
    append(stage.ordinal + 1)
    append("&sound=")
    append(sound.name)
    append("&volume=")
    append(volume.name)
}
