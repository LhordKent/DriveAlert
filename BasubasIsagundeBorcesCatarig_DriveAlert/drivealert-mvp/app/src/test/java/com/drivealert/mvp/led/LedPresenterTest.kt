package com.drivealert.mvp.led

import com.drivealert.mvp.data.AuthRepository
import com.drivealert.mvp.data.DeviceAvailability
import com.drivealert.mvp.data.DeviceState
import com.drivealert.mvp.data.LedRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedPresenterTest {
    @Test
    fun `command write does not fake the actual LED state`() {
        val ledRepository = FakeLedRepository()
        val view = FakeLedView()
        val presenter = LedPresenter(FakeSignedInAuth(), ledRepository, FakeHeartbeatScheduler()) { 100_000L }
        presenter.attach(view)
        presenter.start()
        ledRepository.emit(DeviceState("OFF", false, "online", 90_000L))

        presenter.onTurnOn()

        assertEquals("ON", ledRepository.writtenCommand)
        assertEquals("OFF", view.actualState)
        assertTrue(view.feedback?.contains("Waiting for the ESP32") == true)
    }

    @Test
    fun `fresh online heartbeat is shown as online`() {
        val ledRepository = FakeLedRepository()
        val view = FakeLedView()
        val presenter = LedPresenter(FakeSignedInAuth(), ledRepository, FakeHeartbeatScheduler()) { 100_000L }
        presenter.attach(view)
        presenter.start()

        ledRepository.emit(DeviceState("ON", true, "online", 80_000L))

        assertEquals(DeviceAvailability.ONLINE, view.availability)
        assertEquals("ON", view.requestedState)
        assertEquals("ON", view.actualState)
        assertFalse(view.initialLoading)
    }

    @Test
    fun `stale heartbeat is shown as offline`() {
        val ledRepository = FakeLedRepository()
        val view = FakeLedView()
        val presenter = LedPresenter(FakeSignedInAuth(), ledRepository, FakeHeartbeatScheduler()) { 125_001L }
        presenter.attach(view)
        presenter.start()

        ledRepository.emit(DeviceState("OFF", false, "online", 100_000L))

        assertEquals(DeviceAvailability.OFFLINE, view.availability)
    }

    @Test
    fun `online device becomes offline when heartbeat ages without another Firebase event`() {
        val ledRepository = FakeLedRepository()
        val scheduler = FakeHeartbeatScheduler()
        val view = FakeLedView()
        var currentTime = 100_000L
        val presenter = LedPresenter(FakeSignedInAuth(), ledRepository, scheduler) { currentTime }
        presenter.attach(view)
        presenter.start()
        ledRepository.emit(DeviceState("ON", true, "online", 90_000L))
        assertEquals(DeviceAvailability.ONLINE, view.availability)

        currentTime = 115_001L
        scheduler.tick()

        assertEquals(DeviceAvailability.OFFLINE, view.availability)
    }
}

private class FakeHeartbeatScheduler : HeartbeatScheduler {
    private var task: (() -> Unit)? = null

    override fun start(intervalMs: Long, task: () -> Unit) {
        this.task = task
    }

    override fun stop() {
        task = null
    }

    fun tick() = task?.invoke()
}

private class FakeSignedInAuth : AuthRepository {
    override fun currentUserEmail(): String = "driver@example.com"
    override fun signIn(email: String, password: String, callback: (Result<String>) -> Unit) = Unit
    override fun logout() = Unit
}

private class FakeLedRepository : LedRepository {
    private var stateCallback: ((DeviceState) -> Unit)? = null
    var writtenCommand: String? = null

    override fun observeDevice(onState: (DeviceState) -> Unit, onError: (Throwable) -> Unit) {
        stateCallback = onState
    }

    override fun stopObserving() { stateCallback = null }

    override fun writeCommand(command: String, callback: (Result<Unit>) -> Unit) {
        writtenCommand = command
        callback(Result.success(Unit))
    }

    fun emit(state: DeviceState) = stateCallback?.invoke(state)
}

private class FakeLedView : LedContract.View {
    var initialLoading = true
    var commandLoading = false
    var requestedState = "Not set"
    var actualState = "Unknown"
    var availability = DeviceAvailability.UNAVAILABLE
    var feedback: String? = null

    override fun showAccount(email: String) = Unit
    override fun showInitialLoading(isLoading: Boolean) { initialLoading = isLoading }
    override fun showCommandLoading(isLoading: Boolean) { commandLoading = isLoading }
    override fun showDeviceState(requestedState: String, actualState: String, availability: DeviceAvailability) {
        this.requestedState = requestedState
        this.actualState = actualState
        this.availability = availability
    }
    override fun showFeedback(message: String?, isError: Boolean) { feedback = message }
    override fun openLogin() = Unit
}
