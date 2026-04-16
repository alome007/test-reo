package dev.uclip.app.pairing

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared signal for "user pressed Pair new device" and "pairing session done".
 * DevicesScreen flips [request]; platform-specific [PairingHost] composables
 * observe [isRequested] and render their own UI (QR dialog on desktop,
 * camera scan on Android).
 */
class PairingLauncher {
    private val _isRequested = MutableStateFlow(false)
    val isRequested: StateFlow<Boolean> = _isRequested.asStateFlow()

    fun request() { _isRequested.value = true }
    fun dismiss() { _isRequested.value = false }
}

/**
 * Platform-provided composable that renders the pairing UX when the launcher
 * is requested. Desktop shows a QR dialog; Android launches a camera scan.
 */
@Composable
expect fun PairingHost()
