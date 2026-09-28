package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import com.lhordkent.drivealert.monitoring.WarningDeliveryStatus
import com.lhordkent.drivealert.monitoring.warningOutputCommand
import com.lhordkent.drivealert.postauth.PreferredVolume
import com.lhordkent.drivealert.postauth.WarningSound
import com.lhordkent.drivealert.postauth.WarningStage
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpWarningOutputGatewayTest {
    @Test
    fun `all sound identifiers serialize without physical track numbers`() {
        WarningSound.values().forEach { sound ->
            val payload = WarningStage.STAGE_1.warningOutputCommand(sound, PreferredVolume.MEDIUM)
                .toWarningWirePayload()
            assertTrue(payload.contains("sound=${sound.name}"))
        }
    }

    @Test
    fun `all logical volume identifiers serialize unchanged`() {
        PreferredVolume.values().forEach { volume ->
            val payload = WarningStage.STAGE_1.warningOutputCommand(WarningSound.DIGITAL_BEEP, volume)
                .toWarningWirePayload()
            assertTrue(payload.contains("volume=${volume.name}"))
        }
    }

    @Test
    fun `stage intent serializes without advisory text or physical track numbers`() {
        WarningStage.values().forEach { stage ->
            val payload = stage.command().toWarningWirePayload()
            assertTrue(payload.startsWith("stage=${stage.ordinal + 1}&"))
            assertTrue(!payload.contains("advisory"))
            assertTrue(!payload.contains("track"))
        }
    }

    @Test
    fun `warning URL uses the dedicated control endpoint`() {
        assertEquals("http://192.168.43.12:80/drivealert/warning", HttpWarningOutputGateway.warningUrl("192.168.43.12"))
    }

    @Test
    fun `resolved host receives exact payload and accepted response is delivered`() = runBlocking {
        val request = AtomicReference<String>()
        withServer(200, HttpWarningOutputGateway.ACCEPTED_RESPONSE, request = request) { port ->
            val gateway = gateway(port, persistedHost = "192.0.2.1", resolvedHost = "127.0.0.1")
            assertEquals(WarningDeliveryStatus.DELIVERED, gateway.activate(WarningStage.STAGE_2.command()))
            assertEquals(
                "stage=2&sound=DIGITAL_BEEP&volume=MEDIUM",
                request.get(),
            )
        }
    }

    @Test
    fun `connection refused is failed`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        assertEquals(WarningDeliveryStatus.FAILED, gateway(port).activate(WarningStage.STAGE_1.command()))
    }

    @Test
    fun `read timeout is failed`() = runBlocking {
        withServer(200, HttpWarningOutputGateway.ACCEPTED_RESPONSE, delayMs = 250) { port ->
            val gateway = gateway(port, readTimeoutMs = 40)
            assertEquals(WarningDeliveryStatus.FAILED, gateway.activate(WarningStage.STAGE_1.command()))
        }
    }

    @Test
    fun `non-success response is failed`() = runBlocking {
        withServer(503, "{\"error\":\"warning_output_unavailable\"}") { port ->
            assertEquals(WarningDeliveryStatus.FAILED, gateway(port).activate(WarningStage.STAGE_1.command()))
        }
    }

    @Test
    fun `malformed success response is failed without throwing`() = runBlocking {
        withServer(200, "not-json") { port ->
            assertEquals(WarningDeliveryStatus.FAILED, gateway(port).activate(WarningStage.STAGE_1.command()))
        }
    }

    @Test
    fun `missing active device is failed without resolving`() = runBlocking {
        var resolveCalls = 0
        val gateway = HttpWarningOutputGateway(
            deviceProvider = { null },
            endpointResolver = object : DriveAlertEndpointResolver {
                override suspend fun resolve(device: ProvisionedDevice): ProvisionedDevice? {
                    resolveCalls++
                    return device
                }
            },
        )
        assertEquals(WarningDeliveryStatus.FAILED, gateway.activate(WarningStage.STAGE_1.command()))
        assertEquals(0, resolveCalls)
    }

    private fun gateway(
        port: Int,
        persistedHost: String = "127.0.0.1",
        resolvedHost: String = persistedHost,
        readTimeoutMs: Int = 1_500,
    ): HttpWarningOutputGateway {
        val persisted = ProvisionedDevice(
            deviceId = "camera",
            driverUserId = "driver",
            hostname = "drivealert-test",
            lastKnownIp = persistedHost,
            lastConnectedAtEpochMillis = 1,
        )
        return HttpWarningOutputGateway(
            deviceProvider = { persisted },
            endpointResolver = object : DriveAlertEndpointResolver {
                override suspend fun resolve(device: ProvisionedDevice) = device.copy(lastKnownIp = resolvedHost)
            },
            controlPort = port,
            connectTimeoutMs = 200,
            readTimeoutMs = readTimeoutMs,
        )
    }

    private suspend fun withServer(
        status: Int,
        response: String,
        delayMs: Long = 0,
        request: AtomicReference<String>? = null,
        block: suspend (Int) -> Unit,
    ) {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverThread = Thread {
            runCatching {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                    val requestLine = reader.readLine()
                    assertEquals("POST ${HttpWarningOutputGateway.WARNING_PATH} HTTP/1.1", requestLine)
                    var contentLength = 0
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = header.substringAfter(':').trim().toInt()
                        }
                    }
                    val body = CharArray(contentLength)
                    var offset = 0
                    while (offset < body.size) {
                        val count = reader.read(body, offset, body.size - offset)
                        if (count < 0) break
                        offset += count
                    }
                    request?.set(String(body, 0, offset))
                    if (delayMs > 0) Thread.sleep(delayMs)
                    val bytes = response.toByteArray()
                    val reason = if (status == 200) "OK" else "Service Unavailable"
                    val headers = "HTTP/1.1 $status $reason\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().use { output ->
                        output.write(headers.toByteArray())
                        output.write(bytes)
                    }
                }
            }
        }
        serverThread.isDaemon = true
        serverThread.start()
        try {
            block(server.localPort)
        } finally {
            server.close()
            serverThread.join(1_000)
        }
    }
}

private fun WarningStage.command() = warningOutputCommand(WarningSound.DIGITAL_BEEP, PreferredVolume.MEDIUM)
