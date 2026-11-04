package com.drivealert.mvp.login

import com.drivealert.mvp.data.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginPresenterTest {
    @Test
    fun `valid credentials are delegated and successful login opens device control`() {
        val repository = FakeAuthRepository()
        val view = FakeLoginView()
        val presenter = LoginPresenter(repository)
        presenter.attach(view)

        presenter.onSignIn(" driver@example.com ", "secret")

        assertEquals("driver@example.com", repository.receivedEmail)
        assertEquals("secret", repository.receivedPassword)
        assertTrue(view.openedDeviceControl)
        assertFalse(view.loading)
    }

    @Test
    fun `invalid email is rejected before repository call`() {
        val repository = FakeAuthRepository()
        val view = FakeLoginView()
        val presenter = LoginPresenter(repository)
        presenter.attach(view)

        presenter.onSignIn("not-an-email", "secret")

        assertEquals(null, repository.receivedEmail)
        assertEquals("Enter a valid email address.", view.error)
    }
}

private class FakeAuthRepository : AuthRepository {
    var receivedEmail: String? = null
    var receivedPassword: String? = null

    override fun currentUserEmail(): String? = null

    override fun signIn(email: String, password: String, callback: (Result<String>) -> Unit) {
        receivedEmail = email
        receivedPassword = password
        callback(Result.success(email))
    }

    override fun logout() = Unit
}

private class FakeLoginView : LoginContract.View {
    var loading = false
    var error: String? = null
    var openedDeviceControl = false

    override fun showLoginForm() = Unit
    override fun showLoading(isLoading: Boolean) { loading = isLoading }
    override fun showError(message: String?) { error = message }
    override fun openDeviceControl() { openedDeviceControl = true }
}
