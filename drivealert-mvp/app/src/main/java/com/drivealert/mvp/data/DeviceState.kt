package com.drivealert.mvp.data

data class DeviceState(
    val command: String? = null,
    val actualLedState: Boolean? = null,
    val status: String? = null,
    val lastUpdate: Long? = null,
)

enum class DeviceAvailability(val label: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    UNAVAILABLE("Unavailable"),
}
