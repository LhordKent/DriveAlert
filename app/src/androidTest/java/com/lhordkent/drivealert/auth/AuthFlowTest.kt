package com.lhordkent.drivealert.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.lhordkent.drivealert.ui.DriveAlertApp
import com.lhordkent.drivealert.ui.auth.AuthTestTags
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun navigationConnectsAllFourScreens() {
        launch()

        composeRule.onNodeWithText("Continue with Google").assertDoesNotExist()
        composeRule.onNodeWithText("Sign In").performClick()
        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertDoesNotExist()
        composeRule.onNodeWithText("Forgot password?").performClick()
        composeRule.onNodeWithText("Forgot password?").assertIsDisplayed()
        composeRule.onNodeWithText("Back to Sign In").performClick()
        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").performClick()
        composeRule.onNodeWithText("Sign in").performScrollTo().performClick()
        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun realOperationMessagesArePresentedTruthfully() {
        composeRule.setContent {
            DriveAlertTheme {
                DriveAlertApp(
                    callbacks = AuthCallbacks(),
                    operationState = AuthOperationState(successMessage = "Signed in successfully."),
                )
            }
        }
        composeRule.onNodeWithText("Success: Signed in successfully.").assertIsDisplayed()
    }

    @Test
    fun invalidSignInShowsErrorsAndBlocksCallback() {
        var submissions = 0
        launch(AuthCallbacks(onSignIn = { _, _ -> submissions++ }))
        composeRule.onNodeWithText("Sign In").performClick()

        composeRule.onNodeWithTag(AuthTestTags.PRIMARY_ACTION).performClick()
        composeRule.onNodeWithText("Email is required").assertIsDisplayed()
        composeRule.onNodeWithText("Password is required").assertIsDisplayed()
        assertEquals(0, submissions)
    }

    @Test
    fun validSignInImeActionInvokesCallbackAndStaysOnScreen() {
        var receivedEmail = ""
        var receivedPassword = ""
        launch(
            AuthCallbacks(onSignIn = { email, password ->
                receivedEmail = email
                receivedPassword = password
            }),
        )
        composeRule.onNodeWithText("Sign In").performClick()
        composeRule.onNodeWithTag(AuthTestTags.EMAIL).performTextInput("lhord@drivealert.app")
        composeRule.onNodeWithTag(AuthTestTags.PASSWORD).performTextInput("safe-drive")
        composeRule.onNodeWithTag(AuthTestTags.PASSWORD).performImeAction()

        composeRule.runOnIdle {
            assertEquals("lhord@drivealert.app", receivedEmail)
            assertEquals("safe-drive", receivedPassword)
        }
        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun passwordVisibilityControlChangesItsLabel() {
        launch()
        composeRule.onNodeWithText("Sign In").performClick()
        composeRule.onNodeWithTag(AuthTestTags.PASSWORD).performTextInput("safe-drive")
        composeRule.onNodeWithText("Show").performClick()
        composeRule.onNodeWithText("Hide").assertIsDisplayed()
    }

    @Test
    fun validSignUpInvokesCallbackWithOptionalFieldsBlank() {
        var received: SignUpInput? = null
        launch(AuthCallbacks(onSignUp = { received = it }))
        composeRule.onNodeWithText("Create Account").performClick()
        composeRule.onNodeWithTag(AuthTestTags.FIRST_NAME).performTextInput("Lhord")
        composeRule.onNodeWithTag(AuthTestTags.LAST_NAME).performTextInput("Kent")
        composeRule.onNodeWithTag(AuthTestTags.EMAIL).performTextInput("lhord@drivealert.app")
        composeRule.onNodeWithTag(AuthTestTags.PASSWORD).performTextInput("safe-drive")
        composeRule.onNodeWithTag(AuthTestTags.CONFIRM_PASSWORD).performTextInput("safe-drive")
        composeRule.onNodeWithTag(AuthTestTags.PRIMARY_ACTION).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals("Lhord", received?.firstName)
            assertEquals("", received?.middleName)
            assertEquals("", received?.phoneNumber)
        }
    }

    @Test
    fun resetPasswordValidatesThenInvokesCallback() {
        var receivedEmail = ""
        launch(AuthCallbacks(onResetPassword = { receivedEmail = it }))
        composeRule.onNodeWithText("Sign In").performClick()
        composeRule.onNodeWithText("Forgot password?").performClick()
        composeRule.onNodeWithTag(AuthTestTags.PRIMARY_ACTION).performClick()
        composeRule.onNodeWithText("Email is required").assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTestTags.EMAIL).performTextInput("lhord@drivealert.app")
        composeRule.onNodeWithTag(AuthTestTags.PRIMARY_ACTION).performClick()

        composeRule.runOnIdle { assertEquals("lhord@drivealert.app", receivedEmail) }
        composeRule.onNodeWithText("Forgot password?").assertIsDisplayed()
    }

    private fun launch(callbacks: AuthCallbacks = AuthCallbacks()) {
        composeRule.setContent {
            DriveAlertTheme {
                DriveAlertApp(callbacks = callbacks)
            }
        }
    }
}
