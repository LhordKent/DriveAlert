package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import com.lhordkent.drivealert.monitoring.VisibilityIssue
import com.lhordkent.drivealert.monitoring.VisibilityOutputCommand
import com.lhordkent.drivealert.monitoring.WarningDeliveryStatus
import com.lhordkent.drivealert.postauth.PreferredVolume
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpVisibilityOutputGatewayTest {
    @Test
    fun `regional issues map to the three dedicated speech intents`() {
        assertEquals("issue=EYES&volume=MEDIUM", command(VisibilityIssue.EYE_REGION_OBSTRUCTED).toVisibilityWirePayload())
        assertEquals("issue=MOUTH&volume=MEDIUM", command(VisibilityIssue.LOWER_FACE_OBSTRUCTED).toVisibilityWirePayload())
        assertEquals("issue=FACE&volume=MEDIUM", command(VisibilityIssue.BOTH_REGIONS_OBSTRUCTED).toVisibilityWirePayload())
        assertEquals("issue=FACE&volume=MEDIUM", command(VisibilityIssue.FACE_UNAVAILABLE).toVisibilityWirePayload())
    }

    @Test
    fun `visibility URL uses its own endpoint`() {
        assertEquals(
            "http://192.168.43.12:80/drivealert/visibility",
            HttpVisibilityOutputGateway.visibilityUrl("192.168.43.12"),
        )
    }

    @Test
    fun `accepted response delivers exact visibility payload`() = runBlocking {
        val request = AtomicReference<String>()
        withServer(request) { port ->
            val gateway = gateway(port)
            assertEquals(
                WarningDeliveryStatus.DELIVERED,
                gateway.activate(command(VisibilityIssue.EYE_REGION_OBSTRUCTED)),
            )
            assertEquals("issue=EYES&volume=MEDIUM", request.get())
        }
    }

    @Test
    fun `missing device fails without endpoint resolution`() = runBlocking {
        var calls = 0
        val gateway = HttpVisibilityOutputGateway(
            deviceProvider = { null },
            endpointResolver = object : DriveAlertEndpointResolver {
                override suspend fun resolve(device: ProvisionedDevice): ProvisionedDevice? {
                    calls++
                    return device
                }
            },
        )
        assertEquals(WarningDeliveryStatus.FAILED, gateway.activate(command(VisibilityIssue.FACE_UNAVAILABLE)))
        assertEquals(0, calls)
    }

    private fun command(issue: VisibilityIssue) = VisibilityOutputCommand(issue, PreferredVolume.MEDIUM)

    private fun gateway(port: Int): HttpVisibilityOutputGateway {
        val device = ProvisionedDevice(
            deviceId = "camera",
            driverUserId = "driver",
            hostname = "drivealert-test",
            lastKnownIp = "127.0.0.1",
            lastConnectedAtEpochMillis = 1,
        )
        return HttpVisibilityOutputGateway(
            deviceProvider = { device },
            endpointResolver = object : DriveAlertEndpointResolver {
                override suspend fun resolve(device: ProvisionedDevice) = device
            },
            controlPort = port,
            connectTimeoutMs = 200,
            readTimeoutMs = 200,
        )
    }

    private suspend fun withServer(request: AtomicReference<String>, block: suspend (Int) -> Unit) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val thread = Thread {
            runCatching {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                    assertEquals("POST ${HttpVisibilityOutputGateway.VISIBILITY_PATH} HTTP/1.1", reader.readLine())
                    var length = 0
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                    }
                    val body = CharArray(length)
                    var offset = 0
                    while (offset < length) {
                        val count = reader.read(body, offset, length - offset)
                        if (count < 0) break
                        offset += count
                    }
                    request.set(String(body, 0, offset))
                    val response = HttpVisibilityOutputGateway.ACCEPTED_RESPONSE.toByteArray()
                    val headers = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().use {
                        it.write(headers.toByteArray())
                        it.write(response)
                    }
                }
            }
        }
        thread.isDaemon = true
        thread.start()
        try {
            block(server.localPort)
        } finally {
            server.close()
            thread.join(1_000)
        }
    }
}
