package com.lhordkent.drivealert.data.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.lhordkent.drivealert.postauth.SharingState
import com.lhordkent.drivealert.postauth.Stage3SyncRecord
import com.lhordkent.drivealert.postauth.SyncRecordKind
import com.lhordkent.drivealert.postauth.VisibleSign
import java.time.ZoneId
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface SharedStage3Repository {
    fun observeSharedRecords(
        driverUserId: String,
        approvedAtEpochMillis: Long,
    ): Flow<List<Stage3SyncRecord>>
}

class FirestoreSharedStage3Repository(
    private val firestore: FirebaseFirestore,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : SharedStage3Repository {
    override fun observeSharedRecords(
        driverUserId: String,
        approvedAtEpochMillis: Long,
    ): Flow<List<Stage3SyncRecord>> = callbackFlow {
        val registration = firestore.collection("users")
            .document(driverUserId)
            .collection(FirestoreStageSyncRemoteDataSource.STAGE_SYNC_COLLECTION)
            .whereEqualTo("driverUserId", driverUserId)
            .whereGreaterThanOrEqualTo("periodStartedAt", Timestamp(approvedAtEpochMillis / 1_000L, ((approvedAtEpochMillis % 1_000L) * 1_000_000L).toInt()))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    val records = snapshot?.documents.orEmpty().mapNotNull { document ->
                        val occurredAt = document.getTimestamp("periodStartedAt") ?: return@mapNotNull null
                        Stage3SyncRecord(
                            id = document.getString("stageSyncRecordId") ?: document.id,
                            occurredAt = occurredAt.toDate().toInstant().atZone(zoneId).toLocalDateTime(),
                            kind = when (document.getString("recordType")) {
                                "STAGE3_PERSISTENCE" -> SyncRecordKind.STAGE_3_PERSISTENCE
                                else -> SyncRecordKind.STAGE_3_TRANSITION
                            },
                            signs = (document.get("signs") as? List<*>)
                                .orEmpty()
                                .mapNotNull { value -> value?.toString()?.toVisibleSignOrNull() }
                                .toSet(),
                            sharingState = SharingState.SHARED,
                            sourceDriverId = driverUserId,
                            receivedAt = document.getTimestamp("uploadedAt")?.toLocalDateTime(zoneId),
                        )
                    }.sortedByDescending { it.occurredAt }
                    trySend(records)
                }
            }
        awaitClose { registration.remove() }
    }
}

private fun String.toVisibleSignOrNull(): VisibleSign? = when (this) {
    "PROLONGED_EYE_CLOSURE" -> VisibleSign.PROLONGED_EYE_CLOSURE
    "YAWNING" -> VisibleSign.YAWNING
    "HEAD_NODDING" -> VisibleSign.HEAD_NODDING
    else -> null
}

private fun Timestamp.toLocalDateTime(zoneId: ZoneId) = toDate().toInstant().atZone(zoneId).toLocalDateTime()
