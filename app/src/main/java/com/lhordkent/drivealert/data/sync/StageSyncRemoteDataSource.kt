package com.lhordkent.drivealert.data.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.lhordkent.drivealert.data.connection.FirestoreTrustedContactRepository
import com.lhordkent.drivealert.data.local.entity.ConnectionStatus
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordType
import com.lhordkent.drivealert.data.local.entity.StageSyncRecordWithSigns
import kotlinx.coroutines.tasks.await

interface StageSyncRemoteDataSource {
    suspend fun hasEligibleApprovedContact(driverUserId: String, periodStartedAtEpochMillis: Long): Boolean
    suspend fun upload(record: StageSyncRecordWithSigns)
}

class FirestoreStageSyncRemoteDataSource(
    private val firestore: FirebaseFirestore,
) : StageSyncRemoteDataSource {
    override suspend fun hasEligibleApprovedContact(
        driverUserId: String,
        periodStartedAtEpochMillis: Long,
    ): Boolean {
        val snapshot = firestore.collection(FirestoreTrustedContactRepository.CONNECTIONS_COLLECTION)
            .whereEqualTo("driverUserId", driverUserId)
            .get(Source.SERVER)
            .await()
        return snapshot.documents.any { document ->
            val approvedAt = document.getTimestamp("approvedAt")
            document.getString("status") == ConnectionStatus.APPROVED.name &&
                approvedAt != null && approvedAt.toDate().time <= periodStartedAtEpochMillis
        }
    }

    override suspend fun upload(record: StageSyncRecordWithSigns) {
        val value = record.record
        val document = firestore.collection("users")
            .document(value.driverUserId)
            .collection(STAGE_SYNC_COLLECTION)
            .document(value.firestoreDocumentId)
        if (document.get(Source.SERVER).await().exists()) return

        document.set(
            mapOf(
                "stageSyncRecordId" to value.stageSyncRecordId,
                "driverUserId" to value.driverUserId,
                "sessionId" to value.sessionId,
                "recordType" to value.recordType.name,
                "periodStartedAt" to com.google.firebase.Timestamp(value.periodStartedAtEpochMillis / 1_000L, ((value.periodStartedAtEpochMillis % 1_000L) * 1_000_000L).toInt()),
                "periodEndedAt" to value.periodEndedAtEpochMillis?.let { com.google.firebase.Timestamp(it / 1_000L, ((it % 1_000L) * 1_000_000L).toInt()) },
                "eventCount" to value.eventCount,
                "signs" to record.signs.map { it.signType.name }.sorted(),
                "warningStage" to "STAGE_3",
                "warningStageAtDetection" to 3,
                "syncStatus" to "SYNCED",
                "createdAtClient" to com.google.firebase.Timestamp(value.createdAtEpochMillis / 1_000L, ((value.createdAtEpochMillis % 1_000L) * 1_000_000L).toInt()),
                "uploadedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        firestore.waitForPendingWrites().await()
    }

    companion object {
        const val STAGE_SYNC_COLLECTION = "stageSyncRecords"
    }
}
