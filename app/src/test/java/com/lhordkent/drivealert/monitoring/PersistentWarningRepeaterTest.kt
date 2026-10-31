package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.detection.model.ConfirmedSignEvent
import com.lhordkent.drivealert.detection.model.SignType
import com.lhordkent.drivealert.postauth.VisibleSign
import com.lhordkent.drivealert.postauth.WarningStage
import org.junit.Assert.*
import org.junit.Test

class PersistentWarningRepeaterTest {
    private var now = 0L
    private val stages = WarningStageController()
    private val controller = MonitoringSessionController({ now }, stages)

    @Test
    fun `six second closure records once and repeats at four and six seconds`() {
        controller.start("session")
        now = 2_000L
        val first = controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE)))
        assertEquals(1, first.filterIsInstance<MonitoringEffect.RecordAlert>().size)
        assertEquals(1, first.filterIsInstance<MonitoringEffect.ActivateWarning>().size)
        now = 3_999L
        assertTrue(eyes().isEmpty())
        for (time in listOf(4_000L, 6_000L)) {
            now = time
            assertEquals(listOf(MonitoringEffect.RepeatWarning(WarningStage.STAGE_1, eyeSigns)), eyes())
            assertEquals(1, controller.state.confirmedEventCount)
            assertEquals(WarningStage.STAGE_1, controller.state.currentStage)
            assertEquals(2_000L, stages.periodStartedAtMs())
            assertEquals(2_000L, controller.state.latestEventAtElapsedMs)
        }
    }

    @Test
    fun `yawn and head reminders use three seconds and merge simultaneous requests`() {
        controller.start("session")
        now = 3_000L
        val signs = setOf(VisibleSign.YAWNING, VisibleSign.HEAD_NODDING)
        controller.accept(listOf(event(SignType.YAWNING), event(SignType.HEAD_NODDING)), signs)
        now = 5_999L
        assertTrue(controller.accept(emptyList(), signs).isEmpty())
        now = 6_000L
        assertEquals(listOf(MonitoringEffect.RepeatWarning(WarningStage.STAGE_1, signs)), controller.accept(emptyList(), signs))
        assertEquals(1, controller.state.confirmedEventCount)
    }

    @Test
    fun `recovery or unavailable channel clears reminders and new closure is a new incident`() {
        controller.start("session")
        controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE)))
        now = 1_000L
        controller.accept(emptyList(), emptySet())
        now = 4_000L
        assertTrue(controller.accept(emptyList(), emptySet()).isEmpty())
        now = 6_000L
        val next = controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE)))
        assertEquals(1, next.filterIsInstance<MonitoringEffect.RecordAlert>().size)
        assertEquals(2, controller.state.confirmedEventCount)
        now = 8_000L
        assertEquals(WarningStage.STAGE_2, eyes().filterIsInstance<MonitoringEffect.RepeatWarning>().single().stage)
    }

    @Test
    fun `channel loss clears only its reminder while another sign continues`() {
        val repeater = PersistentWarningRepeater()
        val both = eyeSigns + VisibleSign.YAWNING
        repeater.update(0, both)
        repeater.update(1_000, setOf(VisibleSign.YAWNING))
        assertEquals(setOf(VisibleSign.YAWNING), repeater.update(3_000, both))
        assertEquals(eyeSigns, repeater.update(5_000, both))
    }

    @Test
    fun `delayed frames skip missed slots and tick alone never generates reminders`() {
        controller.start("session")
        controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE)))
        now = 10_500L
        assertTrue(controller.tick().isEmpty())
        assertEquals(1, eyes().filterIsInstance<MonitoringEffect.RepeatWarning>().size)
        assertTrue(eyes().isEmpty())
        now = 11_999L
        assertTrue(eyes().isEmpty())
        now = 12_000L
        assertEquals(1, eyes().filterIsInstance<MonitoringEffect.RepeatWarning>().size)
    }

    @Test
    fun `sixty second transition supersedes repeat regardless of ticker order`() {
        for (tickFirst in listOf(false, true)) {
            now = 0
            controller.start("session")
            controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE)))
            now = 60_000L
            val effects = if (tickFirst) controller.tick() + eyes() else eyes() + controller.tick()
            assertEquals(listOf(MonitoringEffect.ActivateWarning(WarningStage.STAGE_2)), effects)
            assertEquals(1, controller.state.confirmedEventCount)
            now = 62_000L
            assertEquals(listOf(MonitoringEffect.RepeatWarning(WarningStage.STAGE_2, eyeSigns)), eyes())
        }
    }

    @Test
    fun `stop and restart discard previous repeat schedules`() {
        controller.start("old")
        controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE)))
        controller.stop()
        now = 4_000L
        assertTrue(eyes().isEmpty())
        controller.start("new")
        assertTrue(controller.tick().isEmpty())
        assertTrue(controller.accept(emptyList(), emptySet()).isEmpty())
        controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE)))
        now = 5_999L
        assertTrue(eyes().isEmpty())
        now = 6_000L
        assertEquals(1, eyes().filterIsInstance<MonitoringEffect.RepeatWarning>().size)
    }

    @Test
    fun `clearing camera evidence does not consume a pending stage three synchronization boundary`() {
        controller.start("session")
        repeat(4) { index ->
            now = index * 1_000L
            controller.accept(emptyList(), emptySet())
            controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE)))
        }
        assertEquals(WarningStage.STAGE_3, controller.state.currentStage)
        now = 62_000L
        assertTrue(controller.accept(emptyList(), emptySet()).isEmpty())
        val effects = controller.tick()
        assertEquals(1, effects.filterIsInstance<MonitoringEffect.RecordStage3Boundary>().size)
    }

    private fun eyes() = controller.accept(emptyList(), eyeSigns)

    private fun event(type: SignType) = ConfirmedSignEvent(type, now, 2_000L, 0.1, 0.2, "calibration", 3)

    private val eyeSigns = setOf(VisibleSign.PROLONGED_EYE_CLOSURE)
}
