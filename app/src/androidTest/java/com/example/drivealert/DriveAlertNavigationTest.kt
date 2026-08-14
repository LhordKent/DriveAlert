package com.example.drivealert

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class DriveAlertNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun starterOpensSignInAndForgotPassword() {
        composeRule.onNodeWithText("Stay alert. Drive safer.").assertIsDisplayed()
        composeRule.onNodeWithText("Sign In").performClick()
        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
        composeRule.onNodeWithText("Forgot password?").performClick()
        composeRule.onNodeWithText("Forgot password").assertIsDisplayed()
    }

    @Test
    fun googleDemoOpensRoleSelection() {
        composeRule.onNodeWithText("Continue with Google").performClick()
        composeRule.onNodeWithText("How will you use DriveAlert?").assertIsDisplayed()
        composeRule.onNodeWithText("Continue as Driver").assertIsDisplayed()
        composeRule.onNodeWithText("Continue as Trusted Contact").assertIsDisplayed()
    }

    @Test
    fun trustedContactFlowOpensConnectedDrivers() {
        composeRule.onNodeWithText("Continue with Google").performClick()
        composeRule.onNodeWithText("Continue as Trusted Contact").performClick()
        composeRule.onNodeWithText("Connected Drivers").assertIsDisplayed()
        composeRule.onNodeWithText("Lhord").assertIsDisplayed()
    }
}
