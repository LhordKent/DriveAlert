package com.drivealert.mvp.led

import com.drivealert.mvp.data.DeviceAvailability

interface LedContract {
    interface View {
        fun showAccount(email: String)
        fun showInitialLoading(isLoading: Boolean)
        fun showCommandLoading(isLoading: Boolean)
        fun showDeviceState(requestedState: String, actualState: String, availability: DeviceAvailability)
        fun showFeedback(message: String?, isError: Boolean = false)
        fun openLogin()
    }

    interface Presenter {
        fun attach(view: View)
        fun start()
        fun stop()
        fun onTurnOn()
        fun onTurnOff()
        fun onLogout()
        fun detach()
    }
}
