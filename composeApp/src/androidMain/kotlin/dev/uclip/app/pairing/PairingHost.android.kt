package dev.uclip.app.pairing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.uclip.app.clip.ConnectionManager
import dev.uclip.pairing.QrPayload
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
actual fun PairingHost() {
    val launcher = koinInject<PairingLauncher>()
    val connections = koinInject<ConnectionManager>()
    val scope = rememberCoroutineScope()
    val isRequested by launcher.isRequested.collectAsState()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        launcher.dismiss()
        if (raw != null) {
            runCatching { QrPayload.decode(raw) }
                .onSuccess { qr -> scope.launch { connections.initiatePairingWithQr(qr) } }
        }
    }

    LaunchedEffect(isRequested) {
        if (isRequested) {
            scanLauncher.launch(
                ScanOptions().apply {
                    setPrompt("Scan pairing QR from desktop")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                }
            )
        }
    }
}
