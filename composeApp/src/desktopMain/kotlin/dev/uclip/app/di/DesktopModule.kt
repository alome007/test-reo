package dev.uclip.app.di

import dev.uclip.app.clip.ClipboardBridge
import dev.uclip.app.clip.DesktopClipboardBridge
import dev.uclip.crypto.Crypto
import dev.uclip.crypto.platformCrypto
import dev.uclip.pairing.DeviceIdentity
import dev.uclip.pairing.TimeProvider
import dev.uclip.transport.Discovery
import dev.uclip.transport.JmDnsDiscovery
import dev.uclip.transport.WebSocketTransport
import io.ktor.client.HttpClient
import org.koin.dsl.module

fun desktopModule(identity: DeviceIdentity) = module {
    single { identity }
    single<Discovery> { JmDnsDiscovery() }
    single<ClipboardBridge> { DesktopClipboardBridge() }
    single<Crypto> { platformCrypto() }
    single<HttpClient> { WebSocketTransport.httpClient() }
    single<TimeProvider> { TimeProvider { System.currentTimeMillis() } }
}
