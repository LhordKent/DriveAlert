package com.lhordkent.drivealert.notification

import com.lhordkent.drivealert.detection.model.ConfirmedSignEvent
import com.lhordkent.drivealert.detection.model.SignType
import com.lhordkent.drivealert.monitoring.MonitoringEffect
import com.lhordkent.drivealert.monitoring.MonitoringSessionController
import com.lhordkent.drivealert.postauth.VisibleSign
import com.lhordkent.drivealert.postauth.WarningStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverWarningNotifierTest {
    private class FakeGateway(
        private val result: NotificationDeliveryStatus = NotificationDeliveryStatus.POSTED,
    ) : DriverWarningNotificationGateway {
        val visible = linkedMapOf<String, DriverWarningMessage>()
        override fun postOrUpdate(message: DriverWarningMessage): NotificationDeliveryStatus {
            visible[message.alertId] = message
            return result
        }
    }

    @Test fun oneAlertIdProducesOneVisibleNotificationAndMergesSigns() {
        val gateway = FakeGateway()
        val coordinator = DriverWarningNotificationCoordinator(gateway)
        coordinator.notifyConfirmedAlert("alert-1", WarningStage.STAGE_1, setOf(VisibleSign.PROLONGED_EYE_CLOSURE), true)
        coordinator.notifyConfirmedAlert("alert-1", WarningStage.STAGE_1, setOf(VisibleSign.PROLONGED_EYE_CLOSURE, VisibleSign.HEAD_NODDING), true)
        assertEquals(1, gateway.visible.size)
        assertEquals(2, gateway.visible.getValue("alert-1").signs.size)
    }

    @Test fun multiSignMessageUsesPersistedSignsAndStageWording() {
        val text = driverWarningText(WarningStage.STAGE_2, setOf(VisibleSign.PROLONGED_EYE_CLOSURE, VisibleSign.HEAD_NODDING))
        assertTrue(text.contains("Prolonged Eye Closure and Head Nodding detected."))
        assertTrue(text.contains("rest"))
    }

    @Test fun deniedPermissionDoesNotThrowOrAffectMonitoringCaller() {
        val coordinator = DriverWarningNotificationCoordinator(FakeGateway(NotificationDeliveryStatus.PERMISSION_DENIED))
        assertEquals(
            NotificationDeliveryStatus.PERMISSION_DENIED,
            coordinator.notifyConfirmedAlert("alert-1", WarningStage.STAGE_1, setOf(VisibleSign.YAWNING), true),
        )
    }

    @Test fun driverPreferenceCanDisableDelivery() {
        val gateway = FakeGateway()
        val result = DriverWarningNotificationCoordinator(gateway)
            .notifyConfirmedAlert("alert-1", WarningStage.STAGE_1, setOf(VisibleSign.YAWNING), false)
        assertEquals(NotificationDeliveryStatus.DISABLED_BY_DRIVER, result)
        assertTrue(gateway.visible.isEmpty())
    }

    @Test fun notificationUsesTheSamePostEventStageAsThePersistedAlert() {
        var now = 0L
        val monitoring = MonitoringSessionController(clock = { now })
        monitoring.start("session-1")
        monitoring.accept(listOf(confirmed(SignType.YAWNING, 1L)))
        monitoring.accept(emptyList(), emptySet())
        now = 10_000L
        val alert = monitoring.accept(listOf(confirmed(SignType.HEAD_NODDING, 2L)))
            .filterIsInstance<MonitoringEffect.RecordAlert>()
            .single()
        val gateway = FakeGateway()

        DriverWarningNotificationCoordinator(gateway)
            .notifyConfirmedAlert("alert-2", alert.stage, alert.signs, true)

        assertEquals(WarningStage.STAGE_2, alert.stage)
        assertEquals(alert.stage, gateway.visible.getValue("alert-2").stage)
    }
}

private fun confirmed(type: SignType, timestampMs: Long) = ConfirmedSignEvent(
    type = type,
    timestampMs = timestampMs,
    qualifyingDurationMs = 2_000L,
    measuredValue = 0.1,
    threshold = 0.2,
    calibrationId = "calibration-1",
    calibrationVersion = 3,
)
