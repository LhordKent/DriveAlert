package com.lhordkent.drivealert.provisioning

import android.content.Context
import android.content.SharedPreferences

/** Remembers a setup target, never a live connection or network credentials. */
internal class BleSetupRecoveryStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("ble_setup_recovery", Context.MODE_PRIVATE))

    fun load(driverId: String?): ProvisioningDevice? {
        if (driverId.isNullOrBlank()) return null
        val address = preferences.getString("$driverId.address", null) ?: return null
        val name = preferences.getString("$driverId.name", null) ?: "DriveAlert device"
        return ProvisioningDevice(address, name, 0, previouslyKnown = true)
    }

    fun save(driverId: String?, device: ProvisioningDevice) {
        if (driverId.isNullOrBlank()) return
        // Persist before the user can dismiss the task; apply() may still be pending then.
        preferences.edit().putString("$driverId.address", device.id)
            .putString("$driverId.name", device.name).commit()
    }
}
