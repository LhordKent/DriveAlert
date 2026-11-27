package com.lhordkent.drivealert.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertTextContains
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
    fun unverifiedSessionShowsVerificationInsteadOfAppNavigation() {
        var checks = 0
        composeRule.setContent {
            DriveAlertTheme {
                DriveAlertApp(
                    callbacks = AuthCallbacks(onCheckVerification = { checks++ }),
                    sessionState = AuthSessionState(isResolving = false, verificationEmail = "pending@gmail.com"),
                )
            }
        }
        composeRule.onNodeWithText("Verify your email").assertIsDisplayed()
        composeRule.onNodeWithText("I've verified my email").performClick()
        composeRule.runOnIdle { assertEquals(1, checks) }
        composeRule.onNodeWithText("Choose your view").assertDoesNotExist()
    }

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
    fun validSignUpRequiresPhoneAndAllowsBlankMiddleNameAndSuffix() {
        var received: SignUpInput? = null
        launch(AuthCallbacks(onSignUp = { received = it }))
        composeRule.onNodeWithText("Create Account").performClick()
        composeRule.onNodeWithTag(AuthTestTags.FIRST_NAME).performTextInput("Lhord")
        composeRule.onNodeWithTag(AuthTestTags.LAST_NAME).performTextInput("Kent")
        composeRule.onNodeWithTag(AuthTestTags.EMAIL).performScrollTo().performTextInput("lhord@drivealert.app")
        composeRule.onNodeWithTag(AuthTestTags.PASSWORD).performScrollTo().performTextInput("safe-drive")
        composeRule.onNodeWithTag(AuthTestTags.CONFIRM_PASSWORD).performScrollTo().performTextInput("safe-drive")
        composeRule.onNodeWithTag(AuthTestTags.PRIMARY_ACTION).performScrollTo().performClick()

        composeRule.onNodeWithText("Phone number is required").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(null, received) }
        composeRule.onNodeWithTag(AuthTestTags.PHONE).performScrollTo().performTextInput("12345678901")
        composeRule.onNodeWithTag(AuthTestTags.PRIMARY_ACTION).performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(null, received) }
        composeRule.onNodeWithTag(AuthTestTags.PASSWORD).performScrollTo().performTextReplacement("Safe-drive")
        composeRule.onNodeWithTag(AuthTestTags.CONFIRM_PASSWORD).performScrollTo().performTextReplacement("Safe-drive")
        composeRule.onNodeWithTag(AuthTestTags.PRIMARY_ACTION).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals("Lhord", received?.firstName)
            assertEquals("", received?.middleName)
            assertEquals("", received?.suffix)
            assertEquals("12345678901", received?.phoneNumber)
        }
    }

    @Test
    fun signUpRemovesNumbersFromNamesButKeepsThemInTheSavedSuffix() {
        var received: SignUpInput? = null
        launch(AuthCallbacks(onSignUp = { received = it }))
        composeRule.onNodeWithText("Create Account").performClick()
        composeRule.onNodeWithTag(AuthTestTags.FIRST_NAME).performScrollTo().performTextInput("Mára1")
        composeRule.onNodeWithTag(AuthTestTags.MIDDLE_NAME).performScrollTo().performTextInput("Anne-Marie٢")
        composeRule.onNodeWithTag(AuthTestTags.LAST_NAME).performScrollTo().performTextInput("O'Neil3")
        composeRule.onNodeWithTag(AuthTestTags.SUFFIX).performScrollTo().assertTextContains("Suffix (optional)")
            .performTextInput(" 3rd ")
        composeRule.onNodeWithTag(AuthTestTags.PHONE).performScrollTo().performTextInput("12345678901")
        composeRule.onNodeWithTag(AuthTestTags.EMAIL).performScrollTo().performTextInput("signup@example.test")
        composeRule.onNodeWithTag(AuthTestTags.PASSWORD).performScrollTo().performTextInput("Safe-drive")
        composeRule.onNodeWithTag(AuthTestTags.CONFIRM_PASSWORD).performScrollTo().performTextInput("Safe-drive")
        composeRule.onNodeWithTag(AuthTestTags.PRIMARY_ACTION).performScrollTo().performClick()

        composeRule.runOnIdle {
            val signup = checkNotNull(received)
            assertEquals("Mára", signup.firstName)
            assertEquals("Anne-Marie", signup.middleName)
            assertEquals("O'Neil", signup.lastName)
            assertEquals("3rd", signup.suffix)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val storeName = "signup-suffix-test"
            val preferences = context.getSharedPreferences(storeName, 0)
            try {
                PendingRegistrationStore(preferences).save("with-suffix", signup)
                PendingRegistrationStore(preferences).save("without-suffix", signup.copy(suffix = " "))
                val reopened = PendingRegistrationStore(context.getSharedPreferences(storeName, 0))
                assertEquals("3rd", reopened.read("with-suffix", signup.email)?.suffix)
                assertEquals(null, checkNotNull(reopened.read("without-suffix", signup.email)).suffix)
            } finally {
                context.deleteSharedPreferences(storeName)
            }
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
