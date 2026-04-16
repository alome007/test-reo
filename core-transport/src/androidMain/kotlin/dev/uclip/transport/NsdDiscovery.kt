package dev.uclip.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android mDNS via [NsdManager]. Acquires a Wi-Fi multicast lock on start
 * (mDNS packets are multicast and would otherwise be dropped). API 26+.
 */
class NsdDiscovery(context: Context) : Discovery {

    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val multicastLock = wifi.createMulticastLock("uclip-mdns").apply {
        setReferenceCounted(true)
    }

    private val _peers = MutableStateFlow<List<PeerAddress>>(emptyList())
    override val peers: Flow<List<PeerAddress>> = _peers.asStateFlow()

    private val known = ConcurrentHashMap<String, PeerAddress>()
    private var selfDeviceId: String = ""
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override fun start(selfDeviceId: String) {
        if (discoveryListener != null) return
        this.selfDeviceId = selfDeviceId
        if (!multicastLock.isHeld) multicastLock.acquire()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                resolve(service)
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                val deviceId = service.readAttribute(TxtKeys.DEVICE_ID) ?: service.serviceName
                if (known.remove(deviceId) != null) {
                    _peers.value = known.values.toList()
                }
            }
        }
        discoveryListener = listener
        nsd.discoverServices(MDNS_SERVICE_TYPE.trimEnd('.'), NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun resolve(service: NsdServiceInfo) {
        nsd.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val deviceId = resolved.readAttribute(TxtKeys.DEVICE_ID) ?: resolved.serviceName
                if (deviceId == selfDeviceId) return
                val host = resolved.host?.hostAddress ?: return
                val displayName = resolved.readAttribute(TxtKeys.DISPLAY_NAME) ?: deviceId
                val peer = PeerAddress(deviceId, displayName, host, resolved.port, PeerAddress.Source.LAN)
                known[deviceId] = peer
                _peers.value = known.values.toList()
            }
        })
    }

    override fun advertise(deviceId: String, displayName: String, port: Int) {
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = deviceId
            serviceType = MDNS_SERVICE_TYPE.trimEnd('.')
            this.port = port
            setAttribute(TxtKeys.DEVICE_ID, deviceId)
            setAttribute(TxtKeys.DISPLAY_NAME, displayName)
            setAttribute(TxtKeys.PROTO, "1")
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
        }
        registrationListener = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun stopAdvertising() {
        registrationListener?.let { try { nsd.unregisterService(it) } catch (_: Throwable) {} }
        registrationListener = null
    }

    override fun close() {
        stopAdvertising()
        discoveryListener?.let { try { nsd.stopServiceDiscovery(it) } catch (_: Throwable) {} }
        discoveryListener = null
        if (multicastLock.isHeld) multicastLock.release()
        known.clear()
        _peers.value = emptyList()
    }
}

private fun NsdServiceInfo.readAttribute(key: String): String? =
    attributes[key]?.toString(Charsets.UTF_8)
