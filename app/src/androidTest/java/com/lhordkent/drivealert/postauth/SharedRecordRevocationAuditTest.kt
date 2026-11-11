package com.lhordkent.drivealert.postauth

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.lhordkent.drivealert.auth.AuthCallbacks
import com.lhordkent.drivealert.auth.AuthSessionState
import com.lhordkent.drivealert.auth.AuthenticatedUser
import com.lhordkent.drivealert.auth.AuthenticationEntry
import com.lhordkent.drivealert.data.profile.UserRole
import com.lhordkent.drivealert.ui.DriveAlertApp
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import org.junit.Rule
import org.junit.Test

class SharedRecordRevocationAuditTest {
    @get:Rule val compose = createComposeRule()

    @Test fun u582_openRecordIsRemovedWhenApprovedConnectionDisappears() {
        val state = mutableStateOf(PostAuthViewModel.seedState().copy(userRole = UserRole.BOTH))
        compose.setContent {
            DriveAlertTheme {
                DriveAlertApp(
                    callbacks = AuthCallbacks(),
                    sessionState = AuthSessionState(
                        isResolving = false,
                        user = AuthenticatedUser("audit-contact", "Audit Contact", "audit@example.com"),
                        entry = AuthenticationEntry.ACCOUNT_CREATED,
                    ),
                    postAuthState = state.value,
                    onChooseView = { state.value = state.value.copy(activeView = it) },
                )
            }
        }
        compose.onNodeWithText("Continue as Trusted Contact").performScrollTo().performClick()
        compose.onNodeWithText("Adrian Cruz").performScrollTo().performClick()
        compose.onNodeWithText("7:42 AM", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Shared by Adrian Cruz").assertIsDisplayed()
        // Apply the same production projection used when the online connection listener removes approval.
        compose.runOnIdle { state.value = state.value.withTrustedConnections(emptyList()) }
        compose.onNodeWithText("This record is no longer in your history.").assertIsDisplayed()
        compose.onNodeWithText("Shared by Adrian Cruz").assertDoesNotExist()
    }
}
