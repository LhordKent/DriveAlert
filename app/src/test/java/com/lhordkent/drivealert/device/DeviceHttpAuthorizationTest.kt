package com.lhordkent.drivealert.device

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class DeviceHttpAuthorizationTest {
    private class Response(private val headers: Map<String, String>) : HttpURLConnection(URL("http://127.0.0.1")) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getHeaderField(name: String): String? = headers[name]
    }
    @Test fun authorizedResponsesMustConfirmDeviceIdentityAndProtocol() {
        val token = "AB".repeat(32)
        val valid = Response(mapOf("X-DriveAlert-Device" to "AABBCCDDEEFF", "X-DriveAlert-Auth" to "1"))
        valid.authorizeDevice(token)
        assertEquals("Bearer $token", valid.getRequestProperty("Authorization"))
        valid.requireDeviceResponse("AABBCCDDEEFF", token)
        assertThrows(IllegalStateException::class.java) { valid.requireDeviceResponse("001122334455", token) }
        assertThrows(IllegalStateException::class.java) { Response(emptyMap()).requireDeviceResponse("AABBCCDDEEFF", token) }
    }
}
