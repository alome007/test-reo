package dev.uclip.app

import android.app.Application
import dev.uclip.app.di.androidModule
import dev.uclip.app.di.commonModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class UClipApplication : Application() {
    private val peerRepository: PeerRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@UClipApplication)
            modules(commonModule, androidModule)
        }
        // Android doesn't host a WebSocket server yet — it's a client that
        // connects to desktops it discovers. Still start discovery so the UI
        // can list peers.
        val identity = DeviceIdentity.random("Android")
        peerRepository.bootstrap(identity, localPort = null)
    }
}
