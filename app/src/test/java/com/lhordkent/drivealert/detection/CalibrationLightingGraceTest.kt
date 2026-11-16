package com.lhordkent.drivealert.detection

import com.lhordkent.drivealert.monitoring.FaceLightingAssessment
import com.lhordkent.drivealert.monitoring.LightingQuality
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationLightingGraceTest {
    private val good = FaceLightingAssessment(1_000, LightingQuality.USABLE)
    private val missing = FaceLightingAssessment()

    @Test fun missingLightingRequiresEstablishedUsableLightingAndExpiresAfterTwoSeconds() {
        val gate = CalibrationLightingGrace()
        assertFalse(gate.allowsAssessment(900, 900, missing))
        assertTrue(gate.allowsAssessment(1_000, 1_000, good))
        assertTrue(gate.allowsAssessment(2_500, 2_500, missing))
        assertTrue(gate.allowsAssessment(3_000, 3_000, missing))
        assertFalse(gate.allowsAssessment(3_001, 3_001, missing))
        assertFalse(gate.allowsAssessment(4_000, 4_000, missing))
    }

    @Test fun missingStaleOrFutureFaceEndsGrace() {
        for (faceTimestamp in listOf(null, 0L, 2_001L)) {
            val gate = CalibrationLightingGrace()
            assertTrue(gate.allowsAssessment(1_000, 1_000, good))
            assertFalse(gate.allowsAssessment(2_000, faceTimestamp, missing))
            assertFalse(gate.allowsAssessment(2_100, 2_100, missing))
        }
    }

    @Test fun measuredPoorLightAndRecoveryImmediatelyEndGrace() {
        for (assessment in listOf(
            FaceLightingAssessment(1_100, LightingQuality.UNKNOWN, poorSinceMs = 1_100),
            FaceLightingAssessment(1_100, LightingQuality.INSUFFICIENT, poorSinceMs = 1_000),
            FaceLightingAssessment(1_100, LightingQuality.UNKNOWN, recovering = true),
        )) {
            val gate = CalibrationLightingGrace()
            assertTrue(gate.allowsAssessment(1_000, 1_000, good))
            assertFalse(gate.allowsAssessment(1_100, 1_100, assessment))
            assertFalse(gate.allowsAssessment(1_200, 1_200, missing))
        }
    }

    @Test fun repeatedUsableSampleDoesNotExtendGraceAndResetClearsIt() {
        val gate = CalibrationLightingGrace()
        assertTrue(gate.allowsAssessment(1_000, 1_000, good))
        assertTrue(gate.allowsAssessment(2_000, 2_000, good))
        assertFalse(gate.allowsAssessment(3_001, 3_001, missing))
        assertTrue(gate.allowsAssessment(4_000, 4_000, good.copy(timestampMs = 4_000)))
        gate.reset()
        assertFalse(gate.allowsAssessment(4_100, 4_100, missing))
    }
}
