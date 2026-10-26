package com.lhordkent.drivealert.monitoring

internal enum class RecoveryDisposition { NONE, RECOVER_SAME_BOOT, INTERRUPT_AFTER_REBOOT }

internal fun monitoringStartAllowed(isActive: Boolean, startInProgress: Boolean): Boolean =
    !isActive && !startInProgress

internal fun recoveryDisposition(recordBootCount: Int?, currentBootCount: Int): RecoveryDisposition = when {
    recordBootCount == null -> RecoveryDisposition.NONE
    recordBootCount == MonitoringRecoveryStore.UNKNOWN_BOOT_COUNT ||
        currentBootCount == MonitoringRecoveryStore.UNKNOWN_BOOT_COUNT -> RecoveryDisposition.INTERRUPT_AFTER_REBOOT
    recordBootCount == currentBootCount -> RecoveryDisposition.RECOVER_SAME_BOOT
    else -> RecoveryDisposition.INTERRUPT_AFTER_REBOOT
}
