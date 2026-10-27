package com.lhordkent.drivealert.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class VisibilityAlertControllerTest {
    @Test
    fun `lighting cannot be acknowledged and repeats every thirty seconds`() {
        val controller = VisibilityAlertController()
        controller.beginSession(DriverAccessoryMode.MASK_AND_SUNGLASSES)
        controller.update(0, VisibilityIssue.INSUFFICIENT_LIGHTING)
        val notify = listOf(VisibilityAlertEffect.Notify(VisibilityIssue.INSUFFICIENT_LIGHTING))
        assertEquals(notify, controller.update(3_000, VisibilityIssue.INSUFFICIENT_LIGHTING))
        controller.acknowledge(VisibilityIssue.INSUFFICIENT_LIGHTING)
        assertTrue(controller.update(32_999, VisibilityIssue.INSUFFICIENT_LIGHTING).isEmpty())
        assertEquals(notify, controller.update(33_000, VisibilityIssue.INSUFFICIENT_LIGHTING))
    }

    @Test
    fun `lower face obstruction notifies once after three seconds`() {
        val controller = VisibilityAlertController()

        assertTrue(controller.update(0, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
        assertTrue(controller.update(2_999, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.LOWER_FACE_OBSTRUCTED)),
            controller.update(3_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED),
        )
        assertTrue(controller.update(5_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
    }

    @Test
    fun `sustained obstruction repeats every thirty seconds`() {
        val controller = VisibilityAlertController()
        controller.update(0, VisibilityIssue.LOWER_FACE_OBSTRUCTED)
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.LOWER_FACE_OBSTRUCTED)),
            controller.update(3_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED),
        )
        assertTrue(controller.update(32_999, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.LOWER_FACE_OBSTRUCTED)),
            controller.update(33_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED),
        )
    }

    @Test
    fun `face loss waits three seconds and repeats after thirty seconds`() {
        val controller = VisibilityAlertController()

        controller.update(100, VisibilityIssue.FACE_UNAVAILABLE)
        assertTrue(controller.update(3_099, VisibilityIssue.FACE_UNAVAILABLE).isEmpty())

        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.FACE_UNAVAILABLE)),
            controller.update(3_100, VisibilityIssue.FACE_UNAVAILABLE),
        )
        assertTrue(controller.update(33_099, VisibilityIssue.FACE_UNAVAILABLE).isEmpty())
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.FACE_UNAVAILABLE)),
            controller.update(33_100, VisibilityIssue.FACE_UNAVAILABLE),
        )
    }

    @Test
    fun `eye and combined obstructions both require three seconds`() {
        val eyeController = VisibilityAlertController()
        eyeController.update(0, VisibilityIssue.EYE_REGION_OBSTRUCTED)
        assertTrue(eyeController.update(1_000, VisibilityIssue.EYE_REGION_OBSTRUCTED).isEmpty())
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.EYE_REGION_OBSTRUCTED)),
            eyeController.update(3_000, VisibilityIssue.EYE_REGION_OBSTRUCTED),
        )

        val bothController = VisibilityAlertController()
        bothController.update(0, VisibilityIssue.BOTH_REGIONS_OBSTRUCTED)
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.BOTH_REGIONS_OBSTRUCTED)),
            bothController.update(3_000, VisibilityIssue.BOTH_REGIONS_OBSTRUCTED),
        )
    }

    @Test
    fun `brief obstruction clears silently and notified obstruction clears after recovery`() {
        val controller = VisibilityAlertController()
        controller.update(0, VisibilityIssue.LOWER_FACE_OBSTRUCTED)
        assertTrue(controller.update(500, null).isEmpty())

        controller.update(2_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED)
        controller.update(5_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED)
        assertTrue(controller.update(5_100, null).isEmpty())
        assertEquals(VisibilityIssue.LOWER_FACE_OBSTRUCTED, controller.reportedIssue)
        assertTrue(controller.update(7_099, null).isEmpty())
        assertEquals(listOf(VisibilityAlertEffect.Clear), controller.update(7_100, null))
        assertNull(controller.reportedIssue)
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
            controller.update(4_600, VisibilityIssue.LOWER_FACE_OBSTRUCTED),
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
            controller.update(9_000, VisibilityIssue.BOTH_REGIONS_OBSTRUCTED),
        )
    }

    @Test
    fun `acknowledged obstruction stays quiet for the rest of the session`() {
        val controller = VisibilityAlertController(reminderIntervalMs = 30_000)
        controller.update(0, VisibilityIssue.EYE_REGION_OBSTRUCTED)
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.EYE_REGION_OBSTRUCTED)),
            controller.update(3_000, VisibilityIssue.EYE_REGION_OBSTRUCTED),
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
            controller.update(3_000, VisibilityIssue.EYE_REGION_OBSTRUCTED),
        )
    }

    @Test
    fun `disconnect interrupts an episode without forgetting accepted accessories`() {
        val controller = VisibilityAlertController()
        controller.beginSession(DriverAccessoryMode.MASK)
        controller.update(0, VisibilityIssue.FACE_UNAVAILABLE)
        controller.update(3_000, VisibilityIssue.FACE_UNAVAILABLE)
        controller.interrupt()

        assertTrue(controller.update(40_000, VisibilityIssue.LOWER_FACE_OBSTRUCTED).isEmpty())
        assertTrue(controller.update(41_000, VisibilityIssue.FACE_UNAVAILABLE).isEmpty())
        assertTrue(controller.update(43_999, VisibilityIssue.FACE_UNAVAILABLE).isEmpty())
        assertEquals(
            listOf(VisibilityAlertEffect.Notify(VisibilityIssue.FACE_UNAVAILABLE)),
            controller.update(44_000, VisibilityIssue.FACE_UNAVAILABLE),
        )
    }
}
