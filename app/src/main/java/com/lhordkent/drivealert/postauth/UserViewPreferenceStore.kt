package com.lhordkent.drivealert.postauth

import android.content.SharedPreferences
import com.lhordkent.drivealert.data.profile.UserRole

interface UserViewPreferenceStore {
    fun read(userId: String): UserView?
    fun write(userId: String, view: UserView)
}

class SharedPreferencesUserViewStore(private val preferences: SharedPreferences) : UserViewPreferenceStore {
    override fun read(userId: String): UserView? = preferences.getString("view:$userId", null)
        ?.let { value -> UserView.entries.firstOrNull { it.name == value } }

    override fun write(userId: String, view: UserView) {
        preferences.edit().putString("view:$userId", view.name).apply()
    }
}

internal fun restoredUserView(role: UserRole?, remembered: UserView?): UserView = remembered
    ?: if (role == UserRole.TRUSTED_CONTACT) UserView.TRUSTED_CONTACT else UserView.DRIVER
