package com.lhordkent.drivealert.data.connection

import com.lhordkent.drivealert.data.local.entity.ConnectionStatus

data class TrustedContactConnection(
    val connectionId: String,
    val driverUserId: String,
    val trustedContactUserId: String,
    val driverName: String,
    val driverEmail: String,
    val trustedContactName: String,
    val trustedContactEmail: String,
    val requestedByUserId: String,
    val targetConnectionCode: String = "",
    val status: ConnectionStatus,
    val requestedAtEpochMillis: Long,
    val approvedAtEpochMillis: Long?,
    val declinedAtEpochMillis: Long?,
    val revokedAtEpochMillis: Long?,
)
