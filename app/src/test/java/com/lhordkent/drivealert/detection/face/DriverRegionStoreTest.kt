package com.lhordkent.drivealert.detection.face

import org.junit.Assert.*
import org.junit.Test

class DriverRegionStoreTest {
    @Test
    fun `new app session never restores confirmed alignment`() {
        val firstSession = DriverRegionStore()
        val key = "driver/device/81/stream"
        val region = SavedDriverRegion(DriverRegion.DEFAULT, 640, 480)
        firstSession.save(key, region)
        assertEquals(region, firstSession.load(key))
        assertNull(DriverRegionStore().load(key))
    }

    @Test
    fun `session alignment stays scoped to the driver and camera and can be invalidated`() {
        val session = DriverRegionStore()
        val region = SavedDriverRegion(DriverRegion.DEFAULT, 640, 480)
        session.save("driver-a/camera", region)
        assertNull(session.load("driver-b/camera"))
        session.remove("driver-a/camera")
        assertNull(session.load("driver-a/camera"))
    }

    @Test
    fun `reopening a task in the same process clears all previous alignment`() {
        val session = DriverRegionStore()
        session.save("driver-a/camera", SavedDriverRegion(DriverRegion.DEFAULT, 640, 480))
        session.save("driver-a/other-camera", SavedDriverRegion(DriverRegion.DEFAULT, 320, 240))
        session.clear()
        assertNull(session.load("driver-a/camera"))
        assertNull(session.load("driver-a/other-camera"))
    }
}
