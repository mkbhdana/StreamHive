package com.driveplay.app.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveplay.app.catalog.DriveRepository
import com.driveplay.app.data.db.MediaFileEntity
import com.driveplay.app.player.mpv.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingsUiState(
    // Player
    val preferredEngine: PlayerEngine = PlayerEngine.EXO_PLAYER,
    val defaultDecoder: String = "hw+",
    val defaultResizeMode: String = "fit",
    val isMpvAvailable: Boolean = false,

    // Gestures
    val gestureVolumeEnabled: Boolean = true,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureSeekEnabled: Boolean = true,
    val gestureDoubleTapEnabled: Boolean = true,
    val gestureZoomEnabled: Boolean = true,
    val gestureSensitivity: Float = 1.0f,

    // Subtitles
    val subtitleLanguage: String = "eng",
    val subtitleFontSize: Int = 18,
    val subtitleColor: Long = 0xFFFFFFFF,
    val subtitleBgOpacity: Float = 0.5f,
    val subtitlePosition: Int = 90,

    // TMDB
    val tmdbApiKey: String = "",
    val tmdbMovieFolders: Set<String> = emptySet(),
    val tmdbTvFolders: Set<String> = emptySet(),
    val tmdbAnimeFolders: Set<String> = emptySet()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val driveRepository: DriveRepository
) : ViewModel() {

    var uiState by mutableStateOf(loadState())
        private set

    // Available folders from database for the folder picker
    val availableFolders: StateFlow<List<MediaFileEntity>> = driveRepository
        .getAllFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun loadState() = SettingsUiState(
        preferredEngine = prefs.preferredEngine,
        defaultDecoder = prefs.defaultDecoder,
        defaultResizeMode = prefs.defaultResizeMode,
        isMpvAvailable = prefs.isMpvAvailable(),
        gestureVolumeEnabled = prefs.gestureVolumeEnabled,
        gestureBrightnessEnabled = prefs.gestureBrightnessEnabled,
        gestureSeekEnabled = prefs.gestureSeekEnabled,
        gestureDoubleTapEnabled = prefs.gestureDoubleTapEnabled,
        gestureZoomEnabled = prefs.gestureZoomEnabled,
        gestureSensitivity = prefs.gestureSensitivity,
        subtitleLanguage = prefs.subtitleLanguage,
        subtitleFontSize = prefs.subtitleFontSize,
        subtitleColor = prefs.subtitleColor,
        subtitleBgOpacity = prefs.subtitleBgOpacity,
        subtitlePosition = prefs.subtitlePosition,
        tmdbApiKey = prefs.tmdbApiKey,
        tmdbMovieFolders = prefs.tmdbMovieFolders,
        tmdbTvFolders = prefs.tmdbTvFolders,
        tmdbAnimeFolders = prefs.tmdbAnimeFolders
    )

    // ──── Player ────

    fun setPreferredEngine(engine: PlayerEngine) {
        prefs.preferredEngine = engine
        uiState = uiState.copy(preferredEngine = engine)
    }

    fun setDefaultDecoder(decoder: String) {
        prefs.defaultDecoder = decoder
        uiState = uiState.copy(defaultDecoder = decoder)
    }

    fun setDefaultResizeMode(mode: String) {
        prefs.defaultResizeMode = mode
        uiState = uiState.copy(defaultResizeMode = mode)
    }

    // ──── Gestures ────

    fun setGestureVolumeEnabled(enabled: Boolean) {
        prefs.gestureVolumeEnabled = enabled
        uiState = uiState.copy(gestureVolumeEnabled = enabled)
    }

    fun setGestureBrightnessEnabled(enabled: Boolean) {
        prefs.gestureBrightnessEnabled = enabled
        uiState = uiState.copy(gestureBrightnessEnabled = enabled)
    }

    fun setGestureSeekEnabled(enabled: Boolean) {
        prefs.gestureSeekEnabled = enabled
        uiState = uiState.copy(gestureSeekEnabled = enabled)
    }

    fun setGestureDoubleTapEnabled(enabled: Boolean) {
        prefs.gestureDoubleTapEnabled = enabled
        uiState = uiState.copy(gestureDoubleTapEnabled = enabled)
    }

    fun setGestureZoomEnabled(enabled: Boolean) {
        prefs.gestureZoomEnabled = enabled
        uiState = uiState.copy(gestureZoomEnabled = enabled)
    }

    fun setGestureSensitivity(sensitivity: Float) {
        prefs.gestureSensitivity = sensitivity
        uiState = uiState.copy(gestureSensitivity = sensitivity)
    }

    // ──── Subtitles ────

    fun setSubtitleLanguage(lang: String) {
        prefs.subtitleLanguage = lang
        uiState = uiState.copy(subtitleLanguage = lang)
    }

    fun setSubtitleFontSize(size: Int) {
        prefs.subtitleFontSize = size
        uiState = uiState.copy(subtitleFontSize = size)
    }

    fun setSubtitleColor(color: Long) {
        prefs.subtitleColor = color
        uiState = uiState.copy(subtitleColor = color)
    }

    fun setSubtitleBgOpacity(opacity: Float) {
        prefs.subtitleBgOpacity = opacity
        uiState = uiState.copy(subtitleBgOpacity = opacity)
    }

    fun setSubtitlePosition(position: Int) {
        prefs.subtitlePosition = position
        uiState = uiState.copy(subtitlePosition = position)
    }

    // ──── TMDB ────

    fun setTmdbApiKey(key: String) {
        prefs.tmdbApiKey = key
        uiState = uiState.copy(tmdbApiKey = key)
    }

    fun addMovieFolder(folderId: String) {
        val updated = prefs.tmdbMovieFolders + folderId
        prefs.tmdbMovieFolders = updated
        uiState = uiState.copy(tmdbMovieFolders = updated)
    }

    fun removeMovieFolder(folderId: String) {
        val updated = prefs.tmdbMovieFolders - folderId
        prefs.tmdbMovieFolders = updated
        uiState = uiState.copy(tmdbMovieFolders = updated)
    }

    fun addTvFolder(folderId: String) {
        val updated = prefs.tmdbTvFolders + folderId
        prefs.tmdbTvFolders = updated
        uiState = uiState.copy(tmdbTvFolders = updated)
    }

    fun removeTvFolder(folderId: String) {
        val updated = prefs.tmdbTvFolders - folderId
        prefs.tmdbTvFolders = updated
        uiState = uiState.copy(tmdbTvFolders = updated)
    }

    fun addAnimeFolder(folderId: String) {
        val updated = prefs.tmdbAnimeFolders + folderId
        prefs.tmdbAnimeFolders = updated
        uiState = uiState.copy(tmdbAnimeFolders = updated)
    }

    fun removeAnimeFolder(folderId: String) {
        val updated = prefs.tmdbAnimeFolders - folderId
        prefs.tmdbAnimeFolders = updated
        uiState = uiState.copy(tmdbAnimeFolders = updated)
    }
}
