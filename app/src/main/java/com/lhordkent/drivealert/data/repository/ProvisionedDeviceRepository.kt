package com.lhordkent.drivealert.data.repository

import com.lhordkent.drivealert.data.local.dao.ProvisionedDeviceDao
import com.lhordkent.drivealert.data.local.entity.ProvisionedDeviceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ProvisionedDevice(
    val deviceId: String,
    val driverUserId: String,
    val hostname: String?,
    val lastKnownIp: String,
    val streamPort: Int = 81,
    val streamPath: String = "/stream",
    val lastConnectedAtEpochMillis: Long,
    val sessionToken: String? = null,
) {
    val streamUrl: String get() = "http://$lastKnownIp:$streamPort$streamPath"
    override fun toString(): String = "ProvisionedDevice(deviceId=$deviceId, driverUserId=$driverUserId, session=REDACTED)"
}

interface ProvisionedDeviceRepository {
    fun observeActive(driverUserId: String): Flow<ProvisionedDevice?>
    suspend fun active(driverUserId: String): ProvisionedDevice?
    suspend fun saveActive(device: ProvisionedDevice)
}

class RoomProvisionedDeviceRepository(private val dao: ProvisionedDeviceDao,
    private val sessionToken: (String, String) -> String? = { _, _ -> null }) : ProvisionedDeviceRepository {
    override fun observeActive(driverUserId: String): Flow<ProvisionedDevice?> =
        dao.observeActive(driverUserId).map { it?.toDomain() }

    override suspend fun active(driverUserId: String): ProvisionedDevice? = dao.getActive(driverUserId)?.toDomain()

    override suspend fun saveActive(device: ProvisionedDevice) {
        dao.insertAndActivate(device.toEntity())
    }

    private fun ProvisionedDeviceEntity.toDomain() = ProvisionedDevice(
        deviceId, driverUserId, hostname, lastKnownIp, streamPort, streamPath, lastConnectedAtEpochMillis, sessionToken(driverUserId, deviceId),
    )

    private fun ProvisionedDevice.toEntity() = ProvisionedDeviceEntity(
        deviceId, driverUserId, hostname, lastKnownIp, streamPort, streamPath, lastConnectedAtEpochMillis, true,
    )
}
