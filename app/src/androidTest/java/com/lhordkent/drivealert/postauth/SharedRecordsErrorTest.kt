package com.lhordkent.drivealert.postauth

import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRecordsErrorTest {
    @Test
    fun missingIndexDoesNotBlameInternetEvenWhenWrapped() {
        val error = RuntimeException(FirebaseFirestoreException(
            "The query requires an index.", FirebaseFirestoreException.Code.FAILED_PRECONDITION,
        ))
        val message = sharedRecordsErrorMessage(error)
        assertTrue(message.contains("sharing service needs an update"))
        assertFalse(message.contains("internet"))
    }

    @Test
    fun deniedAccessExplainsAccountRequirement() {
        val message = sharedRecordsErrorMessage(FirebaseFirestoreException(
            "Denied", FirebaseFirestoreException.Code.PERMISSION_DENIED,
        ))
        assertTrue(message.contains("approved Trusted Contact account"))
        assertFalse(message.contains("internet"))
    }

    @Test
    fun unavailableServiceOffersRecoveryAndExplainsPreviouslyLoadedRecords() {
        val message = sharedRecordsErrorMessage(FirebaseFirestoreException(
            "Unavailable", FirebaseFirestoreException.Code.UNAVAILABLE,
        ))
        assertTrue(message.contains("internet connection"))
        assertTrue(message.contains("close and reopen the app"))
        assertTrue(message.contains("Previously loaded records"))
    }
}
