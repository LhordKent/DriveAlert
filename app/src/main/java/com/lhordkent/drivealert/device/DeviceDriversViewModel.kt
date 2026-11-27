package com.lhordkent.drivealert.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lhordkent.drivealert.DriveAlertApplication
import com.lhordkent.drivealert.data.connection.ConnectionCodeTarget
import com.lhordkent.drivealert.data.connection.ResolvedDeviceDriver
import com.lhordkent.drivealert.data.connection.TrustedContactEmailException
import com.lhordkent.drivealert.data.connection.TrustedContactEmailMatch
import com.lhordkent.drivealert.data.device.DeviceAccessOverview
import com.lhordkent.drivealert.data.device.DeviceAccessGrant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface DeviceDriverInviteTarget {
    val displayName: String

    data class ByEmail(val match: TrustedContactEmailMatch) : DeviceDriverInviteTarget {
        override val displayName get() = match.displayName
    }

    data class ByCode(val match: ConnectionCodeTarget) : DeviceDriverInviteTarget {
        override val displayName get() = match.displayName
    }
}

data class DeviceDriversState(
    val overview: DeviceAccessOverview = DeviceAccessOverview(),
    val grants: List<DeviceAccessGrant> = emptyList(),
    val selectedDeviceId: String? = null,
    val inviteInput: String = "",
    val inviteByEmail: Boolean = true,
    val target: DeviceDriverInviteTarget? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class DeviceDriversViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as DriveAlertApplication).container
    private val repository = container.deviceAccessRepository
    private val operations = application.getSharedPreferences("device_demo_operations", android.content.Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(DeviceDriversState())
    val state = mutableState.asStateFlow()
    private var uid: String? = null
    private var binding: Job? = null
    private var local: Job? = null
    private var action: Job? = null
    private var generation = 0L

    fun bind(userId: String?) {
        if (uid == userId) return
        generation++
        binding?.cancel(); local?.cancel(); action?.cancel()
        uid = userId
        mutableState.value = DeviceDriversState()
        if (userId == null) return
        local = viewModelScope.launch { repository.observe(userId).collect { grants ->
            if (uid == userId) mutableState.update { it.copy(grants = grants) }
        } }
        binding = viewModelScope.launch {
            while (uid == userId) {
                refreshQuietly(userId)
                delay(30_000)
            }
        }
    }

    private suspend fun refreshQuietly(userId: String) {
        try {
            val overview = repository.refresh(userId)
            if (uid == userId) mutableState.update {
                val selected = it.selectedDeviceId?.takeIf { id -> overview.owned.any { owner -> owner.deviceId == id } }
                    ?: overview.owned.firstOrNull()?.deviceId
                it.copy(overview = overview, selectedDeviceId = selected, target = if (selected == it.selectedDeviceId) it.target else null)
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) { /* Retry when the periodic loop runs again. */ }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* A background transport failure never erases verified offline access. */ }
    }

    fun refresh() = runAction("Device access refreshed.") { userId ->
        val overview = repository.refresh(userId)
        mutableState.update { it.copy(overview = overview, selectedDeviceId = it.selectedDeviceId ?: overview.owned.firstOrNull()?.deviceId) }
    }

    suspend fun loadDriverPhoneNumbers(userId: String, accessIds: List<String>): Map<String, String> =
        container.trustedContactEmailDirectory.deviceDriverPhoneNumbers(userId, accessIds)

    fun selectDevice(id: String) { mutableState.update { it.copy(selectedDeviceId = id, target = null, inviteInput = "", error = null) } }
    fun setInviteInput(value: String) { mutableState.update { it.copy(inviteInput = value, target = null, error = null, message = null) } }
    fun setInviteByEmail(enabled: Boolean) {
        mutableState.update { it.copy(inviteByEmail = enabled, inviteInput = "", target = null, error = null, message = null) }
    }
    fun dismissTarget() { if (!state.value.busy) mutableState.update { it.copy(target = null) } }

    fun lookup() = runAction(null) { userId ->
        val current = state.value
        val id = current.selectedDeviceId ?: error("Register a device first.")
        val target = if (current.inviteByEmail) {
            DeviceDriverInviteTarget.ByEmail(container.trustedContactEmailDirectory.lookupDeviceDriver(userId, id, current.inviteInput))
        } else {
            val match = container.trustedContactRepository.resolveConnectionCode(current.inviteInput)
                ?: error("No active account was found. Check the connection code.")
            check(match.userId != userId) { "Your primary driver access is already included." }
            check(current.overview.managed.none { it.deviceId == id && it.driverUserId == match.userId && it.status in listOf("PENDING", "ACTIVE") }) {
                "This driver is already invited or authorized."
            }
            DeviceDriverInviteTarget.ByCode(match)
        }
        mutableState.update { if (it.selectedDeviceId == id) it.copy(target = target) else it }
    }

    fun confirmActivation() = runAction("Driver added. They can now connect to this device.") { userId ->
        val current = state.value
        val id = requireNotNull(current.selectedDeviceId)
        val target = when (val match = requireNotNull(current.target)) {
            is DeviceDriverInviteTarget.ByCode -> ResolvedDeviceDriver(match.match.userId, match.match.normalizedCode)
            is DeviceDriverInviteTarget.ByEmail -> container.trustedContactEmailDirectory.resolveDeviceDriver(userId, id, match.match.matchId)
        }
        val operationKey = "$userId/$id/${target.userId}"
        val operationId = operations.getString(operationKey, null) ?: UUID.randomUUID().toString().also {
            check(operations.edit().putString(operationKey, it).commit()) { "Could not save this confirmation. Try again." }
        }
        val overview = repository.invite(userId, id, target.connectionCode, operationId, expectedDriverUid = target.userId)
        operations.edit().remove(operationKey).apply()
        mutableState.update { it.copy(target = null, inviteInput = "", overview = overview) }
    }

    fun transition(id: String, status: String) = runAction(if (status == "ACTIVE") "Driver access activated." else "Device access updated. After removing a driver, reconnect as the primary driver over Bluetooth to sync the change to the device.") { userId ->
        val overview = repository.transition(userId, id, status)
        mutableState.update { it.copy(overview = overview) }
    }

    private fun runAction(success: String?, block: suspend (String) -> Unit) {
        if (state.value.busy) return
        val userId = uid ?: return
        val expected = generation
        mutableState.update { it.copy(busy = true, error = null, message = null) }
        action = viewModelScope.launch {
            try {
                block(userId)
                if (expected == generation) mutableState.update { it.copy(busy = false, message = success) }
            } catch (error: CancellationException) {
                if (expected == generation) mutableState.update { it.copy(busy = false, error = "The request timed out or was cancelled. Connect to the internet and retry; an existing confirmation will be reused.") }
            } catch (error: Exception) {
                val matchExpired = error is TrustedContactEmailException && error.status == "EMAIL_MATCH_EXPIRED"
                if (expected == generation) mutableState.update {
                    it.copy(busy = false, error = deviceAccessError(error), target = it.target.takeUnless { matchExpired })
                }
            }
        }
    }
}

fun deviceAccessError(error: Throwable): String = when (error) {
    is TrustedContactEmailException -> when (error.status) {
        "INVALID_EMAIL" -> "Enter a valid email address."
        "NO_EMAIL_MATCH" -> "No eligible Driver was found. Check their email or use their connection code."
        "EMAIL_MATCH_EXPIRED" -> "This search expired or the account changed. Search again."
        "EMAIL_SEARCH_LIMIT" -> "Too many searches. Try again in a few minutes."
        "PRIMARY_DRIVER_REQUIRED" -> "Only the primary driver can invite drivers to this device."
        "DRIVER_REQUIRED", "AUTH_REQUIRED", "AUTH_INVALID" -> "Sign in with a verified Driver account to search by email."
        else -> "Email lookup could not be completed. Check your internet connection and try again."
    }
    is com.google.firebase.firestore.FirebaseFirestoreException -> when (error.code) {
        com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Device access could not be verified. Check your account and ask the owner to check access. The sharing service may also need an update."
        com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Sign in again to manage device access."
        else -> "Connect to the internet and try again. Previously verified offline access is kept."
    }
    is kotlinx.coroutines.TimeoutCancellationException -> "Connect to the internet and retry. This operation needs an online confirmation."
    else -> error.message ?: "Device access could not be updated. Try again."
}
