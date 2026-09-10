package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DriveAlertSessionController {
    suspend fun disconnect(device: ProvisionedDevice): Result<Unit>
}

class HttpDriveAlertSessionController : DriveAlertSessionController {
    override suspend fun disconnect(device: ProvisionedDevice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(disconnectUrl(device.lastKnownIp)).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
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
        }
    }

    companion object {
        private const val CONTROL_PORT = 80
        private const val REQUEST_TIMEOUT_MS = 3_000
        internal fun disconnectUrl(ipAddress: String): String =
            "http://$ipAddress:$CONTROL_PORT/drivealert/disconnect"
    }
}
