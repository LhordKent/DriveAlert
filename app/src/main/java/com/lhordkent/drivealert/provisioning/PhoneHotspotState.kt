package com.lhordkent.drivealert.provisioning

import android.content.Intent

internal enum class PhoneHotspotState { ON, OFF, UNKNOWN }

internal object PhoneHotspotSignal {
    // Android 11 sends this protected broadcast with the last Soft AP state.
    // Some devices omit that initial state, so missing data stays unknown.
    const val ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val EXTRA_STATE = "wifi_state"
    private const val EXTRA_MODE = "android.net.wifi.extra.WIFI_AP_MODE"
    private const val MODE_TETHERED = 1
    private const val STATE_DISABLED = 11
    private const val STATE_ENABLED = 13

    fun read(intent: Intent?): PhoneHotspotState = decode(
        state = intent?.getIntExtra(EXTRA_STATE, -1),
        mode = intent?.getIntExtra(EXTRA_MODE, -1),
    )

    fun decode(state: Int?, mode: Int?): PhoneHotspotState = when {
        mode != MODE_TETHERED -> PhoneHotspotState.UNKNOWN
        state == STATE_DISABLED -> PhoneHotspotState.OFF
        state == STATE_ENABLED -> PhoneHotspotState.ON
        else -> PhoneHotspotState.UNKNOWN
    }
}
