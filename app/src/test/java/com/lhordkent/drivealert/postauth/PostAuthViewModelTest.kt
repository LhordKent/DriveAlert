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

        assertEquals(6, state.driverAlerts.size)
        assertEquals(3, state.driverAlerts.count { it.occurredAt.toLocalDate() == today })
        assertTrue(state.driverAlerts.any { it.signs.size > 1 })
        assertTrue(state.driverSyncRecords.all { it.kind == SyncRecordKind.STAGE_3_TRANSITION || it.kind == SyncRecordKind.STAGE_3_PERSISTENCE })
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
    fun invitationValidationBlocksSelfAndDuplicates() {
        val viewModel = PostAuthViewModel()
        viewModel.setAccountEmail("driver@example.com")

        assertEquals("Enter a valid email address", viewModel.sendConnectionRequest(UserView.DRIVER, "bad"))
        assertEquals("Use a different email address", viewModel.sendConnectionRequest(UserView.DRIVER, "driver@example.com"))
        assertTrue(viewModel.sendConnectionRequest(UserView.DRIVER, "mara.santos@gmail.com")?.contains("already exists") == true)
        assertNull(viewModel.sendConnectionRequest(UserView.DRIVER, "new.contact@example.com"))
        assertTrue(viewModel.state.driverOutgoingRequests.any { it.email == "new.contact@example.com" })
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
        val viewModel = PostAuthViewModel()
        val event = viewModel.state.driverAlerts.first()
        val record = viewModel.state.driverSyncRecords.first()

        viewModel.updateSharingState(event.id, SharingState.FAILED)
        viewModel.updateSharingState(record.id, SharingState.FAILED)

        assertEquals(SharingState.FAILED, viewModel.state.driverSyncRecords.first { it.id == record.id }.sharingState)
        assertTrue(viewModel.state.driverSyncRecords.none { it.id == event.id })
    }

    @Test
    fun contactApprovalSharesOnlyFutureStage3SynchronizationRecords() {
        val state = PostAuthViewModel.seedState(LocalDate.of(2026, 8, 31))
        val approvalTime = state.driverSyncRecords.maxOf { it.occurredAt }.plusMinutes(1)
        val newlyApproved = Contact("new", "New Contact", "new@example.com", approvalTime)
        val oldRecord = state.driverSyncRecords.first()
        val futureRecord = oldRecord.copy(id = "future", occurredAt = approvalTime.plusMinutes(1))

        assertFalse(oldRecord.isEligibleFor(newlyApproved))
        assertTrue(futureRecord.isEligibleFor(newlyApproved))
    }

    @Test
    fun mockSetupReducersStayInMemory() {
        val viewModel = PostAuthViewModel()
        viewModel.reconnectDevice()
        viewModel.completeAlignmentCheck()
        viewModel.completeCalibration()

        assertEquals(DeviceConnectionState.RECONNECTING, viewModel.state.deviceConnection)
        assertFalse(viewModel.state.alignmentReady)
        assertEquals(CalibrationState.IN_PROGRESS, viewModel.state.calibrationState)

        viewModel.reconnectDevice()
        viewModel.completeAlignmentCheck()
        viewModel.completeCalibration()
        assertEquals(DeviceConnectionState.CONNECTED, viewModel.state.deviceConnection)
        assertTrue(viewModel.state.alignmentReady)
        assertEquals(CalibrationState.READY, viewModel.state.calibrationState)
    }
}
