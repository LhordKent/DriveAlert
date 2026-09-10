package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.data.local.entity.StageSyncRecordType
import com.lhordkent.drivealert.detection.model.ConfirmedSignEvent
import com.lhordkent.drivealert.detection.model.SignType
import com.lhordkent.drivealert.postauth.VisibleSign
import com.lhordkent.drivealert.postauth.WarningStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningStageControllerTest {
    private val clock = FakeClock()
    private val warningStages = WarningStageController()
    private val controller = MonitoringSessionController(clock::now, warningStages)

    @Test
    fun normalEventImmediatelyEntersStage1AndStoresPostEventStage() {
        start()
        val effects = controller.accept(listOf(event(SignType.YAWNING, 1L)))

        assertEquals(WarningStage.STAGE_1, controller.state.currentStage)
        assertEquals(1, controller.state.confirmedEventCount)
        assertEquals(WarningStage.STAGE_1, effects.recordedAlert().stage)
    }

    @Test
    fun distinctEventInStage1ImmediatelyEntersStage2AndStartsFreshPeriod() {
        startStage1()
        recover()
        clock.value = 10_000L
        val effects = controller.accept(listOf(event(SignType.HEAD_NODDING, 2L)))

        assertEquals(WarningStage.STAGE_2, controller.state.currentStage)
        assertEquals(WarningStage.STAGE_2, effects.recordedAlert().stage)
        assertEquals(10_000L, warningStages.periodStartedAtMs())
        clock.value = 69_999L
        controller.tick()
        assertEquals(WarningStage.STAGE_2, controller.state.currentStage)
    }

    @Test
    fun distinctEventInStage2ImmediatelyEntersStage3AndStartsFreshPeriod() {
        enterStage2()
        recover()
        clock.value = 20_000L
        val effects = controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE, 3L)))

        assertEquals(WarningStage.STAGE_3, controller.state.currentStage)
        assertEquals(WarningStage.STAGE_3, effects.recordedAlert().stage)
        assertEquals(20_000L, warningStages.periodStartedAtMs())
        clock.value = 79_999L
        controller.tick()
        assertEquals(WarningStage.STAGE_3, controller.state.currentStage)
    }

    @Test
    fun distinctEventInStage3RemainsStage3AndDoesNotRestartPeriod() {
        enterStage3()
        recover()
        clock.value = 30_000L
        val effects = controller.accept(listOf(event(SignType.YAWNING, 4L)))

        assertEquals(WarningStage.STAGE_3, controller.state.currentStage)
        assertEquals(WarningStage.STAGE_3, effects.recordedAlert().stage)
        assertEquals(20_000L, warningStages.periodStartedAtMs())
        assertTrue(effects.filterIsInstance<MonitoringEffect.RecordStage3Boundary>().isEmpty())
    }

    @Test
    fun quietRecoveredStage1ReturnsToNormalAtTickerBoundary() {
        startStage1()
        recover()
        clock.value = 60_000L
        controller.tick()
        assertNull(controller.state.currentStage)
    }

    @Test
    fun quietRecoveredStage2DeescalatesToStage1AtTickerBoundary() {
        enterStage2()
        recover()
        clock.value = 70_000L
        controller.tick()
        assertEquals(WarningStage.STAGE_1, controller.state.currentStage)
    }

    @Test
    fun quietRecoveredStage3DeescalatesToStage2AtTickerBoundary() {
        enterStage3()
        recover()
        clock.value = 80_000L
        val effects = controller.tick()
        assertEquals(WarningStage.STAGE_2, controller.state.currentStage)
        assertTrue(effects.filterIsInstance<MonitoringEffect.RecordStage3Boundary>().isEmpty())
    }

    @Test
    fun persistentActivityAdvancesStage1AtBoundaryWithoutNewEvent() {
        startStage1()
        clock.value = 60_000L
        controller.tick()
        assertEquals(WarningStage.STAGE_2, controller.state.currentStage)
    }

    @Test
    fun persistentActivityAdvancesStage2AtBoundaryWithoutNewEvent() {
        startStage1()
        clock.value = 60_000L
        controller.tick()
        clock.value = 120_000L
        val effects = controller.tick()

        assertEquals(WarningStage.STAGE_3, controller.state.currentStage)
        assertEquals(1, effects.boundaries(StageSyncRecordType.STAGE3_TRANSITION).size)
    }

    @Test
    fun persistentActivityKeepsStage3AndCreatesOnePersistenceBoundary() {
        startStage1()
        clock.value = 60_000L
        controller.tick()
        clock.value = 120_000L
        controller.tick()
        clock.value = 180_000L
        val effects = controller.tick()

        assertEquals(WarningStage.STAGE_3, controller.state.currentStage)
        assertEquals(1, effects.boundaries(StageSyncRecordType.STAGE3_PERSISTENCE).size)
    }

    @Test
    fun oneContinuousEyeClosureDoesNotEscalateBeforePersistenceBoundary() {
        start()
        controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE, 1L)))
        clock.value = 10_000L
        controller.accept(emptyList(), setOf(VisibleSign.PROLONGED_EYE_CLOSURE))
        controller.tick()

        assertEquals(WarningStage.STAGE_1, controller.state.currentStage)
        assertEquals(1, controller.state.confirmedEventCount)
    }

    @Test
    fun exactReplayDoesNotEscalate() {
        start()
        val first = event(SignType.YAWNING, 1_000L)
        controller.accept(listOf(first))
        recover()
        controller.accept(listOf(first))

        assertEquals(WarningStage.STAGE_1, controller.state.currentStage)
        assertEquals(1, controller.state.confirmedEventCount)
    }

    @Test
    fun simultaneousMultiSignConfirmationCountsAsOneStageEvent() {
        start()
        val effects = controller.accept(
            listOf(
                event(SignType.YAWNING, 1_000L),
                event(SignType.PROLONGED_EYE_CLOSURE, 1_000L),
            ),
        )

        assertEquals(WarningStage.STAGE_1, controller.state.currentStage)
        assertEquals(1, controller.state.confirmedEventCount)
        assertEquals(
            setOf(VisibleSign.YAWNING, VisibleSign.PROLONGED_EYE_CLOSURE),
            effects.recordedAlert().signs,
        )
    }

    @Test
    fun overlappingAdjacentSignMergesWithoutEscalating() {
        start()
        controller.accept(
            listOf(event(SignType.PROLONGED_EYE_CLOSURE, 1_000L)),
            setOf(VisibleSign.PROLONGED_EYE_CLOSURE),
        )
        val effects = controller.accept(
            listOf(event(SignType.YAWNING, 2_000L)),
            setOf(VisibleSign.PROLONGED_EYE_CLOSURE, VisibleSign.YAWNING),
        )

        assertEquals(WarningStage.STAGE_1, controller.state.currentStage)
        assertEquals(1, controller.state.confirmedEventCount)
        assertEquals(setOf(VisibleSign.YAWNING), effects.singleMerge().signs)
    }

    @Test
    fun recoveredRearmedLaterEventEscalates() {
        startStage1()
        recover()
        clock.value = 10_000L
        controller.accept(listOf(event(SignType.YAWNING, 8_000L)))
        assertEquals(WarningStage.STAGE_2, controller.state.currentStage)
        assertEquals(2, controller.state.confirmedEventCount)
    }

    @Test
    fun firstStage3EntryCreatesExactlyOneTransitionBoundaryAfterAlertEffect() {
        enterStage2()
        recover()
        clock.value = 20_000L
        val effects = controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE, 3L)))

        assertEquals(1, effects.boundaries(StageSyncRecordType.STAGE3_TRANSITION).size)
        assertTrue(
            effects.indexOfFirst { it is MonitoringEffect.RecordAlert } <
                effects.indexOfFirst { it is MonitoringEffect.RecordStage3Boundary },
        )
    }

    @Test
    fun individualStage3EventsDoNotCreateIndividualSyncRecords() {
        enterStage3()
        recover()
        clock.value = 30_000L
        val effects = controller.accept(listOf(event(SignType.YAWNING, 4L)))
        assertTrue(effects.filterIsInstance<MonitoringEffect.RecordStage3Boundary>().isEmpty())
    }

    @Test
    fun recurrentCompletedStage3PeriodCreatesOnePersistenceBoundary() {
        enterStage3()
        recover()
        clock.value = 30_000L
        controller.accept(listOf(event(SignType.YAWNING, 4L)))
        recover()
        clock.value = 80_000L
        val effects = controller.tick()

        assertEquals(WarningStage.STAGE_3, controller.state.currentStage)
        assertEquals(1, effects.boundaries(StageSyncRecordType.STAGE3_PERSISTENCE).size)
        assertEquals(80_000L, warningStages.periodStartedAtMs())
    }

    @Test
    fun tickerCanAdvanceMultipleExpiredQuietPeriodsIncrementally() {
        enterStage3()
        recover()
        clock.value = 200_000L
        controller.tick()
        assertNull(controller.state.currentStage)
    }

    @Test
    fun stoppingSessionClearsStageAndCounters() {
        startStage1()
        controller.stop()
        assertEquals(ActiveMonitoringState(), controller.state)
    }

    private fun start() = controller.start("session-1")

    private fun startStage1() {
        start()
        controller.accept(listOf(event(SignType.YAWNING, 1L)))
    }

    private fun enterStage2() {
        startStage1()
        recover()
        clock.value = 10_000L
        controller.accept(listOf(event(SignType.HEAD_NODDING, 2L)))
        assertEquals(WarningStage.STAGE_2, controller.state.currentStage)
    }

    private fun enterStage3() {
        enterStage2()
        recover()
        clock.value = 20_000L
        controller.accept(listOf(event(SignType.PROLONGED_EYE_CLOSURE, 3L)))
        assertEquals(WarningStage.STAGE_3, controller.state.currentStage)
    }

    private fun recover() = controller.accept(emptyList(), emptySet())

}

private class FakeClock(var value: Long = 0L) {
    fun now() = value
}

private fun List<MonitoringEffect>.recordedAlert() = filterIsInstance<MonitoringEffect.RecordAlert>().single()

private fun List<MonitoringEffect>.singleMerge() = filterIsInstance<MonitoringEffect.MergeLatestAlertSigns>().single()

private fun List<MonitoringEffect>.boundaries(type: StageSyncRecordType) =
    filterIsInstance<MonitoringEffect.RecordStage3Boundary>().filter { it.type == type }

private fun event(type: SignType, timestampMs: Long) = ConfirmedSignEvent(
    type = type,
    timestampMs = timestampMs,
    qualifyingDurationMs = 2_000L,
    measuredValue = 0.1,
    threshold = 0.2,
    calibrationId = "calibration-1",
    calibrationVersion = 3,
)
