package com.drivealert.mvp.login

interface LoginContract {
    interface View {
        fun showLoginForm()
        fun showLoading(isLoading: Boolean)
        fun showError(message: String?)
        fun openDeviceControl()
    }

    interface Presenter {
        fun attach(view: View)
        fun onViewReady()
        fun onSignInSelected()
        fun onSignIn(email: String, password: String)
        fun detach()
    }
}
