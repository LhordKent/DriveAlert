package com.lhordkent.drivealert.postauth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.lhordkent.drivealert.data.connection.TrustedContactEmailMatch
import com.lhordkent.drivealert.ui.postauth.InviteConnectionScreen
import com.lhordkent.drivealert.ui.postauth.PostAuthTestTags
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrustedContactEmailInviteTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun searchShowsTheMatchAndOnlyExplicitSendInvitesIt() {
        val state = mutableStateOf(ConnectionInviteUiState())
        var searches = 0
        var invites = 0
        compose.setContent {
            DriveAlertTheme {
                InviteConnectionScreen(
                    inviteState = state.value,
                    onLookup = {},
                    onLookupEmail = {
                        searches++
                        state.value = ConnectionInviteUiState(
                            emailMatch = TrustedContactEmailMatch("Demo Contact", "contact@example.test", "match"),
                        )
                    },
                    onSend = { invites++ },
                    onClear = { state.value = ConnectionInviteUiState() },
                    onBack = {},
                )
            }
        }

        val email = compose.onNodeWithTag(PostAuthTestTags.INVITE_EMAIL)
        email.performTextReplacement("CONTACT@EXAMPLE.TEST")
        compose.onNodeWithText("Search").performScrollTo().performClick()
        compose.onNodeWithText("Demo Contact").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("contact@example.test").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, searches); assertEquals(0, invites) }

        email.performScrollTo().performTextReplacement("new@example.test")
        compose.onNodeWithText("Demo Contact").assertDoesNotExist()
        compose.onNodeWithText("Send invite").assertDoesNotExist()
        email.performTextReplacement("CONTACT@EXAMPLE.TEST")
        compose.onNodeWithText("Search").performScrollTo().performClick()
        compose.onNodeWithText("Send invite").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, searches); assertEquals(1, invites) }
        compose.onNodeWithText("Use a connection code").performScrollTo().performClick()
        compose.onNodeWithTag(PostAuthTestTags.INVITE_CODE).assertIsDisplayed()
    }
}
