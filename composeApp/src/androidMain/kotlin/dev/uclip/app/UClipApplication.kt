package dev.uclip.app

import android.app.Application
import dev.uclip.app.clip.ClipSyncController
import dev.uclip.app.di.androidModule
import dev.uclip.app.di.commonModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class UClipApplication : Application() {
    private val peerRepository: PeerRepository by inject()
    private val clipSync: ClipSyncController by inject()

    override fun onCreate() {
        super.onCreate()
        val identity = DeviceIdentity.random("Android")
        startKoin {
            androidContext(this@UClipApplication)
            modules(commonModule, androidModule(identity))
        }
        // Android is currently a client-only role: we browse peers but don't
        // host a WebSocket server, so nothing to advertise yet.
        peerRepository.bootstrap(identity, localPort = null)
        clipSync.start()
    }
}
