package dev.uclip.app.di

import dev.uclip.app.PeerRepository
import dev.uclip.app.clip.ClipSyncController
import dev.uclip.app.clip.ConnectionManager
import org.koin.dsl.module

val commonModule = module {
    single { PeerRepository(get()) }
    single { ClipSyncController(bridge = get(), crypto = get()) }
    single { ConnectionManager(httpClient = get(), controller = get(), identity = get()) }
}
