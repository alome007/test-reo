package dev.uclip.app.di

import dev.uclip.app.PeerRepository
import dev.uclip.app.clip.ClipSyncController
import dev.uclip.app.clip.ConnectionManager
import dev.uclip.app.pairing.PairingLauncher
import dev.uclip.pairing.PairingCoordinator
import dev.uclip.pairing.PairingRegistry
import org.koin.dsl.module

val commonModule = module {
    single { PairingRegistry() }
    single { PairingCoordinator(crypto = get(), registry = get(), time = get()) }
    single { PairingLauncher() }
    single { PeerRepository(get()) }
    single { ClipSyncController(bridge = get(), crypto = get()) }
    single {
        ConnectionManager(
            httpClient = get(),
            controller = get(),
            identity = get(),
            registry = get(),
            coordinator = get(),
        )
    }
}
