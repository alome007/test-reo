package dev.uclip.app.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import dev.uclip.app.App
import dev.uclip.app.DeviceIdentity
import dev.uclip.app.PeerRepository
import dev.uclip.app.di.commonModule
import dev.uclip.app.di.desktopModule
import dev.uclip.transport.WebSocketServer
import java.awt.image.BufferedImage
import java.net.ServerSocket
import org.koin.compose.KoinContext
import org.koin.core.context.startKoin

fun main() {
    val port = reserveFreePort()
    val server = WebSocketServer(port = port).also { it.start() }
    val identity = DeviceIdentity.random("Mac")

    val koin = startKoin {
        modules(commonModule, desktopModule)
    }.koin
    koin.get<PeerRepository>().bootstrap(identity, localPort = port)

    try {
        application {
            KoinContext {
                val trayState = rememberTrayState()
                val showWindow = remember { mutableStateOf(true) }
                val icon = remember { BitmapPainter(makeTrayBitmap().toComposeImageBitmap()) }

                Tray(
                    icon = icon,
                    state = trayState,
                    tooltip = "Universal Clipboard",
                    onAction = { showWindow.value = true },
                )

                if (showWindow.value) {
                    Window(
                        onCloseRequest = { showWindow.value = false },
                        title = "Universal Clipboard",
                        state = rememberWindowState(width = 420.dp, height = 640.dp),
                    ) {
                        Surface(color = MaterialTheme.colorScheme.background) { App() }
                    }
                }
            }
        }
    } finally {
        koin.get<PeerRepository>().shutdown()
        server.stop()
    }
}

private fun reserveFreePort(): Int = ServerSocket(0).use { it.localPort }

private fun makeTrayBitmap(): BufferedImage {
    val size = 16
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.color = java.awt.Color(0x33, 0x66, 0xCC)
    g.fillRoundRect(0, 0, size, size, 4, 4)
    g.dispose()
    return img
}
