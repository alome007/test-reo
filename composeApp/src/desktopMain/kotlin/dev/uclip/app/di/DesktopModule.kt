package dev.uclip.app.di

import dev.uclip.transport.Discovery
import dev.uclip.transport.JmDnsDiscovery
import org.koin.dsl.module

val desktopModule = module {
    single<Discovery> { JmDnsDiscovery() }
}
