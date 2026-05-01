package com.mkbhdana.streamhive

import android.app.Application
import com.mkbhdana.streamhive.player.proxy.StreamProxyServer
import com.mkbhdana.streamhive.player.proxy.StreamProxyService
import com.mkbhdana.streamhive.settings.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StreamHiveApp : Application() {

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
