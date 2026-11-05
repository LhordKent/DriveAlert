package com.lhordkent.drivealert.data.device

import androidx.room.Entity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Public BLE address is an identifier for this prototype, never a secret. */
object DeviceIdentity {
    fun canonical(address: String): String? = address.replace(":", "").uppercase(java.util.Locale.ROOT)
        .takeIf { it.matches(Regex("[0-9A-F]{12}")) }
}

@Entity(tableName = "device_access", primaryKeys = ["userId", "deviceId"])
data class DeviceAccessGrant(
    val userId: String,
    val deviceId: String,
    val ownerUserId: String,
    val displayName: String,
    val status: String,
    val revision: Long,
    val verifiedAt: Long,
) {
    val allowed: Boolean get() = status == "OWNER" || status == "ACTIVE"
}

@Entity(tableName = "device_identity_mappings", primaryKeys = ["userId", "legacyDeviceId"])
data class DeviceIdentityMapping(val userId: String, val legacyDeviceId: String, val canonicalDeviceId: String)

@Dao
abstract class DeviceAccessDao {
    @Query("SELECT * FROM device_access WHERE userId = :uid")
    abstract fun observe(uid: String): Flow<List<DeviceAccessGrant>>

    @Query("SELECT * FROM device_access WHERE userId = :uid")
    abstract suspend fun all(uid: String): List<DeviceAccessGrant>

    @Query("SELECT * FROM device_access WHERE userId = :uid AND deviceId = :deviceId")
    abstract suspend fun get(uid: String, deviceId: String): DeviceAccessGrant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insert(grant: DeviceAccessGrant)

    @Transaction
    open suspend fun saveVerified(grant: DeviceAccessGrant) {
        val previous = get(grant.userId, grant.deviceId)
        if (previous == null || grant.revision > previous.revision ||
            (grant.revision == previous.revision && grant.status == previous.status)) insert(grant)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun mapIdentity(mapping: DeviceIdentityMapping)
}

data class DeviceOwnership(val deviceId: String, val ownerUserId: String, val displayName: String)
data class DeviceDriverMembership(
    val id: String,
    val deviceId: String,
    val ownerUserId: String,
    val driverUserId: String,
    val driverDisplayName: String,
    val deviceDisplayName: String,
    val status: String,
    val revision: Long,
    val ownerDisplayName: String = "Primary driver",
)
data class DeviceAccessOverview(
    val owned: List<DeviceOwnership> = emptyList(),
    val incoming: List<DeviceDriverMembership> = emptyList(),
    val managed: List<DeviceDriverMembership> = emptyList(),
)

interface DeviceAccessPolicy {
    suspend fun allowed(uid: String, deviceId: String): Boolean
}
