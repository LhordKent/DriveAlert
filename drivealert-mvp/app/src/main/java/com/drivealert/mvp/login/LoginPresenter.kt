package com.drivealert.mvp.login

import com.drivealert.mvp.data.AuthRepository

class LoginPresenter(
    private val authRepository: AuthRepository,
) : LoginContract.Presenter {
    private var view: LoginContract.View? = null

    override fun attach(view: LoginContract.View) {
        this.view = view
    }

    override fun onViewReady() {
        if (authRepository.currentUserEmail() != null) view?.openDeviceControl()
    }

    override fun onSignInSelected() {
        view?.showError(null)
        view?.showLoginForm()
    }

    override fun onSignIn(email: String, password: String) {
        val cleanEmail = email.trim()
        when {
            cleanEmail.isBlank() -> view?.showError("Enter your email address.")
            !EMAIL_PATTERN.matches(cleanEmail) -> view?.showError("Enter a valid email address.")
            password.isBlank() -> view?.showError("Enter your password.")
            else -> {
                view?.showError(null)
                view?.showLoading(true)
                authRepository.signIn(cleanEmail, password) { result ->
                    view?.showLoading(false)
                    result.fold(
                        onSuccess = { view?.openDeviceControl() },
                        onFailure = { view?.showError(it.message ?: "Sign in failed. Try again.") },
                    )
                }
            }
        }
    }

    override fun detach() {
        view = null
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
