package com.lhordkent.drivealert.data.device

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.lhordkent.drivealert.data.connection.ConnectionCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class DeviceAccessRepository(
    private val firestore: FirebaseFirestore,
    private val dao: DeviceAccessDao,
    private val credentials: com.lhordkent.drivealert.device.DeviceCredentialStore,
    private val currentUid: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid },
) : DeviceAccessPolicy {
    private val suspended = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private fun requireUser(uid: String) = check(currentUid() == uid) { "The signed-in account changed. Try again." }

    fun observe(uid: String): Flow<List<DeviceAccessGrant>> = dao.observe(uid)

    override suspend fun allowed(uid: String, deviceId: String): Boolean {
        if (currentUid() != uid || uid in suspended || DeviceIdentity.canonical(deviceId) != deviceId) return false
        val grant = dao.get(uid, deviceId)
        return currentUid() == uid && uid !in suspended && grant?.allowed == true && credentials.load(uid, deviceId) != null
    }

    private suspend fun save(uid: String, grant: DeviceAccessGrant) {
        requireUser(uid)
        dao.saveVerified(grant)
    }

    suspend fun refresh(uid: String): DeviceAccessOverview = online(uid) {
        // Explicit server reads cannot grant access from pending writes or stale disk cache.
        val owned = firestore.collection("deviceOwnership").whereEqualTo("ownerUserId", uid).get(Source.SERVER).await()
        val incoming = firestore.collection("deviceDriverAccess").whereEqualTo("driverUserId", uid).get(Source.SERVER).await()
        val managed = firestore.collection("deviceDriverAccess").whereEqualTo("ownerUserId", uid).get(Source.SERVER).await()
        check(listOf(owned, incoming, managed).all { !it.metadata.hasPendingWrites() && !it.metadata.isFromCache }) {
            "Wait for the sharing service to confirm device access, then retry."
        }
        requireUser(uid)
        val owners = owned.documents.map { DeviceOwnership(it.id, uid, it.getString("displayName") ?: "DriveAlert device") }
        val received = incoming.documents.map(::membership)
        // Cache secrets before granting offline access. Recipients cannot read pending credentials.
        owners.forEach { if (credentials.load(uid, it.deviceId)?.owner != true) loadCredential(uid, it.deviceId) }
        received.filter { it.status == "ACTIVE" }.forEach {
            if (credentials.load(uid, it.deviceId)?.serial != it.revision - 1) loadCredential(uid, it.deviceId)
        }
        for (member in managed.documents) {
            val status = member.getString("status")
            val revision = requireNotNull(member.getLong("revision"))
            val serial = revision - when (status) { "PENDING" -> 0; "REMOVED", "LEFT" -> 2; else -> 1 }
            val floor = if (status in listOf("PENDING", "ACTIVE")) serial - 1 else serial
            credentials.setRevocation(uid, requireNotNull(member.getString("deviceId")),
                com.lhordkent.drivealert.device.DeviceCredentialCrypto.accountId(requireNotNull(member.getString("driverUserId"))), floor.coerceAtLeast(0))
        }
        owners.forEach { save(uid, DeviceAccessGrant(uid, it.deviceId, uid, it.displayName, "OWNER", 1, System.currentTimeMillis())) }
        received.forEach { save(uid, DeviceAccessGrant(uid, it.deviceId, it.ownerUserId, it.deviceDisplayName, it.status, it.revision, System.currentTimeMillis())) }
        suspended.remove(uid)
        DeviceAccessOverview(owners, received, managed.documents.map(::membership))
    }

    suspend fun isClaimed(uid: String, id: String): Boolean = online(uid) {
        require(DeviceIdentity.canonical(id) == id)
        val marker = firestore.collection("devices").document(id).get(Source.SERVER).await()
        check(!marker.metadata.isFromCache && !marker.metadata.hasPendingWrites())
        marker.exists()
    }

    suspend fun claim(uid: String, id: String, label: String) = online(uid) {
        require(DeviceIdentity.canonical(id) == id)
        val marker = firestore.collection("devices").document(id)
        val ownership = firestore.collection("deviceOwnership").document(id)
        val ownerSecret = com.lhordkent.drivealert.device.DeviceCredential(id,
            com.lhordkent.drivealert.device.DeviceCredentialCrypto.accountId(uid), com.lhordkent.drivealert.device.DeviceCredentialCrypto.randomSecret(), true)
        firestore.runTransaction { tx ->
            val existing = tx.get(marker)
            if (existing.exists()) {
                check(tx.get(ownership).getString("ownerUserId") == uid) { "This device is registered to another account." }
            } else {
                tx.set(marker, mapOf("deviceId" to id, "claimed" to true, "createdAt" to FieldValue.serverTimestamp(), "schemaVersion" to 1))
                tx.set(ownership, mapOf("deviceId" to id, "ownerUserId" to uid, "displayName" to label.take(80), "createdAt" to FieldValue.serverTimestamp(), "schemaVersion" to 1))
                tx.set(firestore.collection("deviceCredentials").document("${id}__$uid"), credentialValues(ownerSecret, uid, uid))
            }
        }.await()
        requireUser(uid)
        refresh(uid)
    }

    /** One owner-scoped operation ID is persisted by the caller before confirmation. */
    suspend fun invite(uid: String, deviceId: String, code: String, operationId: String) = online(uid) {
        val normalized = ConnectionCode.normalize(code)
        require(ConnectionCode.isValid(normalized)) { "Enter a complete DriveAlert connection code." }
        val codeRef = firestore.collection("connectionCodes").document(normalized)
        val eventRef = firestore.collection("users").document(uid).collection("deviceSubscriptionEvents").document(operationId)
        val ownerRef = firestore.collection("deviceOwnership").document(deviceId)
        val ownerSecret = credentials.load(uid, deviceId) ?: loadCredential(uid, deviceId)
        firestore.runTransaction { tx ->
            val owner = tx.get(ownerRef)
            val target = tx.get(codeRef)
            val event = tx.get(eventRef)
            check(owner.getString("ownerUserId") == uid) { "Only the primary driver can add drivers." }
            check(target.getBoolean("active") == true) { "This connection code is no longer active." }
            val driverUid = target.getString("ownerUserId") ?: error("Account was not found.")
            check(driverUid != uid) { "The primary driver is already included." }
            val accessRef = firestore.collection("deviceDriverAccess").document("${deviceId}__$driverUid")
            val existing = tx.get(accessRef)
            val ownerProfile = tx.get(firestore.collection("users").document(uid))
            val ownerCode = tx.get(firestore.collection("connectionCodes").document(requireNotNull(ownerProfile.getString("connectionCode"))))
            if (event.exists()) {
                check(event.getString("deviceId") == deviceId && event.getString("driverUserId") == driverUid) { "This confirmation belongs to a different invitation." }
            } else {
                check(existing.getString("status") !in listOf("PENDING", "ACTIVE")) { "This driver is already invited or authorized." }
                val serial = (existing.getLong("revision") ?: 0) + 1
                val certificate = com.lhordkent.drivealert.device.DeviceCredentialCrypto.delegate(ownerSecret, driverUid, serial, operationId)
                tx.set(eventRef, mapOf("deviceId" to deviceId, "ownerUserId" to uid, "driverUserId" to driverUid,
                    "mode" to "SIMULATED", "kind" to "ACTIVATION", "createdAt" to FieldValue.serverTimestamp()))
                tx.set(accessRef, mapOf("deviceId" to deviceId, "ownerUserId" to uid, "driverUserId" to driverUid,
                    "ownerDisplayName" to ownerCode.getString("displayName").orEmpty(),
                    "driverDisplayName" to target.getString("displayName").orEmpty(), "deviceDisplayName" to owner.getString("displayName").orEmpty(),
                    "status" to "PENDING", "revision" to ((existing.getLong("revision") ?: 0) + 1), "targetConnectionCode" to normalized,
                    "requestedAt" to FieldValue.serverTimestamp(), "acceptedAt" to null, "endedAt" to null,
                    "billingMode" to "SIMULATED", "demoActivationId" to operationId))
                tx.set(firestore.collection("deviceCredentials").document("${deviceId}__$driverUid"), credentialValues(certificate, uid, driverUid))
            }
        }.await()
        refresh(uid)
    }

    suspend fun transition(uid: String, id: String, next: String) = online(uid) {
        require(next in listOf("ACTIVE", "DECLINED", "CANCELLED", "REMOVED", "LEFT"))
        val ref = firestore.collection("deviceDriverAccess").document(id)
        firestore.runTransaction { tx ->
            val current = tx.get(ref)
            val status = current.getString("status")
            check(current.getString("ownerUserId") == uid || current.getString("driverUserId") == uid)
            if (status != next) {
                val owner = current.getString("ownerUserId") == uid
                val valid = if (owner) (status == "PENDING" && next == "CANCELLED") || (status == "ACTIVE" && next == "REMOVED")
                    else (status == "PENDING" && next in listOf("ACTIVE", "DECLINED")) || (status == "ACTIVE" && next == "LEFT")
                check(valid) { "This invitation changed. Refresh and try again." }
                tx.update(ref, mapOf("status" to next, "revision" to (requireNotNull(current.getLong("revision")) + 1),
                    (if (next == "ACTIVE") "acceptedAt" else "endedAt") to FieldValue.serverTimestamp()))
            }
        }.await()
        refresh(uid)
    }

    suspend fun mapLegacy(uid: String, legacyId: String, canonicalId: String) {
        requireUser(uid)
        dao.mapIdentity(DeviceIdentityMapping(uid, legacyId, canonicalId))
    }

    private suspend fun loadCredential(uid: String, deviceId: String): com.lhordkent.drivealert.device.DeviceCredential {
        val doc = firestore.collection("deviceCredentials").document("${deviceId}__$uid").get(Source.SERVER).await()
        check(!doc.metadata.hasPendingWrites() && !doc.metadata.isFromCache)
        check(doc.exists()) { "Device authorization is not ready. Ask the primary driver to send a new invitation." }
        val secret = com.lhordkent.drivealert.device.DeviceCredential(deviceId, requireNotNull(doc.getString("accountId")),
            requireNotNull(doc.getString("secret")), doc.getString("kind") == "OWNER", requireNotNull(doc.getLong("serial")), doc.getString("certificate").orEmpty())
        requireUser(uid)
        check(secret.accountId == com.lhordkent.drivealert.device.DeviceCredentialCrypto.accountId(uid))
        credentials.save(uid, secret)
        return secret
    }

    private fun credentialValues(secret: com.lhordkent.drivealert.device.DeviceCredential, ownerUid: String, driverUid: String): Map<String, Any> = mapOf(
        "deviceId" to secret.deviceId, "ownerUserId" to ownerUid, "driverUserId" to driverUid, "accountId" to secret.accountId,
        "secret" to secret.secret, "kind" to if (secret.owner) "OWNER" else "DRIVER", "serial" to secret.serial,
        "certificate" to secret.certificate, "createdAt" to FieldValue.serverTimestamp())

    private suspend fun <T> online(uid: String, action: suspend () -> T): T {
        requireUser(uid)
        try {
            return withTimeout(12_000) { action().also { requireUser(uid) } }
        } catch (error: FirebaseFirestoreException) {
            if (error.code in listOf(FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED)) suspended.add(uid)
            throw error
        }
    }

    private fun membership(doc: DocumentSnapshot) = DeviceDriverMembership(doc.id,
        requireNotNull(doc.getString("deviceId")), requireNotNull(doc.getString("ownerUserId")),
        requireNotNull(doc.getString("driverUserId")), doc.getString("driverDisplayName").orEmpty(),
        doc.getString("deviceDisplayName").orEmpty(), requireNotNull(doc.getString("status")), requireNotNull(doc.getLong("revision")), doc.getString("ownerDisplayName") ?: "Primary driver")
}
