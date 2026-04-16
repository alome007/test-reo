package dev.uclip.app

import android.app.Application

class UClipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO(phase 1): start Koin, init SQLDelight driver, load pairings.
    }
}
