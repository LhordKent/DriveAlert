package com.lhordkent.drivealert.monitoring

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.postauth.MonitoringScenario
import com.lhordkent.drivealert.ui.postauth.MonitoringPreviewScreen
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

class CameraVisibilityScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun visibilityStatesReplaceLightingControlsAndRecover() {
        val scenario = mutableStateOf(MonitoringScenario.NORMAL)
        val lowLight = mutableStateOf(false)
        val stream = mutableStateOf(StreamConnectionState.CONNECTED)
        val lighting = mutableStateOf<FaceLightingAssessment?>(null)
        composeRule.setContent {
            DriveAlertTheme {
                MonitoringPreviewScreen(
                    scenario = scenario.value,
                    monitoring = ActiveMonitoringState(isActive = true),
                    onExit = {},
                    cameraVisibility = cameraVisibilityPresentation(stream.value, scenario.value, true, lowLight.value,
                        lighting = lighting.value),
                )
            }
        }
        composeRule.onNodeWithText("Camera visibility").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Clear view").assertIsDisplayed()
        composeRule.onNodeWithText("Low-light illumination").assertDoesNotExist()
        composeRule.onNodeWithText("Auto").assertDoesNotExist()
        capture("clear")

        composeRule.runOnIdle { lowLight.value = true }
        composeRule.onNodeWithText("Low light").assertIsDisplayed()
        composeRule.onNodeWithText("Monitoring active").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Camera visibility").performScrollTo()
        capture("low-light")

        composeRule.runOnIdle { lighting.value = FaceLightingAssessment(0, poorSinceMs = 0) }
        composeRule.onNodeWithText("Checking visibility").assertIsDisplayed()
        composeRule.runOnIdle {
            lighting.value = FaceLightingAssessment(2_000, LightingQuality.INSUFFICIENT, 10.0, 0.0, poorSinceMs = 0)
        }
        composeRule.onNodeWithText("Insufficient lighting").assertIsDisplayed()
        capture("confirmed-poor-light")

        // Missing face must use the existing face warning, even in a dim cabin
        // with a previously confirmed lighting problem.
        composeRule.runOnIdle { scenario.value = MonitoringScenario.FACE_TRACKING_UNAVAILABLE }
        composeRule.onNodeWithText("Face not visible").assertIsDisplayed()
        composeRule.onNodeWithText("Low light may", substring = true).assertIsDisplayed()
        capture("dark-face-missing")

        composeRule.runOnIdle { lowLight.value = false }
        composeRule.onNodeWithText("Face not visible").assertIsDisplayed()
        composeRule.onNodeWithText("position or an obstruction", substring = true).assertIsDisplayed()
        capture("face-missing")

        composeRule.runOnIdle { stream.value = StreamConnectionState.RECONNECTING }
        composeRule.onNodeWithText("Disconnected").assertIsDisplayed()
        composeRule.onNodeWithText("Camera disconnected.", substring = true).assertIsDisplayed()
        capture("disconnected")

        composeRule.runOnIdle {
            stream.value = StreamConnectionState.CONNECTED
            scenario.value = MonitoringScenario.NORMAL
            lowLight.value = false
            lighting.value = null
        }
        composeRule.onNodeWithText("Clear view").assertIsDisplayed()
        composeRule.onNodeWithText("Monitoring active").performScrollTo().assertIsDisplayed()
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "camera-visibility-$name.png")
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
