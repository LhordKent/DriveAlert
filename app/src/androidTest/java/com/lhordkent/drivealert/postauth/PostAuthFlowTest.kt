package com.lhordkent.drivealert.postauth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lhordkent.drivealert.auth.AuthCallbacks
import com.lhordkent.drivealert.auth.AuthSessionState
import com.lhordkent.drivealert.auth.AuthenticatedUser
import com.lhordkent.drivealert.auth.AuthenticationEntry
import com.lhordkent.drivealert.monitoring.ActiveMonitoringState
import com.lhordkent.drivealert.monitoring.WarningDeliveryStatus
import com.lhordkent.drivealert.ui.DriveAlertApp
import com.lhordkent.drivealert.data.profile.UserRole
import com.lhordkent.drivealert.ui.postauth.AlertDetailScreen
import com.lhordkent.drivealert.ui.postauth.MonitoringPreviewScreen
import com.lhordkent.drivealert.ui.postauth.DriverHomeScreen
import com.lhordkent.drivealert.detection.DriverVisionUiState
import com.lhordkent.drivealert.detection.frame.StreamConnectionState
import com.lhordkent.drivealert.detection.model.CalibrationResult
import com.lhordkent.drivealert.ui.postauth.Stage3SyncRecordDetailScreen
import com.lhordkent.drivealert.ui.postauth.RequestsScreen
import com.lhordkent.drivealert.ui.theme.DriveAlertTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class PostAuthFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun freshAuthenticationRequiresLiveCalibrationBeforeMonitoring() {
        launchSignedIn(AuthenticationEntry.ACCOUNT_CREATED)

        composeRule.onNodeWithText("Choose your view").assertIsDisplayed()
        composeRule.onNodeWithText("Continue as Driver").performScrollTo().performClick()
        composeRule.onNodeWithText("Set up DriveAlert").assertIsDisplayed()
        composeRule.onNodeWithText("Device connection").performScrollTo().performClick()
        composeRule.onNodeWithText("Connect your DriveAlert camera").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Driver calibration").performScrollTo().performClick()
        composeRule.onNodeWithText("Personalized calibration").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Continue to Home").performScrollTo().performClick()
        composeRule.onNodeWithText("Setup needed").assertIsDisplayed()
        composeRule.onNodeWithText("Start Monitoring").performScrollTo().performClick()
        composeRule.onNodeWithText("Personalized calibration").assertIsDisplayed()
    }

    @Test
    fun freshAuthenticationConnectsTrustedContactNavigation() {
        launchSignedIn(AuthenticationEntry.ACCOUNT_CREATED)

        composeRule.onNodeWithText("Continue as Trusted Contact").performScrollTo().performClick()
        composeRule.onNodeWithText("Connected Drivers").assertIsDisplayed()
        composeRule.onNodeWithText("Adrian Cruz").performScrollTo().performClick()
        composeRule.onNodeWithText("Stage 3 synchronization timeline").assertIsDisplayed()
        composeRule.onNodeWithText("7:42 AM", substring = true).performScrollTo().performClick()
        composeRule.onNodeWithText("Warning Stage").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Shared by Adrian Cruz").assertIsDisplayed()
    }

    @Test
    fun everyMonitoringAvailabilityScenarioHasPlainLanguageStatus() {
        val expected = mapOf(
            MonitoringScenario.NORMAL to "Monitoring active",
            MonitoringScenario.EYE_UNAVAILABLE to "Eye monitoring is unavailable",
            MonitoringScenario.YAWNING_UNAVAILABLE to "Yawning monitoring is unavailable",
            MonitoringScenario.EYE_AND_YAWNING_UNAVAILABLE to "Insufficient facial information is available",
            MonitoringScenario.FACE_TRACKING_UNAVAILABLE to "Face tracking is unavailable",
        )
        val selected = mutableStateOf(MonitoringScenario.NORMAL)
        composeRule.setContent {
            DriveAlertTheme {
                MonitoringPreviewScreen(
                    scenario = selected.value,
                    monitoring = ActiveMonitoringState(isActive = true),
                    onExit = {},
                )
            }
        }
        expected.forEach { (scenario, text) ->
            composeRule.runOnIdle { selected.value = scenario }
            composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun monitoringPreviewCanPresentEveryLiveWarningStage() {
        val monitoring = mutableStateOf(
            ActiveMonitoringState(
                isActive = true,
                currentStage = WarningStage.STAGE_1,
                confirmedEventCount = 3,
                latestSigns = setOf(VisibleSign.YAWNING),
                warningDelivery = WarningDeliveryStatus.HARDWARE_PENDING,
            ),
        )
        composeRule.setContent {
            DriveAlertTheme {
                MonitoringPreviewScreen(MonitoringScenario.NORMAL, monitoring.value, {})
            }
        }

        composeRule.onNodeWithText("Warning Stage 1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Yawning").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Onboard speaker delivery pending", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { monitoring.value = monitoring.value.copy(currentStage = WarningStage.STAGE_2) }
        composeRule.onNodeWithText("fixed rest advisory", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { monitoring.value = monitoring.value.copy(currentStage = WarningStage.STAGE_3) }
        composeRule.onNodeWithText("Maximum Driver warning", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun driverHomeSeparatesMonitoringToggleFromActivityNavigation() {
        var startCount = 0
        var stopCount = 0
        var activityCount = 0
        var accessoryMode = com.lhordkent.drivealert.monitoring.DriverAccessoryMode.NONE
        val active = mutableStateOf(false)
        val calibration = CalibrationResult(
            calibrationId = "test", schemaVersion = 3, calibratedAtTimestampMs = 1,
            neutralEar = 0.3, closedEyeEar = 0.1, earThreshold = 0.2,
            neutralMar = 0.1, openMouthMar = 0.5, marThreshold = 0.3,
            neutralHeadPitchDegrees = 0.0, downwardPitchMultiplier = 1.0,
        )
        composeRule.setContent {
            DriveAlertTheme {
                DriverHomeScreen(
                    state = PostAuthUiState(),
                    vision = DriverVisionUiState(
                        streamState = StreamConnectionState.CONNECTED,
                        activeCalibration = calibration,
                        monitoring = ActiveMonitoringState(isActive = active.value),
                    ),
                    onStartMonitoring = { selected -> accessoryMode = selected; startCount++; active.value = true },
                    onStopMonitoring = { stopCount++; active.value = false },
                    onOpenMonitoringActivity = { activityCount++ },
                    onSetupDevice = {},
                    onOpenAlerts = {},
                )
            }
        }

        composeRule.onNodeWithText("Start Monitoring").performClick()
        composeRule.onNodeWithText("Face mask").performClick()
        composeRule.onNodeWithText("Start monitoring").performClick()
        composeRule.onNodeWithText("Stop Monitoring").assertIsDisplayed()
        composeRule.onNodeWithText("Monitoring Activity").performClick()
        composeRule.onNodeWithText("Stop Monitoring").performClick()
        composeRule.runOnIdle {
            assertEquals(1, startCount)
            assertEquals(1, activityCount)
            assertEquals(1, stopCount)
            assertEquals(com.lhordkent.drivealert.monitoring.DriverAccessoryMode.MASK, accessoryMode)
        }
    }

    @Test
    fun alertDetailsUseInterventionLanguageAndSharingRules() {
        val stage1 = com.lhordkent.drivealert.postauth.AlertEvent("stage1", java.time.LocalDateTime.now(), setOf(com.lhordkent.drivealert.postauth.VisibleSign.YAWNING), WarningStage.STAGE_1)
        val stage3 = stage1.copy(id = "stage3", stage = WarningStage.STAGE_3)
        val selected = mutableStateOf(stage1)

        composeRule.setContent { DriveAlertTheme { AlertDetailScreen(selected.value, {}) } }
        composeRule.onNodeWithText("Stage 1: Initial Warning").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("not eligible for Trusted Contact synchronization", substring = true).performScrollTo().assertIsDisplayed()

        composeRule.runOnIdle { selected.value = stage3 }
        composeRule.onNodeWithText("Stage 3: Maximum Warning Stage").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("drowsiness severity", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun stage3DetailSupportsEverySharingState() {
        val original = com.lhordkent.drivealert.postauth.Stage3SyncRecord(
            "sync", java.time.LocalDateTime.now(), com.lhordkent.drivealert.postauth.SyncRecordKind.STAGE_3_TRANSITION,
            setOf(com.lhordkent.drivealert.postauth.VisibleSign.YAWNING), SharingState.PENDING,
        )
        val selected = mutableStateOf(original.copy(sharingState = SharingState.NO_CONTACT))
        composeRule.setContent { DriveAlertTheme { Stage3SyncRecordDetailScreen(selected.value, {}) } }

        listOf(
            SharingState.NO_CONTACT to "No approved contact",
            SharingState.PENDING to "Pending",
            SharingState.SHARED to "Shared",
            SharingState.FAILED to "Failed",
        ).forEach { (state, expectedText) ->
            composeRule.runOnIdle { selected.value = original.copy(sharingState = state) }
            composeRule.onNodeWithText(expectedText).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun initialCloudLoadingDoesNotRenderFalseEmptyState() {
        composeRule.setContent {
            DriveAlertTheme {
                RequestsScreen(
                    incoming = emptyList(), outgoing = emptyList(), onAccept = {}, onDecline = {}, onCancel = {}, isLoading = true,
                )
            }
        }
        composeRule.onNodeWithText("Loading requests…").assertIsDisplayed()
        composeRule.onAllNodesWithText("No pending requests").assertCountEquals(0)
    }

    @Test
    fun restoredSessionAndRoleSwitchUseTheExistingNavigationHost() {
        launchSignedIn(AuthenticationEntry.RESTORED)

        composeRule.onNodeWithText("Set up DriveAlert").assertIsDisplayed()
        composeRule.onNodeWithText("Continue to Home").performScrollTo().performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Switch to Trusted Contact view").performScrollTo().performClick()
        composeRule.onNodeWithText("Connected Drivers").assertIsDisplayed()
        composeRule.onNodeWithText("Requests", substring = true).performClick()
        composeRule.onNodeWithText("Requests to you").assertIsDisplayed()
    }

    private fun launchSignedIn(entry: AuthenticationEntry) {
        composeRule.setContent {
            DriveAlertTheme {
                val postAuthViewModel: PostAuthViewModel = viewModel {
                    PostAuthViewModel(initialState = PostAuthViewModel.seedState().copy(userRole = UserRole.DRIVER))
                }
                DriveAlertApp(
                    callbacks = AuthCallbacks(),
                    sessionState = AuthSessionState(
                        isResolving = false,
                        user = AuthenticatedUser("test-user", "Lhord Kent", "lhord@drivealert.app"),
                        entry = entry,
                    ),
                    postAuthState = postAuthViewModel.state,
                    onSetAccountEmail = postAuthViewModel::setAccountEmail,
                    onSetProfileName = postAuthViewModel::setProfileDisplayName,
                    onChooseView = postAuthViewModel::chooseView,
                    onCompleteSetup = postAuthViewModel::completeDriverSetup,
                    onFilterChange = postAuthViewModel::selectAlertFilter,
                    onSendTrustedContactRequest = postAuthViewModel::sendTrustedContactRequest,
                    onSendDriverRequest = postAuthViewModel::sendDriverRequest,
                    onAcceptDriverIncomingRequest = postAuthViewModel::acceptDriverIncomingRequest,
                    onAcceptTrustedIncomingRequest = postAuthViewModel::acceptTrustedIncomingRequest,
                    onDeclineDriverIncomingRequest = postAuthViewModel::declineDriverIncomingRequest,
                    onDeclineTrustedIncomingRequest = postAuthViewModel::declineTrustedIncomingRequest,
                    onCancelDriverOutgoingRequest = postAuthViewModel::cancelDriverOutgoingRequest,
                    onCancelTrustedOutgoingRequest = postAuthViewModel::cancelTrustedOutgoingRequest,
                    onRevokeDriverContact = postAuthViewModel::revokeDriverContact,
                    onDisconnectDriver = postAuthViewModel::disconnectDriver,
                    onWarningSoundChange = postAuthViewModel::selectWarningSound,
                    onVolumeChange = postAuthViewModel::selectPreferredVolume,
                    onNotificationsChange = postAuthViewModel::updateNotifications,
                    onClearPostAuth = postAuthViewModel::clearForSignOut,
                )
            }
        }
    }
}
