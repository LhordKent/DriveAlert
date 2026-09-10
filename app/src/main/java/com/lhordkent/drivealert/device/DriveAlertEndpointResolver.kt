package com.lhordkent.drivealert.device

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.lhordkent.drivealert.data.repository.ProvisionedDevice
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.Socket
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

interface DriveAlertEndpointResolver {
    suspend fun resolve(device: ProvisionedDevice): ProvisionedDevice?
}

class AndroidDriveAlertEndpointResolver(context: Context) : DriveAlertEndpointResolver {
    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)
    private val policy = EndpointResolutionPolicy()

    override suspend fun resolve(device: ProvisionedDevice): ProvisionedDevice? {
        return policy.resolve(
            persisted = device,
            persistedReachable = { canConnect(device.lastKnownIp, device.streamPort) },
            discover = discovery@ {
                val discovered = withTimeoutOrNull(8_000) { discover(device.hostname) } ?: return@discovery null
                val address = discovered.host?.hostAddress ?: return@discovery null
                if (!isLocalAddress(address)) return@discovery null
                device.copy(
                    hostname = discovered.serviceName.substringBefore(".").ifBlank { device.hostname },
                    lastKnownIp = address,
                    streamPort = discovered.port.takeIf { it > 0 } ?: 81,
                    streamPath = discovered.attributes["path"]?.toString(Charsets.UTF_8) ?: "/stream",
                    lastConnectedAtEpochMillis = System.currentTimeMillis(),
                )
            },
        )
    }

    private suspend fun canConnect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isLocalAddress(host)) return@withContext false
        runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 1_500) } }.isSuccess
    }

    private fun isLocalAddress(host: String): Boolean = runCatching {
        InetAddress.getByName(host).let { it.isSiteLocalAddress || it.isLinkLocalAddress || it.isLoopbackAddress }
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private suspend fun discover(expectedHostname: String?): NsdServiceInfo? = suspendCancellableCoroutine { continuation ->
        var finished = false
        lateinit var listener: NsdManager.DiscoveryListener
        fun finish(info: NsdServiceInfo?) {
            if (finished) return
            finished = true
            runCatching { nsd.stopServiceDiscovery(listener) }
            if (continuation.isActive) continuation.resume(info)
        }
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = finish(null)
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (expectedHostname != null && !serviceInfo.serviceName.equals(expectedHostname, ignoreCase = true)) return
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) = finish(serviceInfo)
                })
            }
        }
        continuation.invokeOnCancellation { if (!finished) runCatching { nsd.stopServiceDiscovery(listener) } }
        nsd.discoverServices("_drivealert._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
    }
}
