package com.lhordkent.drivealert.device

import com.lhordkent.drivealert.data.repository.ProvisionedDevice

class EndpointResolutionPolicy {
    suspend fun resolve(
        persisted: ProvisionedDevice,
        persistedReachable: suspend () -> Boolean,
        discover: suspend () -> ProvisionedDevice?,
    ): ProvisionedDevice? = if (persistedReachable()) persisted else discover()
}
