package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.postauth.WarningStage
import org.junit.Assert.*
import org.junit.Test

class Stage3PlaybackGuardTest {
    @Test fun `new incidents during stage three cannot restart the spoken track`() {
        val guard = Stage3PlaybackGuard()
        guard.accepted("camera", WarningStage.STAGE_3, 100L)
        assertEquals(7_300L, guard.remainingMs("camera", 3_100L))
        // A lower stage must not shorten the accepted speech reservation.
        guard.accepted("camera", WarningStage.STAGE_1, 3_100L)
        assertEquals(7_300L, guard.remainingMs("camera", 3_100L))
        assertEquals(1L, guard.remainingMs("camera", 10_399L))
        assertEquals(0L, guard.remainingMs("camera", 10_400L))
    }

    @Test fun `speech on one camera does not block a different camera`() {
        val guard = Stage3PlaybackGuard()
        guard.accepted("first", WarningStage.STAGE_3, 100)
        assertEquals(0L, guard.remainingMs("second", 200))
        assertEquals(0L, guard.remainingMs(null, 200))
        guard.accepted("second", WarningStage.STAGE_3, 200)
        assertEquals(10_200L, guard.remainingMs("first", 200))
        assertEquals(10_300L, guard.remainingMs("second", 200))
    }
}
