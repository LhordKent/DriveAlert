package com.lhordkent.drivealert.data.connection

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.lhordkent.drivealert.data.local.dao.TrustedContactConnectionProjectionDao
import com.lhordkent.drivealert.data.local.entity.ConnectionStatus
import com.lhordkent.drivealert.data.local.entity.TrustedContactConnectionProjectionEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

interface TrustedContactRepository {
    fun observeForDriver(driverUserId: String): Flow<List<TrustedContactConnection>>
    fun observeForTrustedContact(trustedContactUserId: String): Flow<List<TrustedContactConnection>>
    suspend fun sendRequestByEmail(email: String, inviterRole: ConnectionParticipantRole)
    suspend fun acceptRequest(connectionId: String)
    suspend fun declineRequest(connectionId: String)
    suspend fun cancelRequest(connectionId: String)
    suspend fun revokeConnection(connectionId: String)
}

enum class ConnectionParticipantRole { DRIVER, TRUSTED_CONTACT }

class FirestoreTrustedContactRepository(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val projectionDao: TrustedContactConnectionProjectionDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : TrustedContactRepository {
    private val connections = firestore.collection(CONNECTIONS_COLLECTION)
    private val projectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeForDriver(driverUserId: String): Flow<List<TrustedContactConnection>> = callbackFlow {
        val registration = connections.whereEqualTo("driverUserId", driverUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
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
                if (error != null) {
                    close(error)
                } else {
                    val values = snapshot?.documents.orEmpty().map { it.toConnection() }
                    trySend(values.sortedByDescending { it.requestedAtEpochMillis })
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun sendRequestByEmail(email: String, inviterRole: ConnectionParticipantRole) {
        functions.getHttpsCallable(SEND_REQUEST_FUNCTION)
            .call(mapOf("email" to email.trim().lowercase(), "inviterRole" to inviterRole.name))
            .await()
    }

    override suspend fun acceptRequest(connectionId: String) {
        updateStatus(connectionId, ConnectionStatus.APPROVED, "approvedAt")
    }

    override suspend fun declineRequest(connectionId: String) {
        updateStatus(connectionId, ConnectionStatus.DECLINED, "declinedAt")
    }

    override suspend fun cancelRequest(connectionId: String) {
        updateStatus(connectionId, ConnectionStatus.REVOKED, "revokedAt")
    }

    override suspend fun revokeConnection(connectionId: String) {
        updateStatus(connectionId, ConnectionStatus.REVOKED, "revokedAt")
    }

    suspend fun updateProjection(driverUserId: String, values: List<TrustedContactConnection>) {
        projectionDao.upsertAll(
            values.map {
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
            }.filter { it.driverUserId == driverUserId },
        )
    }

    private suspend fun updateStatus(connectionId: String, status: ConnectionStatus, timestampField: String) {
        connections.document(connectionId).update(
            mapOf(
                "status" to status.name,
                timestampField to FieldValue.serverTimestamp(),
            ),
        ).await()
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
        const val SEND_REQUEST_FUNCTION = "sendTrustedContactRequest"
    }
}
