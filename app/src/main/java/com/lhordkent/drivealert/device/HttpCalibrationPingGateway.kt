package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** No retry: an ambiguous HTTP acknowledgement must not repeat calibration audio. */
internal class HttpCalibrationPingGateway(
    private val controlPort: Int = 80,
) {
    suspend fun play(device: ProvisionedDevice): Boolean = send(device, "calibration-ping", "accepted")
    suspend fun countdown(device: ProvisionedDevice): Boolean = send(device, "calibration-countdown", "started")

    private suspend fun send(device: ProvisionedDevice, path: String, status: String): Boolean = withContext(Dispatchers.IO) {
        val connection = URL("http://${device.lastKnownIp}:$controlPort/drivealert/$path").openConnection() as HttpURLConnection
        try {
            check(device.sessionToken != null) { "No authorized camera session." }
            connection.authorizeDevice(device.sessionToken)
            connection.requestMethod = "POST"
            connection.connectTimeout = 1_500
            connection.readTimeout = 1_500
            connection.useCaches = false
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(0)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext false
            connection.requireDeviceResponse(device.deviceId, device.sessionToken)
            connection.inputStream.bufferedReader().use { it.readText().trim() == "{\"status\":\"$status\"}" }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { false
        } finally { connection.disconnect() }
    }
}
