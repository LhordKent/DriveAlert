package com.lhordkent.drivealert.provisioning

import java.util.UUID

/** Mirrors drivealert-iot/PROVISIONING_PROTOCOL.md. Keep all Android UUIDs here. */
object ProvisioningProtocol {
    val serviceUuid: UUID = UUID.fromString("7f9c1000-8b6e-4c5a-9d2f-6a3b7c8d0001")
    val ssidUuid: UUID = UUID.fromString("7f9c1001-8b6e-4c5a-9d2f-6a3b7c8d0001")
    val passwordUuid: UUID = UUID.fromString("7f9c1002-8b6e-4c5a-9d2f-6a3b7c8d0001")
    val applyUuid: UUID = UUID.fromString("7f9c1003-8b6e-4c5a-9d2f-6a3b7c8d0001")
    val statusUuid: UUID = UUID.fromString("7f9c1004-8b6e-4c5a-9d2f-6a3b7c8d0001")
    val clientConfigurationUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val applyCommand = "APPLY"
    const val queryCredentialsCommand = "QUERY_CREDENTIALS"
    const val connectSavedCommand = "CONNECT_SAVED"
    const val advertisedNamePrefix = "DriveAlert-"
}

class WifiCredentials private constructor(
    val ssid: String,
    val password: String,
) {
    override fun toString(): String = "WifiCredentials(ssid=${ssid.take(32)}, password=<redacted>)"

    companion object {
        fun create(ssid: String, password: String): Result<WifiCredentials> {
            val ssidBytes = ssid.toByteArray(Charsets.UTF_8).size
            val passwordBytes = password.toByteArray(Charsets.UTF_8).size
            return if (ssidBytes !in 1..32 || (passwordBytes != 0 && passwordBytes !in 8..63)) {
                Result.failure(IllegalArgumentException("SSID or password length is invalid."))
            } else {
                Result.success(WifiCredentials(ssid, password))
            }
        }
    }
}
