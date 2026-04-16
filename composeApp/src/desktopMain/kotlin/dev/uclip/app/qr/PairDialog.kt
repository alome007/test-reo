package dev.uclip.app.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import dev.uclip.pairing.DeviceIdentity
import dev.uclip.pairing.PairingCoordinator
import dev.uclip.pairing.QrPayload

@Composable
fun PairDialog(
    coordinator: PairingCoordinator,
    identity: DeviceIdentity,
    host: String,
    port: Int,
    onDismiss: () -> Unit,
) {
    val qr = remember { coordinator.generateOffer(identity, host, port) }
    val bitmap = remember(qr) {
        BitmapPainter(QrGenerator.encode(QrPayload.encode(qr), 512).toComposeImageBitmap())
    }

    DialogWindow(
        onCloseRequest = {
            coordinator.clearOffer()
            onDismiss()
        },
        state = rememberDialogState(width = 480.dp, height = 640.dp),
        title = "Pair a device",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Text("Scan this QR in the mobile app")
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(360.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = bitmap,
                    contentDescription = "Pairing QR",
                    modifier = Modifier.size(340.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("${qr.host}:${qr.port}")
            Spacer(Modifier.height(8.dp))
            Text("Token: ${qr.token.take(12)}…")
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                coordinator.clearOffer()
                onDismiss()
            }) { Text("Cancel") }
        }
    }
}
