package com.lhordkent.drivealert.detection.face

import android.content.Context

data class SavedDriverRegion(val region: DriverRegion, val width: Int, val height: Int)

/** Stores geometry only, scoped to the account and camera configuration. */
class DriverRegionStore(context: Context) {
    private val preferences = context.getSharedPreferences("driver-region-v1", Context.MODE_PRIVATE)
    fun load(key: String): SavedDriverRegion? = runCatching {
        val fields = preferences.getString(key, null)?.split(',') ?: return null
        require(fields.size == 6)
        SavedDriverRegion(DriverRegion(fields[0].toDouble(), fields[1].toDouble(), fields[2].toDouble(), fields[3].toDouble()), fields[4].toInt(), fields[5].toInt())
            .also { require(it.width > 0 && it.height > 0) }
    }.getOrNull()
    fun save(key: String, value: SavedDriverRegion) {
        val r = value.region
        preferences.edit().putString(key, listOf(r.left, r.top, r.right, r.bottom, value.width, value.height).joinToString(",")).apply()
    }
    fun remove(key: String) { preferences.edit().remove(key).apply() }
}
