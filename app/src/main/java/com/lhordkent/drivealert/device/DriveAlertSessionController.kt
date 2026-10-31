package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.Socket

interface DriveAlertSessionController {
    suspend fun disconnect(device: ProvisionedDevice): Result<Unit>
    suspend fun heartbeat(device: ProvisionedDevice): Result<Boolean> = Result.success(false)
}

class HttpDriveAlertSessionController(private val controlPort: Int = CONTROL_PORT) : DriveAlertSessionController {
    override suspend fun heartbeat(device: ProvisionedDevice): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Result.success(request("http://${device.lastKnownIp}:$controlPort/drivealert/heartbeat").let { code ->
                // The app can be updated before the ESP32 is flashed.
                check(code in 200..299 || code == 404) { "Camera heartbeat rejected (HTTP $code)." }
                code in 200..299
            })
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (error: Exception) { Result.failure(error) }
    }

    override suspend fun disconnect(device: ProvisionedDevice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("http://${device.lastKnownIp}:$controlPort/drivealert/disconnect").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = REQUEST_TIMEOUT_MS
                connection.readTimeout = REQUEST_TIMEOUT_MS
                connection.useCaches = false
                connection.setFixedLengthStreamingMode(0)
                connection.connect()
                check(connection.responseCode in 200..299) {
                    "DriveAlert camera rejected the disconnect request."
                }
            } finally {
                connection.disconnect()
            }
            // An HTTP acknowledgement precedes the firmware restart. Confirm the
            // old camera control server has closed before exposing BLE setup.
            var stopped = false
            repeat(12) {
                if (!stopped) {
                    delay(250)
                    stopped = !runCatching { Socket().use { socket ->
                        socket.connect(InetSocketAddress(device.lastKnownIp, controlPort), 400)
                    } }.isSuccess
                }
            }
            check(stopped) { "The camera accepted the request but has not left Wi-Fi mode. Retry returning to Bluetooth." }
        }
    }

    private fun request(address: String): Int {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = REQUEST_TIMEOUT_MS
            connection.readTimeout = REQUEST_TIMEOUT_MS
            connection.useCaches = false
            connection.setFixedLengthStreamingMode(0)
            connection.responseCode
        } finally { connection.disconnect() }
    }

    companion object {
        private const val CONTROL_PORT = 80
        private const val REQUEST_TIMEOUT_MS = 3_000
        internal fun disconnectUrl(ipAddress: String): String =
            "http://$ipAddress:$CONTROL_PORT/drivealert/disconnect"
    }
}
