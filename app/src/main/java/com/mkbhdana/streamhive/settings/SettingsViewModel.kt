package com.mkbhdana.streamhive.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkbhdana.streamhive.catalog.DriveRepository
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    // Player
    val preferredEngine: PlayerEngine = PlayerEngine.EXO_PLAYER,
    val defaultDecoder: String = "hw+",
    val defaultResizeMode: String = "fit",
    val isMpvAvailable: Boolean = false,
    val keepServerRunning: Boolean = true,

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
    private val driveRepository: DriveRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
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
        keepServerRunning = prefs.keepServerRunning,
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

    fun setKeepServerRunning(enabled: Boolean) {
        prefs.keepServerRunning = enabled
        uiState = uiState.copy(keepServerRunning = enabled)
        if (enabled) {
            com.mkbhdana.streamhive.player.proxy.StreamProxyService.start(context)
        } else {
            com.mkbhdana.streamhive.player.proxy.StreamProxyService.stop(context)
        }
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

    // ──── Folder Browser (for catalog picker) ────

    data class FolderBrowserState(
        val drives: List<com.mkbhdana.streamhive.data.model.SharedDrive> = emptyList(),
        val selectedDriveId: String? = null,
        val currentFolders: List<MediaFileEntity> = emptyList(),
        val folderStack: List<Pair<String, String>> = emptyList(), // id to name
        val isLoading: Boolean = false
    )

    var folderBrowserState by mutableStateOf(FolderBrowserState())
        private set

    fun initFolderBrowser() {
        folderBrowserState = FolderBrowserState(isLoading = true)
        viewModelScope.launch {
            val drivesResult = driveRepository.listSharedDrives()
            drivesResult.onSuccess { drives ->
                folderBrowserState = folderBrowserState.copy(
                    drives = drives,
                    isLoading = false
                )
            }.onFailure {
                folderBrowserState = folderBrowserState.copy(isLoading = false)
            }
        }
    }

    fun browserSelectDrive(driveId: String) {
        folderBrowserState = folderBrowserState.copy(
            selectedDriveId = driveId,
            folderStack = emptyList(),
            isLoading = true,
            currentFolders = emptyList()
        )
        viewModelScope.launch {
            val result = driveRepository.listFilesInDrive(driveId, driveId)
            result.onSuccess {
                val cached = driveRepository.getCachedFiles(driveId, driveId).first()
                folderBrowserState = folderBrowserState.copy(
                    currentFolders = cached.filter { it.isFolder },
                    isLoading = false
                )
            }.onFailure {
                folderBrowserState = folderBrowserState.copy(isLoading = false)
            }
        }
    }

    fun browserOpenFolder(folderId: String, folderName: String) {
        val driveId = folderBrowserState.selectedDriveId ?: return
        folderBrowserState = folderBrowserState.copy(
            folderStack = folderBrowserState.folderStack + (folderId to folderName),
            isLoading = true,
            currentFolders = emptyList()
        )
        viewModelScope.launch {
            val result = driveRepository.listFilesInDrive(driveId, folderId)
            result.onSuccess {
                val cached = driveRepository.getCachedFiles(driveId, folderId).first()
                folderBrowserState = folderBrowserState.copy(
                    currentFolders = cached.filter { it.isFolder },
                    isLoading = false
                )
            }.onFailure {
                folderBrowserState = folderBrowserState.copy(isLoading = false)
            }
        }
    }

    fun browserGoBack() {
        val stack = folderBrowserState.folderStack
        if (stack.isEmpty()) {
            // Go back to drive list
            folderBrowserState = folderBrowserState.copy(
                selectedDriveId = null,
                currentFolders = emptyList()
            )
            return
        }
        val newStack = stack.dropLast(1)
        val driveId = folderBrowserState.selectedDriveId ?: return
        val parentId = newStack.lastOrNull()?.first ?: driveId
        folderBrowserState = folderBrowserState.copy(
            folderStack = newStack,
            isLoading = true,
            currentFolders = emptyList()
        )
        viewModelScope.launch {
            val result = driveRepository.listFilesInDrive(driveId, parentId)
            result.onSuccess {
                val cached = driveRepository.getCachedFiles(driveId, parentId).first()
                folderBrowserState = folderBrowserState.copy(
                    currentFolders = cached.filter { it.isFolder },
                    isLoading = false
                )
            }.onFailure {
                folderBrowserState = folderBrowserState.copy(isLoading = false)
            }
        }
    }

    /** Returns the current folder ID (for adding as catalog folder) */
    fun browserCurrentFolderId(): String? {
        return folderBrowserState.folderStack.lastOrNull()?.first ?: folderBrowserState.selectedDriveId
    }

    // ──── Data Management ────

    fun exportSettings(uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val json = prefs.exportToJson()
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importSettings(uri: android.net.Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (json != null) {
                    val success = prefs.importFromJson(json)
                    if (success) {
                        uiState = loadState()
                    }
                    launch(kotlinx.coroutines.Dispatchers.Main) { onComplete(success) }
                } else {
                    launch(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(kotlinx.coroutines.Dispatchers.Main) { onComplete(false) }
            }
        }
    }
}
