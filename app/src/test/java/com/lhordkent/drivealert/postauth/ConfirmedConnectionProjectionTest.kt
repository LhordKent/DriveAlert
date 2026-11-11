package com.lhordkent.drivealert.postauth

import com.lhordkent.drivealert.data.connection.TrustedContactConnection
import com.lhordkent.drivealert.data.local.entity.ConnectionStatus
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ConfirmedConnectionProjectionTest {
    @Test fun sharedRecordsWaitForServerApprovalTimestamp() {
        val optimisticApproval = TrustedContactConnection(
            connectionId="driver__contact", driverUserId="driver", trustedContactUserId="contact",
            driverName="Driver", driverEmail="driver@example.com", trustedContactName="Contact",
            trustedContactEmail="contact@example.com", requestedByUserId="driver",
            status=ConnectionStatus.APPROVED, requestedAtEpochMillis=1_000,
            approvedAtEpochMillis=null, declinedAtEpochMillis=null, revokedAtEpochMillis=null,
        )
        val pending = PostAuthUiState().withTrustedConnections(listOf(optimisticApproval))
        assertTrue("An unacknowledged timestamp must not start a shared-record query",pending.connectedDrivers.isEmpty())
        val confirmed = pending.withTrustedConnections(listOf(optimisticApproval.copy(approvedAtEpochMillis=5_000)))
        assertEquals("driver",confirmed.connectedDrivers.single().id)
        assertEquals(Instant.ofEpochMilli(5_000).atZone(ZoneId.systemDefault()).toLocalDateTime(),confirmed.connectedDrivers.single().connectedAt)
        assertTrue(confirmed.withTrustedConnections(listOf(optimisticApproval.copy(status=ConnectionStatus.REVOKED,approvedAtEpochMillis=5_000))).connectedDrivers.isEmpty())
    }
}
