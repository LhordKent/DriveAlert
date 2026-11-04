package com.lhordkent.drivealert.postauth

import com.google.firebase.firestore.FirebaseFirestoreException

internal fun sharedRecordsErrorMessage(error: Throwable): String {
    val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    return when (firestoreError?.code) {
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "Shared records could not refresh because the sharing service needs an update. Previously loaded records may still appear."
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Access to shared records was denied. Check that you are signed in to the approved Trusted Contact account."
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "Sign in again to refresh shared records."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Shared records could not refresh. Check your internet connection, then close and reopen the app. Previously loaded records may still appear."
        else -> "Shared records could not refresh. Close and reopen the app to try again. Previously loaded records may still appear."
    }
}
