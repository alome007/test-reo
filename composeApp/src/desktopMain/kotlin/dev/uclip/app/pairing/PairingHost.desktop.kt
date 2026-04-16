package dev.uclip.app.pairing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.uclip.app.qr.LocalHost
import dev.uclip.app.qr.PairDialog
import dev.uclip.pairing.DeviceIdentity
import dev.uclip.pairing.PairingCoordinator
import org.koin.compose.koinInject

/** Carries the resolved WebSocket server port from main() into Koin. */
data class DesktopServerPort(val port: Int)

@Composable
actual fun PairingHost() {
    val launcher = koinInject<PairingLauncher>()
    val coordinator = koinInject<PairingCoordinator>()
    val identity = koinInject<DeviceIdentity>()
    val serverPort = koinInject<DesktopServerPort>()
    val isRequested by launcher.isRequested.collectAsState()
    if (isRequested) {
        PairDialog(
            coordinator = coordinator,
            identity = identity,
            host = LocalHost.bestLanHost(),
            port = serverPort.port,
            onDismiss = { launcher.dismiss() },
        )
    }
}
