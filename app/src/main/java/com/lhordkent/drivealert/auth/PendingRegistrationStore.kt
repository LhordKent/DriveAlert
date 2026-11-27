package com.lhordkent.drivealert.auth

import android.content.SharedPreferences
import com.lhordkent.drivealert.data.profile.NewUserProfile
import org.json.JSONObject

/** Keeps signup details across restarts until verification. Never stores passwords. */
class PendingRegistrationStore(private val preferences: SharedPreferences) {
    fun save(uid: String, input: SignUpInput) {
        preferences.edit().putString(uid, JSONObject().apply {
            put("firstName", input.firstName)
            put("middleName", input.middleName)
            put("lastName", input.lastName)
            input.suffix.trim().takeIf(String::isNotBlank)?.let { put("suffix", it) }
            put("phoneNumber", input.phoneNumber)
        }.toString()).apply()
    }

    fun read(uid: String, email: String): NewUserProfile? = preferences.getString(uid, null)?.let {
        val data = JSONObject(it)
        NewUserProfile(
            uid = uid,
            firstName = data.getString("firstName"),
            middleName = data.optString("middleName").ifBlank { null },
            lastName = data.getString("lastName"),
            email = email,
            phoneNumber = data.optString("phoneNumber").ifBlank { null },
            suffix = data.optString("suffix").ifBlank { null },
        )
    }

    fun clear(uid: String) { preferences.edit().remove(uid).apply() }
}
