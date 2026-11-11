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
            put("phoneNumber", input.phoneNumber)
        }.toString()).apply()
    }

    fun read(uid: String, email: String): NewUserProfile? = preferences.getString(uid, null)?.let {
        val data = JSONObject(it)
        NewUserProfile(uid, data.getString("firstName"), data.optString("middleName").ifBlank { null },
            data.getString("lastName"), email, data.optString("phoneNumber").ifBlank { null })
    }

    fun clear(uid: String) { preferences.edit().remove(uid).apply() }
}
