package com.mkbhdana.streamhive

import android.app.Application
import com.mkbhdana.streamhive.auth.AuthRepository
import com.mkbhdana.streamhive.player.proxy.StreamProxyService
import com.mkbhdana.streamhive.settings.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StreamHiveApp : Application() {

    @Inject lateinit var appPreferences: AppPreferences

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate() {
        super.onCreate()
        if (appPreferences.keepServerRunning && authRepository.isAuthenticated()) {
            StreamProxyService.start(this)
        }
    }
}
