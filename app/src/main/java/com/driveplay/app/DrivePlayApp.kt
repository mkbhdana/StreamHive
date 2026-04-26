package com.driveplay.app

import android.app.Application
import com.driveplay.app.player.proxy.StreamProxyServer
import com.driveplay.app.player.proxy.StreamProxyService
import com.driveplay.app.settings.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DrivePlayApp : Application() {

    @Inject lateinit var appPreferences: AppPreferences

    // Eagerly inject the proxy server so it starts on app launch
    // This ensures external player can use the proxy URL immediately
    @Inject lateinit var streamProxyServer: StreamProxyServer

    override fun onCreate() {
        super.onCreate()
        // Auto-start proxy foreground service if user has it enabled
        if (appPreferences.keepServerRunning) {
            StreamProxyService.start(this)
        }
    }
}
