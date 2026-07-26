package com.mkbhdana.streamhive

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.mkbhdana.streamhive.auth.AuthRepository
import com.mkbhdana.streamhive.player.proxy.StreamProxyService
import com.mkbhdana.streamhive.settings.AppPreferences
import com.mkbhdana.streamhive.ui.image.PosterFallbackInterceptor
import com.mkbhdana.streamhive.ui.image.PosterSource
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class StreamHiveApp : Application(), ImageLoaderFactory {

    @Inject lateinit var appPreferences: AppPreferences

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate() {
        super.onCreate()
        // Poster URLs are built from plain composable helpers, so the toggle is mirrored
        // here rather than injected. SettingsViewModel keeps it in sync when it changes.
        appPreferences.applyPosterSourceSettings()
        if (appPreferences.keepServerRunning && authRepository.isAuthenticated()) {
            StreamProxyService.start(this)
        }
    }

    /** Coil's singleton loader, with the third-party poster → TMDB fallback installed. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(PosterFallbackInterceptor()) }
            .build()
}
