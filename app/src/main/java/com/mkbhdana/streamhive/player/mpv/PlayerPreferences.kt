package com.mkbhdana.streamhive.player.mpv

import com.mkbhdana.streamhive.settings.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backward compatibility wrapper. Delegates to AppPreferences.
 * This class is kept so existing Hilt injections continue to work.
 */
@Singleton
class PlayerPreferences @Inject constructor(
    private val appPreferences: AppPreferences
) {
    var preferredEngine: PlayerEngine
        get() = appPreferences.preferredEngine
        set(value) { appPreferences.preferredEngine = value }

    fun isMpvAvailable(): Boolean = appPreferences.isMpvAvailable()
}
