package com.lhordkent.drivealert.postauth

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostAuthViewModelTest {
    @Test
    fun seedDataIsInternallyConsistent() {
        val today = LocalDate.of(2026, 8, 31)
        val state = PostAuthViewModel.seedState(today)

        assertTrue(state.driverAlerts.isEmpty())
        assertTrue(state.driverSyncRecords.isEmpty())
        assertTrue(state.connectedDrivers.flatMap { it.sharedRecords }.all { it.sharingState == SharingState.SHARED })
    }

    @Test
    fun monitoringScenariosDeriveRequiredAvailability() {
        assertEquals("Monitoring active", MonitoringScenario.NORMAL.presentation().headline)
        assertFalse(MonitoringScenario.EYE_UNAVAILABLE.presentation().eyeAvailable)
        assertTrue(MonitoringScenario.EYE_UNAVAILABLE.presentation().yawningAvailable)
        assertFalse(MonitoringScenario.YAWNING_UNAVAILABLE.presentation().yawningAvailable)
        assertEquals("Monitoring paused", MonitoringScenario.EYE_AND_YAWNING_UNAVAILABLE.presentation().headline)
        assertFalse(MonitoringScenario.FACE_TRACKING_UNAVAILABLE.presentation().headAvailable)
    }

    @Test
    fun symmetricRequestsUpdateOnlyInMemoryState() {
        val viewModel = PostAuthViewModel()
        val driverRequest = viewModel.state.driverIncomingRequests.first()
        val trustedRequest = viewModel.state.trustedIncomingRequests.first()

        viewModel.acceptRequest(UserView.DRIVER, driverRequest.id)
        assertTrue(viewModel.state.approvedContacts.any { it.email == driverRequest.email })
        assertFalse(viewModel.state.driverIncomingRequests.any { it.id == driverRequest.id })

        viewModel.acceptRequest(UserView.TRUSTED_CONTACT, trustedRequest.id)
        assertTrue(viewModel.state.connectedDrivers.any { it.email == trustedRequest.email })
        assertFalse(viewModel.state.trustedIncomingRequests.any { it.id == trustedRequest.id })
    }

    @Test
    fun connectionCodesNormalizeFormatAndRejectIncompleteValues() {
        val generated = com.lhordkent.drivealert.data.connection.ConnectionCode.generate()
        val formatted = com.lhordkent.drivealert.data.connection.ConnectionCode.format(generated)

        assertTrue(com.lhordkent.drivealert.data.connection.ConnectionCode.isValid(generated))
        assertEquals(generated, com.lhordkent.drivealert.data.connection.ConnectionCode.normalize(formatted.lowercase()))
        assertFalse(com.lhordkent.drivealert.data.connection.ConnectionCode.isValid("DA-short"))

        val viewModel = PostAuthViewModel()
        viewModel.lookupConnectionCode("DA-short")
        assertTrue(viewModel.state.connectionInvite.errorMessage?.contains("complete") == true)
    }

    @Test
    fun disconnectPreservesDriverHistory() {
        val viewModel = PostAuthViewModel()
        val originalAlerts = viewModel.state.driverAlerts
        viewModel.chooseView(UserView.TRUSTED_CONTACT)
        viewModel.completeDriverSetup()
        viewModel.removeConnection(UserView.TRUSTED_CONTACT, "adrian")
        assertFalse(viewModel.state.connectedDrivers.any { it.id == "adrian" })
        assertEquals(originalAlerts, viewModel.state.driverAlerts)

        assertEquals(UserView.TRUSTED_CONTACT, viewModel.state.activeView)
        assertTrue(viewModel.state.driverSetupComplete)
    }

    @Test
    fun onlySynchronizationRecordSharingStateIsChanged() {
        val event = AlertEvent("alert", java.time.LocalDateTime.now(), setOf(VisibleSign.YAWNING), WarningStage.STAGE_1)
        val record = Stage3SyncRecord("sync", java.time.LocalDateTime.now(), SyncRecordKind.STAGE_3_TRANSITION, setOf(VisibleSign.YAWNING), SharingState.PENDING)
        val viewModel = PostAuthViewModel(initialState = PostAuthUiState(driverAlerts = listOf(event), driverSyncRecords = listOf(record)))

        viewModel.updateSharingState(event.id, SharingState.FAILED)
        viewModel.updateSharingState(record.id, SharingState.FAILED)

        assertEquals(SharingState.FAILED, viewModel.state.driverSyncRecords.first { it.id == record.id }.sharingState)
        assertTrue(viewModel.state.driverSyncRecords.none { it.id == event.id })
    }

    @Test
    fun contactApprovalSharesOnlyFutureStage3SynchronizationRecords() {
        val oldRecord = Stage3SyncRecord("sync", java.time.LocalDateTime.of(2026, 8, 31, 12, 0), SyncRecordKind.STAGE_3_TRANSITION, setOf(VisibleSign.YAWNING), SharingState.PENDING)
        val approvalTime = oldRecord.occurredAt.plusMinutes(1)
        val newlyApproved = Contact("new", "New Contact", "new@example.com", approvalTime)
        val futureRecord = oldRecord.copy(id = "future", occurredAt = approvalTime.plusMinutes(1))

        assertFalse(oldRecord.isEligibleFor(newlyApproved))
        assertTrue(futureRecord.isEligibleFor(newlyApproved))
    }

    @Test
    fun setupCompletionStartsFalse() {
        val viewModel = PostAuthViewModel()
        assertFalse(viewModel.state.driverSetupComplete)
    }
}
