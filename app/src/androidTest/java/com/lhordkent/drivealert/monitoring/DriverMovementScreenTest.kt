package com.lhordkent.drivealert.monitoring

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.postauth.MonitoringScenario
import com.lhordkent.drivealert.ui.postauth.MonitoringPreviewScreen
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class DriverMovementScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inactivityStatusAndSpeechFailureDoNotPauseMonitoring() {
        val state = mutableStateOf(DriverVisionUiState(
            monitoring = ActiveMonitoringState(isActive = true),
            movement = DriverMovementAssessment(DriverMovementStatus.OBSERVING),
        ))
        compose.setContent { DriveAlertTheme {
            MonitoringPreviewScreen(MonitoringScenario.NORMAL, monitoring = state.value.monitoring,
                vision = state.value, onExit = {})
        } }
        compose.onNodeWithText("Checking movement").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(movement = DriverMovementAssessment(
            DriverMovementStatus.NO_MOVEMENT, 25_000, WarningDeliveryStatus.FAILED)) }
        compose.onNodeWithText("No movement detected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No qualifying face movement detected for 10 seconds. Drowsiness monitoring continues.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Movement advisory could not be sent to the onboard speaker.")
            .performScrollTo().assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "movement-advisory-screen.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Monitoring active").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(movement = DriverMovementAssessment(
            DriverMovementStatus.RECENT_MOVEMENT)) }
        compose.onNodeWithText("Movement detected").performScrollTo().assertIsDisplayed()
    }
}
