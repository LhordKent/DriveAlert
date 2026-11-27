package com.lhordkent.drivealert.postauth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.lhordkent.drivealert.auth.AuthCallbacks
import com.lhordkent.drivealert.auth.AuthSessionState
import com.lhordkent.drivealert.auth.AuthenticatedUser
import com.lhordkent.drivealert.auth.AuthenticationEntry
import com.lhordkent.drivealert.data.connection.ConnectionContactDetails
import com.lhordkent.drivealert.data.profile.UserRole
import com.lhordkent.drivealert.ui.DriveAlertApp
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConnectionListsTest {
    @get:Rule val compose = createComposeRule()

    private val longEmail = "${"long-name".repeat(7)}@example.test"
    private val number = "09123456789"
    private val state = mutableStateOf(PostAuthViewModel.seedState().copy(userRole = UserRole.BOTH, driverSetupComplete = true))

    private fun open(view: UserView) {
        state.value = state.value.copy(activeView = view)
        compose.setContent {
            DriveAlertTheme {
                DriveAlertApp(
                    callbacks = AuthCallbacks(),
                    sessionState = AuthSessionState(
                        isResolving = false,
                        user = AuthenticatedUser("test-viewer", "Test Viewer", "viewer@example.test"),
                        entry = AuthenticationEntry.RESTORED,
                    ),
                    postAuthState = state.value,
                    loadConnectionDetails = { _, ids ->
                        ids.filter { it == "mara" || it == "adrian" }.associateWith {
                            ConnectionContactDetails(longEmail, number)
                        }
                    },
                    onCancelDriverOutgoingRequest = { id ->
                        state.value = state.value.copy(
                            driverOutgoingRequests = state.value.driverOutgoingRequests.filterNot { it.id == id },
                        )
                    },
                )
            }
        }
    }

    @Test
    fun driverCanOpenInviteAndManageSentRequestsSeparately() {
        open(UserView.DRIVER)
        compose.onNodeWithText("Contacts").performClick()
        compose.onNodeWithText("Invite").assertIsDisplayed()
        compose.onNodeWithText("Sent requests").assertIsDisplayed()
        compose.onNodeWithText("Mara Santos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(longEmail).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(number).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Approved").assertDoesNotExist()
        compose.onNodeWithText("Lea Torres").assertDoesNotExist()
        compose.onNodeWithText("Invite").performScrollTo().performClick()
        compose.onNodeWithText("Invite a Trusted Contact").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Sent requests").performScrollTo().performClick()
        compose.onNodeWithText("Lea Torres").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel request").performScrollTo().performClick()
        compose.onNodeWithText("No sent requests").assertIsDisplayed()
    }

    @Test
    fun trustedContactSeesPeerDetailsAndCanStillOpenRecords() {
        open(UserView.TRUSTED_CONTACT)
        compose.onNodeWithText(longEmail).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(number).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Phone number unavailable").performScrollTo().assertIsDisplayed()
        val nameBounds = compose.onNodeWithText("Adrian Cruz", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val phoneBounds = compose.onNodeWithText(number, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val actionBounds = compose.onAllNodesWithText("Disconnect", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        assertTrue("Disconnect is beside the contact details", actionBounds.left > nameBounds.left)
        assertTrue("Disconnect is centered on the contact details", abs(actionBounds.center.y - (nameBounds.top + phoneBounds.bottom) / 2) <= 2f)
        compose.onAllNodesWithText("Disconnect")[0].performScrollTo().performClick()
        compose.onNodeWithText("Disconnect Driver?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Adrian Cruz").performScrollTo().performClick()
        val record = state.value.connectedDrivers.first().sharedRecords.first()
        compose.onNodeWithText(record.occurredAt.format(com.lhordkent.drivealert.ui.postauth.sharedAlertDateTimeFormatter))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { state.value = state.value.withTrustedConnections(emptyList()) }
        compose.onNodeWithText(number).assertDoesNotExist()
    }
}
