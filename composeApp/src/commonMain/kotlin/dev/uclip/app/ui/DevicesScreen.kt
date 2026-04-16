package dev.uclip.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.uclip.app.PeerRepository
import dev.uclip.app.clip.ClipSyncController
import dev.uclip.app.clip.ConnectionManager
import dev.uclip.transport.PeerAddress
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DevicesScreen() {
    val peerRepo = koinInject<PeerRepository>()
    val connections = koinInject<ConnectionManager>()
    val clipSync = koinInject<ClipSyncController>()

    val peers by peerRepo.peers.collectAsStateWithLifecycle()
    val connected by connections.connected.collectAsStateWithLifecycle()
    val status by clipSync.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Devices on this network", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Connected: ${status.connectedCount}  ·  Last: ${status.lastEvent ?: "—"}")
        Spacer(Modifier.height(12.dp))
        if (peers.isEmpty()) {
            Text("Searching… make sure the other device is running Universal Clipboard on the same Wi-Fi.")
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(peers, key = { it.deviceId }) { peer ->
                    PeerCard(
                        peer = peer,
                        isConnected = peer.deviceId in connected,
                        onConnect = { scope.launch { connections.connectTo(peer) } },
                        onDisconnect = { scope.launch { connections.disconnect(peer.deviceId) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerCard(
    peer: PeerAddress,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { if (isConnected) onDisconnect() else onConnect() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row { Text(peer.displayName, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(0.dp)) }
            Text("${peer.host}:${peer.port}  ·  ${peer.source}")
            Text(peer.deviceId)
            Spacer(Modifier.height(4.dp))
            Text(if (isConnected) "Connected — tap to disconnect" else "Tap to connect")
        }
    }
}
