package com.lhordkent.drivealert.detection.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeClosureConflictTest {
    private fun evidence(left: Float, right: Float) = FaceAttributeResult(
        timestampMs = 1_000, ageMs = 0,
        probabilities = FaceAttributeProbabilities(left, right, 0f, 0.99f, 0f),
        stableEyeglasses = false, lowerFaceObstructed = true,
        eyeRegionObstructed = false, inferenceDurationMs = 1.0,
    )

    @Test fun `either confident open eye contradicts bilateral closure at the boundary`() {
        assertTrue(evidence(0.90f, 0.1f).contradictsBilateralClosureAt(1_000))
        assertTrue(evidence(0.1f, 0.90f).contradictsBilateralClosureAt(1_000))
        assertFalse(evidence(0.899f, 0.899f).contradictsBilateralClosureAt(1_000))
    }

    @Test fun `stale and future observations cannot reset closure evidence`() {
        val sample = evidence(0.99f, 0.99f)
        assertTrue(sample.contradictsBilateralClosureAt(1_750))
        assertFalse(sample.contradictsBilateralClosureAt(1_751))
        assertFalse(sample.contradictsBilateralClosureAt(999))
    }

    @Test fun `unavailable obstructed or malformed probabilities cannot veto`() {
        val sample = evidence(0.99f, 0.1f)
        assertFalse(sample.copy(eyeRegionObstructed = true).contradictsBilateralClosureAt(1_000))
        assertFalse(sample.copy(errorReason = "Unavailable").contradictsBilateralClosureAt(1_000))
        assertFalse(sample.copy(probabilities = null).contradictsBilateralClosureAt(1_000))
        assertFalse(evidence(Float.NaN, 0.99f).contradictsBilateralClosureAt(1_000))
        assertFalse(evidence(0.99f, -0.1f).contradictsBilateralClosureAt(1_000))
    }
}
