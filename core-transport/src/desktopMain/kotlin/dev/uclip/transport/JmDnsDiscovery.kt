package dev.uclip.transport

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class JmDnsDiscovery : Discovery {
    private val _peers = MutableStateFlow<List<PeerAddress>>(emptyList())
    override val peers: Flow<List<PeerAddress>> = _peers.asStateFlow()

    private val known = ConcurrentHashMap<String, PeerAddress>()
    private var jmdns: JmDNS? = null
    private var registered: ServiceInfo? = null
    private var selfDeviceId: String = ""
    private var listener: ServiceListener? = null

    override fun start(selfDeviceId: String) {
        this.selfDeviceId = selfDeviceId
        if (jmdns != null) return
        val local = InetAddress.getLocalHost()
        jmdns = JmDNS.create(local).also { j ->
            val l = object : ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    j.requestServiceInfo(event.type, event.name, true)
                }
                override fun serviceResolved(event: ServiceEvent) {
                    val info = event.info ?: return
                    val deviceId = info.getPropertyString(TxtKeys.DEVICE_ID) ?: return
                    if (deviceId == this@JmDnsDiscovery.selfDeviceId) return
                    val host = info.inetAddresses.firstOrNull()?.hostAddress ?: return
                    val displayName = info.getPropertyString(TxtKeys.DISPLAY_NAME) ?: deviceId
                    val peer = PeerAddress(deviceId, displayName, host, info.port, PeerAddress.Source.LAN)
                    known[deviceId] = peer
                    _peers.value = known.values.toList()
                }
                override fun serviceRemoved(event: ServiceEvent) {
                    val deviceId = event.info?.getPropertyString(TxtKeys.DEVICE_ID) ?: return
                    if (known.remove(deviceId) != null) {
                        _peers.value = known.values.toList()
                    }
                }
            }
            listener = l
            j.addServiceListener(MDNS_SERVICE_TYPE_LOCAL, l)
        }
    }

    override fun advertise(deviceId: String, displayName: String, port: Int) {
        val j = jmdns ?: error("Discovery not started")
        stopAdvertising()
        val info = ServiceInfo.create(
            MDNS_SERVICE_TYPE_LOCAL,
            deviceId,
            port,
            0,
            0,
            mapOf(
                TxtKeys.DEVICE_ID to deviceId,
                TxtKeys.DISPLAY_NAME to displayName,
                TxtKeys.PROTO to "1",
            ),
        )
        j.registerService(info)
        registered = info
    }

    override fun stopAdvertising() {
        registered?.let { jmdns?.unregisterService(it) }
        registered = null
    }

    override fun close() {
        stopAdvertising()
        listener?.let { jmdns?.removeServiceListener(MDNS_SERVICE_TYPE_LOCAL, it) }
        try { jmdns?.close() } catch (_: Throwable) {}
        jmdns = null
        known.clear()
        _peers.value = emptyList()
    }
}
