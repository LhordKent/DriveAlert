package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class HttpCalibrationPingGatewayTest {
    @Test fun `authenticated completion uses dedicated endpoint and accepts matching firmware`() = runBlocking {
        assertTrue(request("200 OK", true, "{\"status\":\"accepted\"}"))
    }

    @Test fun `busy unauthorized missing identity and malformed responses fail without retries`() = runBlocking {
        assertFalse(request("503 Service Unavailable", true, "{}"))
        assertFalse(request("401 Unauthorized", true, "{}"))
        assertFalse(request("200 OK", false, "{\"status\":\"accepted\"}"))
        assertFalse(request("200 OK", true, "{}"))
    }

    @Test fun `countdown requires started acknowledgement from authenticated firmware`() = runBlocking {
        assertTrue(request("200 OK", true, "{\"status\":\"started\"}", countdown = true))
        assertFalse(request("200 OK", true, "{\"status\":\"accepted\"}", countdown = true))
        assertFalse(request("503 Service Unavailable", true, "{}", countdown = true))
    }

    private suspend fun request(status: String, identity: Boolean, body: String, countdown: Boolean = false): Boolean {
        val device = ProvisionedDevice("AABBCCDDEEFF", "driver", "camera", "127.0.0.1", lastConnectedAtEpochMillis = 1, sessionToken = "A".repeat(64))
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            var received: String? = null
            val worker = thread {
                server.accept().use { socket ->
                    socket.soTimeout = 3_000
                    val reader = socket.getInputStream().bufferedReader()
                    val lines = mutableListOf<String>()
                    while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break; lines += line }
                    received = lines.joinToString("\n")
                    val headers = if (identity) "X-DriveAlert-Device: AABBCCDDEEFF\r\nX-DriveAlert-Auth: 1\r\n" else ""
                    socket.getOutputStream().write("HTTP/1.1 $status\r\n${headers}Content-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
                }
            }
            val gateway = HttpCalibrationPingGateway(server.localPort)
            val result = if (countdown) gateway.countdown(device) else gateway.play(device)
            worker.join(4_000)
            assertFalse(worker.isAlive)
            assertTrue(received.orEmpty().startsWith("POST /drivealert/calibration-${if (countdown) "countdown" else "ping"} HTTP/1.1"))
            assertTrue(received.orEmpty().contains("Authorization: Bearer ${device.sessionToken}"))
            return result
        }
    }
}
