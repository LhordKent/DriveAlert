package com.lhordkent.drivealert.data.connection

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.lhordkent.drivealert.data.local.dao.TrustedContactConnectionProjectionDao
import com.lhordkent.drivealert.data.local.entity.ConnectionStatus
import com.lhordkent.drivealert.data.local.entity.TrustedContactConnectionProjectionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

interface TrustedContactRepository {
    fun observeForDriver(driverUserId: String): Flow<List<TrustedContactConnection>>
    fun observeForTrustedContact(trustedContactUserId: String): Flow<List<TrustedContactConnection>>
    suspend fun resolveConnectionCode(code: String): ConnectionCodeTarget?
    suspend fun sendTrustedContactRequest(
        driverUserId: String,
        driverName: String,
        driverEmail: String,
        targetCode: String,
    )
    suspend fun acceptRequest(connectionId: String)
    suspend fun declineRequest(connectionId: String)
    suspend fun cancelRequest(connectionId: String)
    suspend fun revokeConnection(connectionId: String)
}

data class ConnectionCodeTarget(
    val userId: String,
    val displayName: String,
    val normalizedCode: String,
)

class ConnectionCodeNotFoundException : Exception()
class SelfConnectionException : Exception()
class ExistingConnectionException : Exception()
class ConnectionChangedException : Exception()

class FirestoreTrustedContactRepository(
    private val firestore: FirebaseFirestore,
    private val projectionDao: TrustedContactConnectionProjectionDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : TrustedContactRepository {
    private val connections = firestore.collection(CONNECTIONS_COLLECTION)
    private val codes = firestore.collection(CONNECTION_CODES_COLLECTION)
    private val projectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeForDriver(driverUserId: String): Flow<List<TrustedContactConnection>> = callbackFlow {
        val registration = connections.whereEqualTo("driverUserId", driverUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else {
                    val values = snapshot?.documents.orEmpty().map { it.toConnection() }
                    trySend(values.sortedByDescending { it.requestedAtEpochMillis })
                    projectionScope.launch { updateProjection(driverUserId, values) }
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeForTrustedContact(trustedContactUserId: String): Flow<List<TrustedContactConnection>> = callbackFlow {
        val registration = connections.whereEqualTo("trustedContactUserId", trustedContactUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else {
                    val values = snapshot?.documents.orEmpty().map { it.toConnection() }
                    trySend(values.sortedByDescending { it.requestedAtEpochMillis })
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun resolveConnectionCode(code: String): ConnectionCodeTarget? {
        val normalized = ConnectionCode.normalize(code)
        if (!ConnectionCode.isValid(normalized)) return null
        val snapshot = codes.document(normalized).get(Source.SERVER).await()
        if (!snapshot.exists() || snapshot.getBoolean("active") != true) return null
        return ConnectionCodeTarget(
            userId = snapshot.getString("ownerUserId") ?: return null,
            displayName = snapshot.getString("displayName").orEmpty().ifBlank { "DriveAlert user" },
            normalizedCode = normalized,
        )
    }

    override suspend fun sendTrustedContactRequest(
        driverUserId: String,
        driverName: String,
        driverEmail: String,
        targetCode: String,
    ) {
        val normalized = ConnectionCode.normalize(targetCode)
        if (!ConnectionCode.isValid(normalized)) throw ConnectionCodeNotFoundException()
        firestore.runTransaction { transaction ->
            val codeSnapshot = transaction.get(codes.document(normalized))
            if (!codeSnapshot.exists() || codeSnapshot.getBoolean("active") != true) {
                throw ConnectionCodeNotFoundException()
            }
            val trustedContactUserId = codeSnapshot.getString("ownerUserId") ?: throw ConnectionCodeNotFoundException()
            if (trustedContactUserId == driverUserId) throw SelfConnectionException()
            val targetName = codeSnapshot.getString("displayName").orEmpty().ifBlank { "DriveAlert user" }
            val connectionId = "${driverUserId}__${trustedContactUserId}"
            val reference = connections.document(connectionId)
            val existing = transaction.get(reference)
            val oldContactInvitation = existing.getString("requestedByUserId") == trustedContactUserId
            val status = existing.getString("status")
            if (status == ConnectionStatus.APPROVED.name || (status == ConnectionStatus.PENDING.name && !oldContactInvitation)) {
                throw ExistingConnectionException()
            }
            val requestState = mapOf(
                "requestedByUserId" to driverUserId,
                "targetConnectionCode" to normalized,
                "status" to ConnectionStatus.PENDING.name,
                "requestedAt" to FieldValue.serverTimestamp(),
                "approvedAt" to null,
                "declinedAt" to null,
                "revokedAt" to null,
            )
            if (existing.exists()) {
                val updatedState = if (oldContactInvitation) {
                    requestState + mapOf("driverName" to driverName, "driverEmail" to driverEmail)
                } else requestState
                transaction.update(reference, updatedState)
            } else {
                transaction.set(reference, mapOf(
                    "connectionId" to connectionId,
                    "driverUserId" to driverUserId,
                    "trustedContactUserId" to trustedContactUserId,
                    "driverName" to driverName,
                    "driverEmail" to driverEmail,
                    "trustedContactName" to targetName,
                    "trustedContactEmail" to "",
                ) + requestState)
            }
        }.await()
    }

    override suspend fun acceptRequest(connectionId: String) = updateStatus(connectionId, ConnectionStatus.APPROVED, "approvedAt")
    override suspend fun declineRequest(connectionId: String) = updateStatus(connectionId, ConnectionStatus.DECLINED, "declinedAt")
    override suspend fun cancelRequest(connectionId: String) = revokeFrom(connectionId, ConnectionStatus.PENDING)
    override suspend fun revokeConnection(connectionId: String) = revokeFrom(connectionId, ConnectionStatus.APPROVED)

    private suspend fun revokeFrom(connectionId: String, expectedStatus: ConnectionStatus) {
        val reference = connections.document(connectionId)
        firestore.runTransaction { transaction ->
            // Cancel and disconnect both write REVOKED. Check the starting state in the
            // same transaction so a cancel cannot disconnect a just-approved contact.
            if (transaction.get(reference).getString("status") != expectedStatus.name) {
                throw ConnectionChangedException()
            }
            transaction.update(reference, mapOf(
                "status" to ConnectionStatus.REVOKED.name,
                "revokedAt" to FieldValue.serverTimestamp(),
            ))
        }.await()
    }

    suspend fun updateProjection(driverUserId: String, values: List<TrustedContactConnection>) {
        projectionDao.upsertAll(values.map {
            TrustedContactConnectionProjectionEntity(
                connectionId = it.connectionId,
                driverUserId = it.driverUserId,
                trustedContactUserId = it.trustedContactUserId,
                requestedByUserId = it.requestedByUserId,
                status = it.status,
                requestedAtEpochMillis = it.requestedAtEpochMillis,
                approvedAtEpochMillis = it.approvedAtEpochMillis,
                declinedAtEpochMillis = it.declinedAtEpochMillis,
                revokedAtEpochMillis = it.revokedAtEpochMillis,
                lastRefreshedAtEpochMillis = clock(),
            )
        }.filter { it.driverUserId == driverUserId })
    }

    private suspend fun updateStatus(connectionId: String, status: ConnectionStatus, timestampField: String) {
        connections.document(connectionId).update(mapOf("status" to status.name, timestampField to FieldValue.serverTimestamp())).await()
    }

    private fun DocumentSnapshot.toConnection(): TrustedContactConnection {
        val driverUserId = requireNotNull(getString("driverUserId"))
        return TrustedContactConnection(
            connectionId = getString("connectionId") ?: id,
            driverUserId = driverUserId,
            trustedContactUserId = requireNotNull(getString("trustedContactUserId")),
            driverName = getString("driverName").orEmpty(),
            driverEmail = getString("driverEmail").orEmpty(),
            trustedContactName = getString("trustedContactName").orEmpty(),
            trustedContactEmail = getString("trustedContactEmail").orEmpty(),
            requestedByUserId = getString("requestedByUserId") ?: driverUserId,
            targetConnectionCode = getString("targetConnectionCode").orEmpty(),
            status = getString("status")?.let(ConnectionStatus::valueOf) ?: ConnectionStatus.PENDING,
            requestedAtEpochMillis = getTimestamp("requestedAt")?.toEpochMillis() ?: 0L,
            approvedAtEpochMillis = getTimestamp("approvedAt")?.toEpochMillis(),
            declinedAtEpochMillis = getTimestamp("declinedAt")?.toEpochMillis(),
            revokedAtEpochMillis = getTimestamp("revokedAt")?.toEpochMillis(),
        )
    }

    private fun Timestamp.toEpochMillis(): Long = seconds * 1_000L + nanoseconds / 1_000_000L

    companion object {
        const val CONNECTIONS_COLLECTION = "trustedContactConnections"
        const val CONNECTION_CODES_COLLECTION = "connectionCodes"
    }

}
