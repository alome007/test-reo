package dev.uclip.app.di

import dev.uclip.transport.Discovery
import dev.uclip.transport.NsdDiscovery
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    single<Discovery> { NsdDiscovery(androidContext()) }
}
