package com.drivealert.mvp.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

interface LedRepository {
    fun observeDevice(
        onState: (DeviceState) -> Unit,
        onError: (Throwable) -> Unit,
    )

    fun stopObserving()
    fun writeCommand(command: String, callback: (Result<Unit>) -> Unit)
}

class FirebaseLedRepository(
    private val deviceId: String = "esp32_01",
) : LedRepository {
    private val reference: DatabaseReference? = runCatching {
        FirebaseDatabase.getInstance(DATABASE_URL).reference.child("devices").child(deviceId)
    }.getOrNull()
    private var listener: ValueEventListener? = null

    override fun observeDevice(onState: (DeviceState) -> Unit, onError: (Throwable) -> Unit) {
        stopObserving()
        val deviceReference = reference
        if (deviceReference == null) {
            onError(IllegalStateException(CONFIGURATION_ERROR))
            return
        }
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onState(
                    DeviceState(
                        command = snapshot.child("command").getValue(String::class.java),
                        actualLedState = snapshot.child("actualLedState").getValue(Boolean::class.java),
                        status = snapshot.child("status").getValue(String::class.java),
                        lastUpdate = snapshot.child("lastUpdate").getValue(Long::class.java),
                    ),
                )
            }

            override fun onCancelled(error: DatabaseError) = onError(error.toException())
        }.also(deviceReference::addValueEventListener)
    }

    override fun stopObserving() {
        listener?.let { reference?.removeEventListener(it) }
        listener = null
    }

    override fun writeCommand(command: String, callback: (Result<Unit>) -> Unit) {
        val normalized = command.uppercase()
        if (normalized != "ON" && normalized != "OFF") {
            callback(Result.failure(IllegalArgumentException("LED command must be ON or OFF.")))
            return
        }
        val commandReference = reference?.child("command")
        if (commandReference == null) {
            callback(Result.failure(IllegalStateException(CONFIGURATION_ERROR)))
            return
        }
        commandReference.setValue(normalized)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    private companion object {
        const val DATABASE_URL =
            "https://drivealert-lhordkent-default-rtdb.asia-southeast1.firebasedatabase.app/"
        const val CONFIGURATION_ERROR =
            "Firebase is not configured. Add this project's google-services.json to app/."
    }
}
