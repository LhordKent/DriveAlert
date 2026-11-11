package com.lhordkent.drivealert.ui.postauth

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionSharingNoticeTest {
    @Test
    fun `names the exact name and email the receiver will see`() {
        assertEquals(
            "Your name (Juan Dela Cruz) and email (juan@gmail.com) will be visible to the person you send this request to.",
            connectionSharingNotice("Juan Dela Cruz", "juan@gmail.com"),
        )
    }

    @Test
    fun `still explains what is shared before the profile has loaded`() {
        assertEquals(
            "Your name and email will be visible to the person you send this request to.",
            connectionSharingNotice(" ", ""),
        )
        assertEquals(
            "Your email (juan@gmail.com) will be visible to the person you send this request to.",
            connectionSharingNotice("", "juan@gmail.com"),
        )
    }
}
