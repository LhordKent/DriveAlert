package com.drivealert.mvp.led

import com.drivealert.mvp.data.AuthRepository
import com.drivealert.mvp.data.DeviceAvailability
import com.drivealert.mvp.data.DeviceState
import com.drivealert.mvp.data.LedRepository

class LedPresenter(
    private val authRepository: AuthRepository,
    private val ledRepository: LedRepository,
    private val heartbeatScheduler: HeartbeatScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) : LedContract.Presenter {
    private var view: LedContract.View? = null
    private var latestDeviceState: DeviceState? = null

    override fun attach(view: LedContract.View) {
        this.view = view
    }

    override fun start() {
        val email = authRepository.currentUserEmail()
        if (email == null) {
            view?.openLogin()
            return
        }
        view?.showAccount(email)
        view?.showInitialLoading(true)
        heartbeatScheduler.start(STATUS_REFRESH_INTERVAL_MS) {
            latestDeviceState?.let(::displayDeviceState)
        }
        ledRepository.observeDevice(
            onState = { state ->
                latestDeviceState = state
                view?.showInitialLoading(false)
                displayDeviceState(state)
            },
            onError = { error ->
                latestDeviceState = null
                view?.showInitialLoading(false)
                view?.showDeviceState("Not set", "Unknown", DeviceAvailability.UNAVAILABLE)
                view?.showFeedback(error.message ?: "Unable to read the ESP32 state.", isError = true)
            },
        )
    }

    override fun stop() {
        heartbeatScheduler.stop()
        ledRepository.stopObserving()
    }

    override fun onTurnOn() = sendCommand("ON")

    override fun onTurnOff() = sendCommand("OFF")

    override fun onLogout() {
        stop()
        authRepository.logout()
        view?.openLogin()
    }

    override fun detach() {
        stop()
        view = null
    }

    private fun sendCommand(command: String) {
        view?.showFeedback(null)
        view?.showCommandLoading(true)
        ledRepository.writeCommand(command) { result ->
            view?.showCommandLoading(false)
            result.fold(
                onSuccess = {
                    view?.showFeedback("$command command sent. Waiting for the ESP32 report.")
                },
                onFailure = {
                    view?.showFeedback(it.message ?: "Could not send the LED command.", isError = true)
                },
            )
        }
    }

    private fun displayDeviceState(state: DeviceState) {
        view?.showDeviceState(
            requestedState = state.command.normalizedCommand(),
            actualState = state.actualLedState.toDisplayState(),
            availability = state.toAvailability(now()),
        )
    }

    private fun DeviceState.toAvailability(currentTime: Long): DeviceAvailability {
        val normalizedStatus = status?.trim()?.lowercase()
        if (normalizedStatus == "offline") return DeviceAvailability.OFFLINE
        if (normalizedStatus != "online" || lastUpdate == null || lastUpdate <= 0L) {
            return DeviceAvailability.UNAVAILABLE
        }
        return if (currentTime - lastUpdate <= ONLINE_TIMEOUT_MS) {
            DeviceAvailability.ONLINE
        } else {
            DeviceAvailability.OFFLINE
        }
    }

    private fun String?.normalizedCommand(): String = when (this?.trim()?.uppercase()) {
        "ON" -> "ON"
        "OFF" -> "OFF"
        else -> "Not set"
    }

    private fun Boolean?.toDisplayState(): String = when (this) {
        true -> "ON"
        false -> "OFF"
        null -> "Unknown"
    }

    private companion object {
        const val ONLINE_TIMEOUT_MS = 25_000L
        const val STATUS_REFRESH_INTERVAL_MS = 5_000L
    }
}
