package com.lhordkent.drivealert.device

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveAlertSessionControllerTest {
    @Test
    fun `disconnect command targets the numeric control endpoint`() {
        assertEquals(
            "http://192.168.43.12:80/drivealert/disconnect",
            HttpDriveAlertSessionController.disconnectUrl("192.168.43.12"),
        )
    }
}
