package com.lhordkent.drivealert.device

import org.junit.Assert.assertEquals
import org.junit.Assert.*
import org.junit.Test
import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

class DriveAlertSessionControllerTest {
    private val device = ProvisionedDevice("camera", "driver", "drivealert-test", "127.0.0.1", lastConnectedAtEpochMillis = 0)

    @Test fun `heartbeat supports new and unflashed firmware and reports rejection`() = runBlocking {
        for (code in listOf(200, 404, 503)) {
            val server = ServerSocket(0)
            var request = ""
            val worker = thread {
                server.use { it.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    request = reader.readLine()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().write("HTTP/1.1 $code Test\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                } }
            }
            val result = HttpDriveAlertSessionController(server.localPort).heartbeat(device)
            worker.join(4000)
            assertEquals("POST /drivealert/heartbeat HTTP/1.1", request)
            if (code == 503) assertTrue(result.isFailure) else assertEquals(code == 200, result.getOrThrow())
        }
    }

    @Test fun `disconnect waits for the acknowledged control server to close`() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        var request = ""
        val worker = thread {
            server.use { it.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                request = reader.readLine()
                while (!reader.readLine().isNullOrEmpty()) { }
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 13\r\nConnection: close\r\n\r\nDISCONNECTING".toByteArray())
            } }
        }
        HttpDriveAlertSessionController(port).disconnect(device).getOrThrow()
        worker.join(4000)
        assertEquals("POST /drivealert/disconnect HTTP/1.1", request)
        assertTrue(server.isClosed)
    }
    @Test
    fun `disconnect command targets the numeric control endpoint`() {
        assertEquals(
            "http://192.168.43.12:80/drivealert/disconnect",
            HttpDriveAlertSessionController.disconnectUrl("192.168.43.12"),
        )
    }
}
