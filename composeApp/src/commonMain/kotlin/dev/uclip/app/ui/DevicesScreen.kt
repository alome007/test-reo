package dev.uclip.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.uclip.app.PeerRepository
import dev.uclip.transport.PeerAddress
import org.koin.compose.koinInject

@Composable
fun DevicesScreen() {
    val peerRepo = koinInject<PeerRepository>()
    val peers by peerRepo.peers.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Devices on this network")
        Spacer(Modifier.height(8.dp))
        if (peers.isEmpty()) {
            Text("Searching… make sure the other device is running Universal Clipboard on the same Wi-Fi.")
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(peers, key = { it.deviceId }) { peer -> PeerCard(peer) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {}) { Text("Pair new device") }
    }
}

@Composable
private fun PeerCard(peer: PeerAddress) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(peer.displayName)
            Text("${peer.host}:${peer.port}  · ${peer.source}")
            Text(peer.deviceId)
        }
    }
}
