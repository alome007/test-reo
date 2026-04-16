package dev.uclip.app

import dev.uclip.pairing.DeviceIdentity
import dev.uclip.transport.Discovery
import dev.uclip.transport.PeerAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Adapter between the low-level [Discovery] API and the UI. Starts discovery
 * under a supervisor scope and exposes the peer list as a [StateFlow] so
 * Compose screens can collect it without worrying about Flow semantics.
 */
class PeerRepository(
    private val discovery: Discovery,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val _peers = MutableStateFlow<List<PeerAddress>>(emptyList())
    val peers: StateFlow<List<PeerAddress>> = _peers.asStateFlow()

    fun bootstrap(identity: DeviceIdentity, localPort: Int?) {
        discovery.start(identity.deviceId)
        if (localPort != null) {
            discovery.advertise(identity.deviceId, identity.displayName, localPort)
        }
        scope.launch {
            discovery.peers.collect { _peers.value = it }
        }
    }

    fun shutdown() {
        discovery.close()
    }
}
