package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import kotlinx.coroutines.delay

/**
 * Picks the camera address to use.
 *
 * Right after Bluetooth setup the camera restarts into Wi-Fi mode and reported its IP before
 * restarting. During that short window the saved IP is polled about once a second instead of
 * waiting on a slow mDNS search; mDNS remains the fallback when the address does not answer.
 */
class EndpointResolutionPolicy(
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val freshHandoffWindowMs: Long = FRESH_HANDOFF_WINDOW_MS,
    private val fastPollBudgetMs: Long = FAST_POLL_BUDGET_MS,
    private val fastPollIntervalMs: Long = FAST_POLL_INTERVAL_MS,
) {
    suspend fun resolve(
        persisted: ProvisionedDevice,
        persistedReachable: suspend () -> Boolean,
        discover: suspend () -> ProvisionedDevice?,
    ): ProvisionedDevice? {
        if (persistedReachable()) return persisted
        if (isFreshHandoff(persisted)) {
            val pollStartedAt = nowEpochMillis()
            while (nowEpochMillis() - pollStartedAt < fastPollBudgetMs) {
                pause(fastPollIntervalMs)
                if (persistedReachable()) return persisted
            }
        }
        return discover()
    }

    private fun isFreshHandoff(device: ProvisionedDevice): Boolean {
        val age = nowEpochMillis() - device.lastConnectedAtEpochMillis
        return age in 0..freshHandoffWindowMs
    }

    companion object {
        const val FRESH_HANDOFF_WINDOW_MS = 60_000L
        const val FAST_POLL_BUDGET_MS = 25_000L
        const val FAST_POLL_INTERVAL_MS = 750L
    }
}
