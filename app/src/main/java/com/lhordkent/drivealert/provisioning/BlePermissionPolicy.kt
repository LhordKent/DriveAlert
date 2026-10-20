package com.lhordkent.drivealert.provisioning

import android.Manifest
import android.os.Build

object BlePermissionPolicy {
    fun requiresLocationServices(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt <= Build.VERSION_CODES.R

    fun runtimePermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> = when {
        sdkInt >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
