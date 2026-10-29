package com.lhordkent.drivealert.postauth

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import com.lhordkent.drivealert.ui.postauth.*
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HistorySettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun bothRolesCanOpenHistorySettings() {
        val view = mutableStateOf(UserView.DRIVER)
        var opens = 0
        compose.setContent { DriveAlertTheme {
            ScrollableScreen("Settings", {}) {
                SettingsScreen(view.value, {}, {}, {}, {}, {}, {}, {}, onHistory = { opens++ })
            }
        } }
        compose.onNodeWithText("History & Storage").performScrollTo().performClick()
        compose.runOnIdle { view.value = UserView.TRUSTED_CONTACT }
        compose.onNodeWithText("History & Storage").performScrollTo().performClick()
        assertEquals(2, opens)
    }

    @Test fun customDaysAreValidatedAndNothingSavesBeforeSave() {
        var saved: Int? = null
        var calls = 0
        compose.setContent { DriveAlertTheme {
            HistorySettingsScreen(PostAuthUiState(historyPreferencesLoaded = true), { saved = it; calls++ }, {}, {}, {})
        } }
        compose.onNodeWithText("History stays on this phone until you delete it.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithText("Number of days").performScrollTo().performTextInput("0")
        compose.onNodeWithText("Save", substring = false).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Number of days").performScrollTo().performTextReplacement("3651")
        compose.onNodeWithText("Save", substring = false).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Number of days").performScrollTo().performTextReplacement("14")
        assertEquals(0, calls)
        compose.onNodeWithText("Save", substring = false).performScrollTo().assertIsEnabled().performClick()
        assertEquals(14, saved)
        assertEquals(1, calls)
    }

    @Test fun manualDeletionCanBeSelectedAndPreviewCanBeCancelled() {
        var called = false
        var days: Int? = 7
        val state = mutableStateOf(PostAuthUiState(historyPreferencesLoaded = true, historyRetentionDays = 7))
        var dismissed = false
        compose.setContent { DriveAlertTheme {
            HistorySettingsScreen(state.value, { called = true; days = it }, {}, { dismissed = true }, {})
        } }
        compose.onNodeWithText("Keep until I delete").performScrollTo().performClick()
        compose.onNodeWithText("Save", substring = false).performScrollTo().performClick()
        assertTrue(called); assertNull(days)
        compose.runOnIdle { state.value = state.value.copy(historyPreview = HistoryRetentionPreview(1, 12)) }
        compose.onNodeWithText("Apply automatic deletion?").assertIsDisplayed()
        compose.onNodeWithText("This removes 12 records", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test fun deleteHistoryRequiresConfirmation() {
        var deleted = false
        compose.setContent { DriveAlertTheme {
            HistoryDeleteButton("Clear history", "Only your history on this phone is removed.") { deleted = true }
        } }
        compose.onNodeWithText("Clear history").performClick()
        assertFalse(deleted)
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(deleted)
        compose.onNodeWithText("Clear history").performClick()
        compose.onAllNodesWithText("Clear history").onLast().performClick()
        assertTrue(deleted)
    }

    @Test fun historySettingsRemainUsableInDarkThemeAtLargeFont() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                DriveAlertTheme(darkTheme = true) {
                    HistorySettingsScreen(PostAuthUiState(historyPreferencesLoaded = true), {}, {}, {}, {})
                }
            }
        }
        capture("history-retention-top")
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithText("Number of days").performScrollTo().performTextInput("14")
        compose.onNodeWithText("Save", substring = false).performScrollTo().assertIsEnabled()
        capture("history-retention-bottom")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "history-retention-keyboard.png").outputStream().use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        Espresso.pressBack()
        compose.onNodeWithText("History & Storage").assertExists()
        compose.onNodeWithText("Save", substring = false).performScrollTo().assertIsEnabled()
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
