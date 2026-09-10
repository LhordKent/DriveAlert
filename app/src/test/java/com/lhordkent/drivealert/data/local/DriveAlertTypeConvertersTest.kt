package com.lhordkent.drivealert.data.local

import com.lhordkent.drivealert.data.local.entity.StoredWarningStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveAlertTypeConvertersTest {
    private val converters = DriveAlertTypeConverters()

    @Test
    fun nullableSessionHighestStageRoundTripsWithoutFailure() {
        assertNull(converters.warningStage(null as String?))
        assertNull(converters.warningStage(null as StoredWarningStage?))
        assertEquals(StoredWarningStage.STAGE_2, converters.warningStage("STAGE_2"))
        assertEquals("STAGE_3", converters.warningStage(StoredWarningStage.STAGE_3))
    }
}
