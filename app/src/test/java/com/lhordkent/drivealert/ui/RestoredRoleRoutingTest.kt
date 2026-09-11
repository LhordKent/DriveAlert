package com.lhordkent.drivealert.ui

import com.lhordkent.drivealert.data.profile.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoredRoleRoutingTest {
    @Test fun driverRoutesToSetupUntilSetupIsComplete() {
        assertEquals(PostAuthRoutes.DRIVER_SETUP, restoredRoleDestination(UserRole.DRIVER, false))
        assertEquals(PostAuthRoutes.DRIVER_HOME, restoredRoleDestination(UserRole.DRIVER, true))
    }

    @Test fun trustedContactRoutesToConnectedDrivers() {
        assertEquals(PostAuthRoutes.TRUSTED_DRIVERS, restoredRoleDestination(UserRole.TRUSTED_CONTACT, false))
    }

    @Test fun bothUsesDriverDefaultWithoutChangingStoredRole() {
        val role = UserRole.BOTH
        assertEquals(PostAuthRoutes.DRIVER_HOME, restoredRoleDestination(role, true))
        assertEquals(UserRole.BOTH, role)
    }
}
