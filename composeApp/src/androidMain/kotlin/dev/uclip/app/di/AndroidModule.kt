package dev.uclip.app.di

import dev.uclip.app.clip.AndroidClipboardBridge
import dev.uclip.app.clip.ClipboardBridge
import dev.uclip.crypto.Crypto
import dev.uclip.crypto.platformCrypto
import dev.uclip.pairing.DeviceIdentity
import dev.uclip.pairing.TimeProvider
import dev.uclip.transport.Discovery
import dev.uclip.transport.NsdDiscovery
import dev.uclip.transport.WebSocketTransport
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun androidModule(identity: DeviceIdentity) = module {
    single { identity }
    single<Discovery> { NsdDiscovery(androidContext()) }
    single<ClipboardBridge> { AndroidClipboardBridge(androidContext()) }
    single<Crypto> { platformCrypto() }
    single<HttpClient> { WebSocketTransport.httpClient() }
    single<TimeProvider> { TimeProvider { System.currentTimeMillis() } }
}
