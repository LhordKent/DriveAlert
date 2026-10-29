package com.lhordkent.drivealert.detection.face

data class SavedDriverRegion(val region: DriverRegion, val width: Int, val height: Int)

/** Alignment belongs to this app session; never restore it from device storage. */
class DriverRegionStore {
    private val regions = mutableMapOf<String, SavedDriverRegion>()
    fun load(key: String): SavedDriverRegion? = regions[key]
    fun save(key: String, value: SavedDriverRegion) {
        regions[key] = value
    }
    fun remove(key: String) { regions.remove(key) }
    fun clear() { regions.clear() }
}
