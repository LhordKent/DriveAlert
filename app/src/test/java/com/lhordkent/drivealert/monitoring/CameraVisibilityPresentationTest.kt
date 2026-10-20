package com.lhordkent.drivealert.monitoring

import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.postauth.MonitoringScenario
import org.junit.Assert.*
import org.junit.Test

class CameraVisibilityPresentationTest {
    @Test
    fun `darkness alone does not disable any monitoring sign`() {
        val view = present(MonitoringScenario.NORMAL, lowLight = true)
        assertEquals("Low light", view.label)
        assertEquals("Monitoring active", view.monitoring.headline)
        assertTrue(view.monitoring.eyeAvailable && view.monitoring.yawningAvailable && view.monitoring.headAvailable)
    }

    @Test
    fun `low light explains missing face as a possibility`() {
        val view = present(MonitoringScenario.FACE_TRACKING_UNAVAILABLE, lowLight = true)
        assertTrue(view.detail.contains("Low light may be affecting"))
        assertEquals("Monitoring paused", view.monitoring.headline)
        assertFalse(view.monitoring.eyeAvailable || view.monitoring.yawningAvailable || view.monitoring.headAvailable)
    }

    @Test
    fun `unknown or sufficient brightness does not claim low light caused face loss`() {
        listOf(null, false).forEach { lowLight ->
            val view = present(MonitoringScenario.FACE_TRACKING_UNAVAILABLE, lowLight)
            assertFalse(view.detail.contains("Low light"))
            assertTrue(view.detail.contains("position or an obstruction"))
        }
    }

    @Test
    fun `disconnected stream overrides stale clear or dark scenes`() {
        listOf(StreamConnectionState.RECONNECTING, StreamConnectionState.UNAVAILABLE).forEach { stream ->
            val view = cameraVisibilityPresentation(stream, MonitoringScenario.NORMAL, true, true)
            assertEquals("Disconnected", view.label)
            assertFalse(view.monitoring.eyeAvailable || view.monitoring.yawningAvailable || view.monitoring.headAvailable)
        }
    }

    @Test
    fun `fresh frames without a recent detection cannot show active monitoring`() {
        val view = cameraVisibilityPresentation(StreamConnectionState.CONNECTED, MonitoringScenario.NORMAL, false, false)
        assertEquals("Checking view", view.label)
        assertEquals("Monitoring paused", view.monitoring.headline)
        assertFalse(view.monitoring.eyeAvailable)
    }

    @Test
    fun `partial visibility and accepted accessories retain their available channels`() {
        val eyes = present(MonitoringScenario.EYE_UNAVAILABLE, true)
        assertFalse(eyes.monitoring.eyeAvailable)
        assertTrue(eyes.monitoring.yawningAvailable && eyes.monitoring.headAvailable)
        assertTrue(eyes.detail.contains("Low light may also"))
        val mouth = present(MonitoringScenario.YAWNING_UNAVAILABLE, false)
        assertTrue(mouth.monitoring.eyeAvailable && mouth.monitoring.headAvailable)
        assertFalse(mouth.monitoring.yawningAvailable)
        assertEquals("Monitoring paused", present(MonitoringScenario.EYE_AND_YAWNING_UNAVAILABLE, true).monitoring.headline)
    }

    @Test
    fun `clear tracking restores all available signs`() {
        assertEquals("Face not visible", present(MonitoringScenario.FACE_TRACKING_UNAVAILABLE, true).label)
        val recovered = present(MonitoringScenario.NORMAL, false)
        assertEquals("Clear view", recovered.label)
        assertEquals("Monitoring active", recovered.monitoring.headline)
    }

    @Test
    fun `recovery holds a reported face warning until the controller clears it`() {
        val recovering = cameraVisibilityPresentation(
            StreamConnectionState.CONNECTED, MonitoringScenario.NORMAL, true, false,
            pendingIssue = VisibilityIssue.FACE_UNAVAILABLE,
        )
        assertEquals("Monitoring paused", recovering.monitoring.headline)
        val recovered = cameraVisibilityPresentation(StreamConnectionState.CONNECTED, MonitoringScenario.NORMAL, true, false)
        assertEquals("Monitoring active", recovered.monitoring.headline)
    }

    @Test
    fun `a pending accessory warning cannot hide currently insufficient measurements`() {
        val view = cameraVisibilityPresentation(
            StreamConnectionState.CONNECTED, MonitoringScenario.EYE_AND_YAWNING_UNAVAILABLE, true, false,
            pendingIssue = VisibilityIssue.LOWER_FACE_OBSTRUCTED,
        )
        assertEquals("Monitoring paused", view.monitoring.headline)
        assertFalse(view.monitoring.headAvailable)
    }

    @Test
    fun `missing head tracking is not presented as three available signs`() {
        val view = cameraVisibilityPresentation(
            StreamConnectionState.CONNECTED, MonitoringScenario.NORMAL, true, false, headAvailable = false,
        )
        assertEquals("Monitoring limited", view.monitoring.headline)
        assertTrue(view.monitoring.eyeAvailable && view.monitoring.yawningAvailable)
        assertFalse(view.monitoring.headAvailable)
    }

    private fun present(scenario: MonitoringScenario, lowLight: Boolean?) =
        cameraVisibilityPresentation(StreamConnectionState.CONNECTED, scenario, true, lowLight)
}
