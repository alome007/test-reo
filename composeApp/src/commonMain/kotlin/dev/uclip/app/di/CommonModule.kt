package dev.uclip.app.di

import dev.uclip.app.PeerRepository
import org.koin.dsl.module

val commonModule = module {
    single { PeerRepository(get()) }
}
