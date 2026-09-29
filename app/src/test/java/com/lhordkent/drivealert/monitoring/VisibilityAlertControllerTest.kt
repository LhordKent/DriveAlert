package com.lhordkent.drivealert.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibilityAlertControllerTest {
    @Test
    fun `lower face obstruction notifies once after two seconds`() {
        val controller = VisibilityAlertController()

        assertTrue(controller.update(0, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
        assertTrue(controller.update(1_999, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.LOWER_FACE_OBSTRUCTED)),
            controller.update(2_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED),
        )
        assertTrue(controller.update(5_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
    }

    @Test
    fun `sustained obstruction repeats every thirty seconds`() {
        val controller = VisibilityAlertController(reminderIntervalMs = 30_000)
        controller.update(0, VisibilityIssue.LOWER_FACE_OBSTRUCTED)
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.LOWER_FACE_OBSTRUCTED)),
            controller.update(2_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED),
        )
        assertTrue(controller.update(31_999, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.LOWER_FACE_OBSTRUCTED)),
            controller.update(32_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED),
        )
    }

    @Test
    fun `face loss uses one second delay`() {
        val controller = VisibilityAlertController()

        controller.update(100, VisibilityIssue.FACE_UNAVAILABLE)

        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.FACE_UNAVAILABLE)),
            controller.update(1_100, VisibilityIssue.FACE_UNAVAILABLE),
        )
    }

    @Test
    fun `sunglasses use two seconds while both obstructed regions use one second`() {
        val eyeController = VisibilityAlertController()
        eyeController.update(0, VisibilityIssue.EYE_REGION_OBSTRUCTED)
        assertTrue(eyeController.update(1_000, VisibilityIssue.EYE_REGION_OBSTRUCTED).isEmpty())
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.EYE_REGION_OBSTRUCTED)),
            eyeController.update(2_000, VisibilityIssue.EYE_REGION_OBSTRUCTED),
        )

        val bothController = VisibilityAlertController()
        bothController.update(0, VisibilityIssue.BOTH_REGIONS_OBSTRUCTED)
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.BOTH_REGIONS_OBSTRUCTED)),
            bothController.update(1_000, VisibilityIssue.BOTH_REGIONS_OBSTRUCTED),
        )
    }

    @Test
    fun `brief obstruction clears silently and notified obstruction clears after recovery`() {
        val controller = VisibilityAlertController(recoveryDelayMs = 1_000)
        controller.update(0, VisibilityIssue.LOWER_FACE_OBSTRUCTED)
        assertTrue(controller.update(500, null).isEmpty())

        controller.update(2_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED)
        controller.update(4_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED)
        assertTrue(controller.update(4_100, null).isEmpty())
        assertEquals(listOf(VisibilityAlertEffect.Clear), controller.update(5_100, null))
    }

    @Test
    fun `separate brief obstructions are not combined`() {
        val controller = VisibilityAlertController()
        controller.update(0, VisibilityIssue.LOWER_FACE_OBSTRUCTED)
        controller.update(1_500, null)

        controller.update(1_600, VisibilityIssue.LOWER_FACE_OBSTRUCTED)

        assertTrue(controller.update(2_100, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.LOWER_FACE_OBSTRUCTED)),
            controller.update(3_600, VisibilityIssue.LOWER_FACE_OBSTRUCTED),
        )
    }

    @Test
    fun `declared mask suppresses mouth alerts but a new eye obstruction escalates`() {
        val controller = VisibilityAlertController()
        controller.beginSession(DriverAccessoryMode.MASK)

        assertTrue(controller.update(0, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
        assertTrue(controller.update(5_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
        assertTrue(controller.update(6_000, VisibilityIssue.BOTH_REGIONS_OBSTRUCTED).isEmpty())
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.BOTH_REGIONS_OBSTRUCTED)),
            controller.update(7_000, VisibilityIssue.BOTH_REGIONS_OBSTRUCTED),
        )
    }

    @Test
    fun `acknowledged obstruction stays quiet for the rest of the session`() {
        val controller = VisibilityAlertController(reminderIntervalMs = 30_000)
        controller.update(0, VisibilityIssue.EYE_REGION_OBSTRUCTED)
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.EYE_REGION_OBSTRUCTED)),
            controller.update(2_000, VisibilityIssue.EYE_REGION_OBSTRUCTED),
        )

        controller.acknowledge(VisibilityIssue.EYE_REGION_OBSTRUCTED)

        assertTrue(controller.update(40_000, VisibilityIssue.EYE_REGION_OBSTRUCTED).isEmpty())
        assertTrue(controller.update(80_000, VisibilityIssue.EYE_REGION_OBSTRUCTED).isEmpty())
    }

    @Test
    fun `ordinary eyeglasses do not suppress a real obstruction`() {
        val controller = VisibilityAlertController()
        controller.beginSession(DriverAccessoryMode.EYEGLASSES)
        controller.update(0, VisibilityIssue.EYE_REGION_OBSTRUCTED)

        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.EYE_REGION_OBSTRUCTED)),
            controller.update(2_000, VisibilityIssue.EYE_REGION_OBSTRUCTED),
        )
    }
}
