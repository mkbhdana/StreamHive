package com.mkbhdana.streamhive.settings

import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkbhdana.streamhive.catalog.DriveRepository
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.MediaFileDao
import com.mkbhdana.streamhive.data.db.TmdbMetadataDao
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity
import com.mkbhdana.streamhive.data.db.PlaybackHistoryDao
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.ui.image.PosterSource
import com.mkbhdana.streamhive.update.AppUpdateInfo
import com.mkbhdana.streamhive.update.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Outcome of checking a user-entered poster URL. The host serves any path segment, so a
 * URL can only really be judged by fetching it — see [SettingsViewModel.updateBetterPosterTemplate].
 */
enum class PosterUrlStatus { Idle, Checking, Invalid, Unreachable, Saved }

data class SettingsUiState(
    // Player
    val preferredEngine: PlayerEngine = PlayerEngine.EXO_PLAYER,
    val exoDecoder: String = "hw+",
    val mpvDecoder: String = "hw+",
    val mapDv7ToHevc: Boolean = false,
    val tunneledPlaybackEnabled: Boolean = false,
    val defaultResizeMode: String = "fit",
    val isMpvAvailable: Boolean = false,

    // MPV engine
    val mpvProfile: String = AppPreferences.MPV_PROFILE_DEFAULT,
    val mpvGpuNext: Boolean = AppPreferences.MPV_GPU_NEXT_DEFAULT,
    val mpvUseVulkan: Boolean = AppPreferences.MPV_USE_VULKAN_DEFAULT,
    val mpvDebanding: String = AppPreferences.MPV_DEBANDING_DEFAULT,
    val mpvUseYuv420p: Boolean = AppPreferences.MPV_YUV420P_DEFAULT,
    val mpvConfText: String = "",
    val mpvInputConfText: String = "",
    val sourcePriorityResolutions: List<String> = emptyList(),
    val sourcePriorityVideoFormats: List<String> = emptyList(),
    val sourcePriorityDecoders: List<String> = emptyList(),
    val sourcePriorityContainers: List<String> = emptyList(),

    // Gestures
    val gestureVolumeEnabled: Boolean = true,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureSeekEnabled: Boolean = true,
    val gestureDoubleTapEnabled: Boolean = true,
    val gestureZoomEnabled: Boolean = true,
    val gestureSpeedPressEnabled: Boolean = true,
    val gestureLockEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val gestureSensitivity: Float = 1.0f,

    // Audio
    val volumeBoostEnabled: Boolean = false,

    // Artwork
    val thirdPartyPostersEnabled: Boolean = false,
    val betterPosterTemplate: String = "",
    val posterUrlStatus: PosterUrlStatus = PosterUrlStatus.Idle,

    // Subtitles
    val preferredAudioLanguage: String = "original",
    val preferredSubtitleLanguage: String = "none",
    val subtitleExcludeLanguages: Set<String> = emptySet(),
    val exoSubtitleFontSize: Int = 18,
    val mpvSubtitleFontSize: Int = 18,
    val exoSubtitleColor: Long = 0xFFFFFFFF,
    val mpvSubtitleColor: Long = 0xFFFFFFFF,
    val exoSubtitleBgOpacity: Float = 0.0f,
    val mpvSubtitleBgOpacity: Float = 0.0f,
    val exoSubtitlePosition: Int = 90,
    val mpvSubtitlePosition: Int = 90,
    val exoSubtitleEdgeType: String = "outline",
    val mpvSubtitleEdgeType: String = "outline",
    val exoSubtitleEdgeSize: Int = 0,
    val mpvSubtitleEdgeSize: Int = 0,
    val exoSubtitleOutlineColor: Long = 0xFF000000,
    val mpvSubtitleOutlineColor: Long = 0xFF000000,
    val libassSubtitlesEnabled: Boolean = false,
    val exoOverrideAssSubtitleStyles: Boolean = false,
    val mpvOverrideAssSubtitleStyles: Boolean = false,
    val subtitleScale: Float = 1.0f,
    val subtitleFont: String = "sans-serif",
    val subtitleBold: Boolean = false,
    val subtitleItalic: Boolean = false,
    val subtitleAlignment: String = "center",
    
    val tapSeekDuration: Int = 10,

    // TMDB
    val tmdbApiKey: String = "",
    val tmdbMovieFolders: Set<String> = emptySet(),
    val tmdbTvFolders: Set<String> = emptySet(),
    val tmdbRecentFolders: Set<String> = emptySet(),
    val tmdbFolderOrder: List<String> = emptyList(),

    // Updates
    val lastUpdateCheckAt: Long = 0L,
    val isCheckingForUpdate: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Int = 0,
    val updateStatusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val driveRepository: DriveRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val tmdbMetadataDao: TmdbMetadataDao,
    private val mediaFileDao: MediaFileDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val okHttpClient: okhttp3.OkHttpClient,
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
        exoDecoder = prefs.exoDecoder,
        mpvDecoder = prefs.mpvDecoder,
        mapDv7ToHevc = prefs.mapDv7ToHevc,
        tunneledPlaybackEnabled = prefs.tunneledPlaybackEnabled,
        defaultResizeMode = prefs.defaultResizeMode,
        isMpvAvailable = prefs.isMpvAvailable(),
        mpvProfile = prefs.mpvProfile,
        mpvGpuNext = prefs.mpvGpuNext,
        mpvUseVulkan = prefs.mpvUseVulkan,
        mpvDebanding = prefs.mpvDebanding,
        mpvUseYuv420p = prefs.mpvUseYuv420p,
        mpvConfText = prefs.mpvConfText,
        mpvInputConfText = prefs.mpvInputConfText,
        sourcePriorityResolutions = prefs.sourcePriorityResolutions,
        sourcePriorityVideoFormats = prefs.sourcePriorityVideoFormats,
        sourcePriorityDecoders = prefs.sourcePriorityDecoders,
        sourcePriorityContainers = prefs.sourcePriorityContainers,
        gestureVolumeEnabled = prefs.gestureVolumeEnabled,
        gestureBrightnessEnabled = prefs.gestureBrightnessEnabled,
        gestureSeekEnabled = prefs.gestureSeekEnabled,
        gestureDoubleTapEnabled = prefs.gestureDoubleTapEnabled,
        gestureZoomEnabled = prefs.gestureZoomEnabled,
        gestureSpeedPressEnabled = prefs.gestureSpeedPressEnabled,
        gestureLockEnabled = prefs.gestureLockEnabled,
        hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
        gestureSensitivity = prefs.gestureSensitivity,
        tapSeekDuration = prefs.tapSeekDuration,
        volumeBoostEnabled = prefs.volumeBoostEnabled,
        thirdPartyPostersEnabled = prefs.thirdPartyPostersEnabled,
        betterPosterTemplate = prefs.betterPosterTemplate,
        preferredAudioLanguage = prefs.preferredAudioLanguage,
        preferredSubtitleLanguage = prefs.preferredSubtitleLanguage,
        subtitleExcludeLanguages = prefs.subtitleExcludeLanguages,
        exoSubtitleFontSize = prefs.exoSubtitleFontSize,
        mpvSubtitleFontSize = prefs.mpvSubtitleFontSize,
        exoSubtitleColor = prefs.exoSubtitleColor,
        mpvSubtitleColor = prefs.mpvSubtitleColor,
        exoSubtitleBgOpacity = prefs.exoSubtitleBgOpacity,
        mpvSubtitleBgOpacity = prefs.mpvSubtitleBgOpacity,
        exoSubtitlePosition = prefs.exoSubtitlePosition,
        mpvSubtitlePosition = prefs.mpvSubtitlePosition,
        exoSubtitleEdgeType = prefs.exoSubtitleEdgeType,
        mpvSubtitleEdgeType = prefs.mpvSubtitleEdgeType,
        exoSubtitleEdgeSize = prefs.exoSubtitleEdgeSize,
        mpvSubtitleEdgeSize = prefs.mpvSubtitleEdgeSize,
        exoSubtitleOutlineColor = prefs.exoSubtitleOutlineColor,
        mpvSubtitleOutlineColor = prefs.mpvSubtitleOutlineColor,
        libassSubtitlesEnabled = prefs.libassSubtitlesEnabled,
        exoOverrideAssSubtitleStyles = prefs.exoOverrideAssSubtitleStyles,
        mpvOverrideAssSubtitleStyles = prefs.mpvOverrideAssSubtitleStyles,
        subtitleScale = prefs.subtitleScale,
        subtitleFont = prefs.subtitleFont,
        subtitleBold = prefs.subtitleBold,
        subtitleItalic = prefs.subtitleItalic,
        subtitleAlignment = prefs.subtitleAlignment,
        tmdbApiKey = prefs.tmdbApiKey,
        tmdbMovieFolders = prefs.tmdbMovieFolders,
        tmdbTvFolders = prefs.tmdbTvFolders,
        tmdbRecentFolders = prefs.tmdbRecentFolders,
        tmdbFolderOrder = prefs.tmdbFolderOrder,
        lastUpdateCheckAt = prefs.lastUpdateCheckAt
    )

    // ──── Player ────

    fun setPreferredEngine(engine: PlayerEngine) {
        prefs.preferredEngine = engine
        uiState = uiState.copy(preferredEngine = engine)
    }

    fun setExoDecoder(decoder: String) {
        prefs.exoDecoder = decoder
        uiState = uiState.copy(exoDecoder = decoder)
    }

    fun setMpvDecoder(decoder: String) {
        prefs.mpvDecoder = decoder
        uiState = uiState.copy(mpvDecoder = decoder)
    }

    // ──── MPV engine ────

    fun setMpvProfile(profile: String) {
        prefs.mpvProfile = profile
        uiState = uiState.copy(mpvProfile = profile)
    }

    fun setMpvGpuNext(enabled: Boolean) {
        prefs.mpvGpuNext = enabled
        uiState = uiState.copy(mpvGpuNext = enabled)
    }

    fun setMpvUseVulkan(enabled: Boolean) {
        prefs.mpvUseVulkan = enabled
        uiState = uiState.copy(mpvUseVulkan = enabled)
    }

    fun setMpvDebanding(mode: String) {
        prefs.mpvDebanding = mode
        uiState = uiState.copy(mpvDebanding = mode)
    }

    fun setMpvUseYuv420p(enabled: Boolean) {
        prefs.mpvUseYuv420p = enabled
        uiState = uiState.copy(mpvUseYuv420p = enabled)
    }

    fun setMpvConfText(text: String) {
        prefs.mpvConfText = text
        uiState = uiState.copy(mpvConfText = text)
    }

    fun setMpvInputConfText(text: String) {
        prefs.mpvInputConfText = text
        uiState = uiState.copy(mpvInputConfText = text)
    }

    fun resetMpvEngineSettings() {
        prefs.resetMpvEngineSettings()
        uiState = uiState.copy(
            mpvProfile = prefs.mpvProfile,
            mpvGpuNext = prefs.mpvGpuNext,
            mpvUseVulkan = prefs.mpvUseVulkan,
            mpvDebanding = prefs.mpvDebanding,
            mpvUseYuv420p = prefs.mpvUseYuv420p
        )
    }

    fun setMapDv7ToHevc(enabled: Boolean) {
        prefs.mapDv7ToHevc = enabled
        uiState = uiState.copy(mapDv7ToHevc = enabled)
    }

    fun setTunneledPlaybackEnabled(enabled: Boolean) {
        prefs.tunneledPlaybackEnabled = enabled
        uiState = uiState.copy(tunneledPlaybackEnabled = enabled)
    }

    fun setDefaultResizeMode(mode: String) {
        prefs.defaultResizeMode = mode
        uiState = uiState.copy(defaultResizeMode = mode)
    }

    fun setSourcePriorityResolutions(order: List<String>) {
        prefs.sourcePriorityResolutions = order
        uiState = uiState.copy(sourcePriorityResolutions = prefs.sourcePriorityResolutions)
    }

    fun setSourcePriorityVideoFormats(order: List<String>) {
        prefs.sourcePriorityVideoFormats = order
        uiState = uiState.copy(sourcePriorityVideoFormats = prefs.sourcePriorityVideoFormats)
    }

    fun setSourcePriorityDecoders(order: List<String>) {
        prefs.sourcePriorityDecoders = order
        uiState = uiState.copy(sourcePriorityDecoders = prefs.sourcePriorityDecoders)
    }

    fun setSourcePriorityContainers(order: List<String>) {
        prefs.sourcePriorityContainers = order
        uiState = uiState.copy(sourcePriorityContainers = prefs.sourcePriorityContainers)
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

    fun setGestureSpeedPressEnabled(enabled: Boolean) {
        prefs.gestureSpeedPressEnabled = enabled
        uiState = uiState.copy(gestureSpeedPressEnabled = enabled)
    }

    fun setGestureLockEnabled(enabled: Boolean) {
        prefs.gestureLockEnabled = enabled
        uiState = uiState.copy(gestureLockEnabled = enabled)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.hapticFeedbackEnabled = enabled
        uiState = uiState.copy(hapticFeedbackEnabled = enabled)
    }

    fun setGestureSensitivity(sensitivity: Float) {
        prefs.gestureSensitivity = sensitivity
        uiState = uiState.copy(gestureSensitivity = sensitivity)
    }

    fun setTapSeekDuration(duration: Int) {
        prefs.tapSeekDuration = duration
        uiState = uiState.copy(tapSeekDuration = duration)
    }


    // ──── Subtitles ────

    fun setExoSubtitleFontSize(size: Int) {
        prefs.exoSubtitleFontSize = size
        uiState = uiState.copy(exoSubtitleFontSize = size)
    }

    fun setMpvSubtitleFontSize(size: Int) {
        prefs.mpvSubtitleFontSize = size
        uiState = uiState.copy(mpvSubtitleFontSize = size)
    }

    fun setExoSubtitleColor(color: Long) {
        prefs.exoSubtitleColor = color
        uiState = uiState.copy(exoSubtitleColor = color)
    }

    fun setMpvSubtitleColor(color: Long) {
        prefs.mpvSubtitleColor = color
        uiState = uiState.copy(mpvSubtitleColor = color)
    }

    fun setExoSubtitleBgOpacity(opacity: Float) {
        prefs.exoSubtitleBgOpacity = opacity
        uiState = uiState.copy(exoSubtitleBgOpacity = opacity)
    }

    fun setMpvSubtitleBgOpacity(opacity: Float) {
        prefs.mpvSubtitleBgOpacity = opacity
        uiState = uiState.copy(mpvSubtitleBgOpacity = opacity)
    }

    fun setExoSubtitlePosition(position: Int) {
        prefs.exoSubtitlePosition = position
        uiState = uiState.copy(exoSubtitlePosition = position)
    }

    fun setMpvSubtitlePosition(position: Int) {
        prefs.mpvSubtitlePosition = position
        uiState = uiState.copy(mpvSubtitlePosition = position)
    }

    fun setExoSubtitleEdgeType(type: String) {
        prefs.exoSubtitleEdgeType = type
        uiState = uiState.copy(exoSubtitleEdgeType = type)
    }

    fun setMpvSubtitleEdgeType(type: String) {
        prefs.mpvSubtitleEdgeType = type
        uiState = uiState.copy(mpvSubtitleEdgeType = type)
    }

    fun setExoSubtitleEdgeSize(size: Int) {
        prefs.exoSubtitleEdgeSize = size
        uiState = uiState.copy(exoSubtitleEdgeSize = size)
    }

    fun setMpvSubtitleEdgeSize(size: Int) {
        prefs.mpvSubtitleEdgeSize = size
        uiState = uiState.copy(mpvSubtitleEdgeSize = size)
    }

    fun setExoSubtitleOutlineColor(color: Long) {
        prefs.exoSubtitleOutlineColor = color
        uiState = uiState.copy(exoSubtitleOutlineColor = color)
    }

    fun setMpvSubtitleOutlineColor(color: Long) {
        prefs.mpvSubtitleOutlineColor = color
        uiState = uiState.copy(mpvSubtitleOutlineColor = color)
    }

    fun setThirdPartyPostersEnabled(enabled: Boolean) {
        prefs.thirdPartyPostersEnabled = enabled
        // Poster URLs are built from plain helpers, so mirror the flag for them.
        PosterSource.thirdPartyEnabled = enabled
        uiState = uiState.copy(thirdPartyPostersEnabled = enabled)
    }

    /**
     * Check a user-supplied poster URL and save it only if it actually serves an image.
     *
     * A shape check alone is not enough: the host happily serves any path segment, so a
     * typo there still returns the right poster, while a wrong host looks perfectly valid
     * on paper. The URL is therefore probed with a known IMDb id before being accepted.
     * A literal IMDb id in the input is rewritten to the `{imdb_id}` placeholder first.
     */
    fun updateBetterPosterTemplate(input: String) {
        val normalized = PosterSource.normalizeTemplate(input)
        if (normalized == null) {
            uiState = uiState.copy(posterUrlStatus = PosterUrlStatus.Invalid)
            return
        }
        uiState = uiState.copy(posterUrlStatus = PosterUrlStatus.Checking)
        viewModelScope.launch {
            val servesAnImage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val probeUrl = normalized.replace(PosterSource.ID_PLACEHOLDER, POSTER_PROBE_IMDB_ID)
                    val request = okhttp3.Request.Builder().url(probeUrl).head().build()
                    okHttpClient.newCall(request).execute().use { response ->
                        response.isSuccessful &&
                            response.header("Content-Type").orEmpty().startsWith("image", ignoreCase = true)
                    }
                }.getOrDefault(false)
            }
            uiState = if (servesAnImage) {
                prefs.betterPosterTemplate = normalized
                PosterSource.thirdPartyTemplate = normalized
                uiState.copy(
                    betterPosterTemplate = normalized,
                    posterUrlStatus = PosterUrlStatus.Saved
                )
            } else {
                uiState.copy(posterUrlStatus = PosterUrlStatus.Unreachable)
            }
        }
    }

    /** A widely-available title, used purely to prove a poster URL resolves. */
    private val POSTER_PROBE_IMDB_ID = "tt0111161"

    /** Clear the check result once the user edits the field again. */
    fun clearPosterUrlStatus() {
        if (uiState.posterUrlStatus != PosterUrlStatus.Idle) {
            uiState = uiState.copy(posterUrlStatus = PosterUrlStatus.Idle)
        }
    }

    fun resetBetterPosterTemplate() {
        prefs.betterPosterTemplate = PosterSource.DEFAULT_TEMPLATE
        PosterSource.thirdPartyTemplate = PosterSource.DEFAULT_TEMPLATE
        uiState = uiState.copy(
            betterPosterTemplate = PosterSource.DEFAULT_TEMPLATE,
            posterUrlStatus = PosterUrlStatus.Idle
        )
    }

    fun setVolumeBoostEnabled(enabled: Boolean) {
        prefs.volumeBoostEnabled = enabled
        uiState = uiState.copy(volumeBoostEnabled = enabled)
    }

    fun setPreferredAudioLanguage(lang: String) {
        prefs.preferredAudioLanguage = lang
        uiState = uiState.copy(preferredAudioLanguage = lang)
    }

    fun setPreferredSubtitleLanguage(lang: String) {
        prefs.preferredSubtitleLanguage = lang
        uiState = uiState.copy(preferredSubtitleLanguage = lang)
    }

    fun setSubtitleExcludeLanguages(langs: Set<String>) {
        prefs.subtitleExcludeLanguages = langs
        uiState = uiState.copy(subtitleExcludeLanguages = langs)
    }

    fun setLibassSubtitlesEnabled(enabled: Boolean) {
        prefs.libassSubtitlesEnabled = enabled
        uiState = uiState.copy(libassSubtitlesEnabled = enabled)
    }

    fun setExoOverrideAssSubtitleStyles(enabled: Boolean) {
        prefs.exoOverrideAssSubtitleStyles = enabled
        uiState = uiState.copy(exoOverrideAssSubtitleStyles = enabled)
    }

    fun setMpvOverrideAssSubtitleStyles(enabled: Boolean) {
        prefs.mpvOverrideAssSubtitleStyles = enabled
        uiState = uiState.copy(mpvOverrideAssSubtitleStyles = enabled)
    }

    fun setSubtitleScale(scale: Float) {
        prefs.subtitleScale = scale
        uiState = uiState.copy(subtitleScale = scale)
    }

    fun setSubtitleFont(font: String) {
        prefs.subtitleFont = font
        uiState = uiState.copy(subtitleFont = font)
    }

    fun setSubtitleBold(bold: Boolean) {
        prefs.subtitleBold = bold
        uiState = uiState.copy(subtitleBold = bold)
    }

    fun setSubtitleItalic(italic: Boolean) {
        prefs.subtitleItalic = italic
        uiState = uiState.copy(subtitleItalic = italic)
    }

    fun setSubtitleAlignment(alignment: String) {
        prefs.subtitleAlignment = alignment
        uiState = uiState.copy(subtitleAlignment = alignment)
    }

    fun checkForUpdates(onComplete: (String) -> Unit = {}) {
        if (uiState.isCheckingForUpdate) return

        uiState = uiState.copy(isCheckingForUpdate = true)
        viewModelScope.launch {
            val checkedAt = System.currentTimeMillis()
            appUpdateRepository.checkForUpdate().fold(
                onSuccess = { update ->
                    prefs.lastUpdateCheckAt = checkedAt
                    uiState = uiState.copy(
                        isCheckingForUpdate = false,
                        lastUpdateCheckAt = checkedAt,
                        availableUpdate = update,
                        updateStatusMessage = null
                    )
                    onComplete(
                        if (update != null) "Update v${update.versionName} is available"
                        else "You are already on the latest version"
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(isCheckingForUpdate = false)
                    onComplete("Update check failed: ${error.message ?: "Unknown error"}")
                }
            )
        }
    }

    fun dismissUpdatePrompt() {
        uiState = uiState.copy(availableUpdate = null)
    }

    fun downloadAndInstallUpdate() {
        val update = uiState.availableUpdate ?: return
        if (uiState.isDownloadingUpdate) return

        if (!appUpdateRepository.canRequestPackageInstalls()) {
            appUpdateRepository.openInstallPermissionSettings()
            uiState = uiState.copy(
                updateStatusMessage = "Allow StreamHive to install unknown apps, then tap Download again."
            )
            return
        }

        uiState = uiState.copy(
            isDownloadingUpdate = true,
            updateDownloadProgress = 0,
            updateStatusMessage = null
        )

        viewModelScope.launch {
            appUpdateRepository.downloadUpdateApk(update) { progress ->
                uiState = uiState.copy(updateDownloadProgress = progress)
            }.fold(
                onSuccess = { apkFile ->
                    runCatching { appUpdateRepository.launchApkInstaller(apkFile) }
                        .fold(
                            onSuccess = {
                                uiState = uiState.copy(
                                    availableUpdate = null,
                                    isDownloadingUpdate = false,
                                    updateDownloadProgress = 100,
                                    updateStatusMessage = "Opening installer"
                                )
                            },
                            onFailure = { error ->
                                uiState = uiState.copy(
                                    isDownloadingUpdate = false,
                                    updateStatusMessage = error.message ?: "Unable to open installer"
                                )
                            }
                        )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isDownloadingUpdate = false,
                        updateStatusMessage = error.message ?: "Update download failed"
                    )
                }
            )
        }
    }

    fun clearUpdateStatusMessage() {
        uiState = uiState.copy(updateStatusMessage = null)
    }

    // ──── TMDB ────

    private fun touchCatalogSettings() {
        prefs.catalogSettingsLastChanged = System.currentTimeMillis()
    }

    fun setTmdbApiKey(key: String) {
        prefs.tmdbApiKey = key
        uiState = uiState.copy(tmdbApiKey = key)
        touchCatalogSettings()
    }

    private fun removeFolderFromCatalogTypes(folderId: String) {
        prefs.tmdbMovieFolders = prefs.tmdbMovieFolders - folderId
        prefs.tmdbTvFolders = prefs.tmdbTvFolders - folderId
    }

    private fun syncCatalogFoldersState() {
        uiState = uiState.copy(
            tmdbMovieFolders = prefs.tmdbMovieFolders,
            tmdbTvFolders = prefs.tmdbTvFolders,
            tmdbRecentFolders = prefs.tmdbRecentFolders,
            tmdbFolderOrder = prefs.tmdbFolderOrder
        )
    }

    fun addMovieFolder(folderId: String) {
        removeFolderFromCatalogTypes(folderId)
        val updated = prefs.tmdbMovieFolders + folderId
        prefs.tmdbMovieFolders = updated
        syncCatalogFoldersState()
    }

    fun removeMovieFolder(folderId: String) {
        val updated = prefs.tmdbMovieFolders - folderId
        prefs.tmdbMovieFolders = updated
        syncCatalogFoldersState()
    }

    fun addTvFolder(folderId: String) {
        removeFolderFromCatalogTypes(folderId)
        val updated = prefs.tmdbTvFolders + folderId
        prefs.tmdbTvFolders = updated
        syncCatalogFoldersState()
    }

    fun removeTvFolder(folderId: String) {
        val updated = prefs.tmdbTvFolders - folderId
        prefs.tmdbTvFolders = updated
        syncCatalogFoldersState()
    }

    fun addTmdbFolder(folderId: String, type: String) {
        val added = when (type) {
            "movie" -> {
                addMovieFolder(folderId)
                true
            }
            "tv" -> {
                addTvFolder(folderId)
                true
            }
            else -> false
        }
        if (!added) return
        val ordered = getOrderedFolderIds().toMutableList()
        if (folderId !in ordered) {
            ordered.add(folderId)
            prefs.tmdbFolderOrder = ordered
            syncCatalogFoldersState()
        }
        touchCatalogSettings()
    }

    fun removeTmdbFolder(folderId: String) {
        removeFolderFromCatalogTypes(folderId)
        
        val currentRecent = prefs.tmdbRecentFolders
        if (currentRecent.contains(folderId)) {
            val updatedRecent = currentRecent - folderId
            prefs.tmdbRecentFolders = updatedRecent
        }
        
        val ordered = prefs.tmdbFolderOrder.toMutableList()
        if (ordered.remove(folderId)) {
            prefs.tmdbFolderOrder = ordered
        }
        syncCatalogFoldersState()
        touchCatalogSettings()
    }

    fun toggleRecentFolder(folderId: String) {
        val current = prefs.tmdbRecentFolders
        val updated = if (current.contains(folderId)) {
            current - folderId
        } else {
            current + folderId
        }
        prefs.tmdbRecentFolders = updated
        uiState = uiState.copy(tmdbRecentFolders = updated)
        touchCatalogSettings()
    }

    // ──── Folder Ordering ────

    /** Get the ordered list of all mapped folder IDs, synced with current folder sets */
    fun getOrderedFolderIds(): List<String> {
        val allFolderIds = (uiState.tmdbMovieFolders + uiState.tmdbTvFolders).toList()
        val currentOrder = uiState.tmdbFolderOrder
        // Start with items that are in the saved order, then append any new ones
        val ordered = currentOrder.filter { it in allFolderIds }.toMutableList()
        allFolderIds.filter { it !in ordered }.forEach { ordered.add(it) }
        return ordered
    }

    fun moveFolderUp(folderId: String) {
        val ordered = getOrderedFolderIds().toMutableList()
        val index = ordered.indexOf(folderId)
        if (index > 0) {
            ordered.removeAt(index)
            ordered.add(index - 1, folderId)
            prefs.tmdbFolderOrder = ordered
            uiState = uiState.copy(tmdbFolderOrder = ordered)
            touchCatalogSettings()
        }
    }

    fun moveFolderDown(folderId: String) {
        val ordered = getOrderedFolderIds().toMutableList()
        val index = ordered.indexOf(folderId)
        if (index >= 0 && index < ordered.size - 1) {
            ordered.removeAt(index)
            ordered.add(index + 1, folderId)
            prefs.tmdbFolderOrder = ordered
            uiState = uiState.copy(tmdbFolderOrder = ordered)
            touchCatalogSettings()
        }
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

    fun exportSettings(uri: android.net.Uri, includeApiKey: Boolean, includeMetadata: Boolean, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val settingsJson = prefs.exportToJson()
                val rootObject = org.json.JSONObject(settingsJson)

                // Add validation key
                rootObject.put("streamhive_settings_version", 1)

                // Remove API key if user chose not to export it
                if (!includeApiKey) {
                    rootObject.remove("tmdb_api_key")
                }

                // Export playback history
                val history = playbackHistoryDao.getAll()
                if (history.isNotEmpty()) {
                    val historyArray = org.json.JSONArray()
                    history.forEach { h ->
                        val obj = org.json.JSONObject()
                        obj.put("fileId", h.fileId)
                        obj.put("fileName", h.fileName)
                        obj.put("driveId", h.driveId)
                        obj.put("lastPosition", h.lastPosition)
                        obj.put("duration", h.duration)
                        obj.put("lastPlayedAt", h.lastPlayedAt)
                        obj.put("posterPath", h.posterPath ?: "")
                        obj.put("thumbnailUrl", h.thumbnailUrl ?: "")
                        obj.put("lastPlayerEngine", h.lastPlayerEngine ?: "")
                        obj.put("lastDecoderMode", h.lastDecoderMode ?: "")
                        historyArray.put(obj)
                    }
                    rootObject.put("playback_history", historyArray)
                }

                // Export mapped folder names to avoid weird IDs on import
                val allMappedFolders = prefs.tmdbMovieFolders + prefs.tmdbTvFolders
                if (allMappedFolders.isNotEmpty()) {
                    val foldersArray = org.json.JSONArray()
                    val allFoldersFromDb = mediaFileDao.getAllFolders().first()
                    allMappedFolders.forEach { id ->
                        val folder = allFoldersFromDb.find { it.id == id }
                        val obj = org.json.JSONObject()
                        obj.put("id", id)
                        obj.put("name", folder?.name ?: id)
                        obj.put("mimeType", folder?.mimeType ?: "application/vnd.google-apps.folder")
                        obj.put("parentId", folder?.parentId ?: "")
                        obj.put("driveId", folder?.driveId ?: "")
                        obj.put("modifiedTime", folder?.modifiedTime ?: "")
                        obj.put("createdTime", folder?.createdTime ?: "")
                        foldersArray.put(obj)
                    }
                    rootObject.put("tmdb_folder_names", foldersArray)
                }

                // Export fixed/edited metadata
                if (includeMetadata) {
                    val allMeta = tmdbMetadataDao.getByMediaType("movie") + tmdbMetadataDao.getByMediaType("tv")
                    if (allMeta.isNotEmpty()) {
                        val metaArray = org.json.JSONArray()
                        allMeta.forEach { m ->
                            val obj = org.json.JSONObject()
                            obj.put("driveFileId", m.driveFileId)
                            obj.put("tmdbId", m.tmdbId)
                            obj.put("title", m.title)
                            obj.put("overview", m.overview ?: "")
                            obj.put("posterPath", m.posterPath ?: "")
                            obj.put("backdropPath", m.backdropPath ?: "")
                            obj.put("rating", m.rating?.toDouble() ?: 0.0)
                            obj.put("year", m.year ?: "")
                            obj.put("originalLanguage", m.originalLanguage ?: "")
                            obj.put("mediaType", m.mediaType)
                            obj.put("imdbId", m.imdbId ?: "")
                            obj.put("cachedAt", m.cachedAt)
                            metaArray.put(obj)
                        }
                        rootObject.put("tmdb_metadata", metaArray)
                    }
                }

                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(rootObject.toString(2).toByteArray())
                }
                launch(kotlinx.coroutines.Dispatchers.Main) { onComplete(true, null) }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(kotlinx.coroutines.Dispatchers.Main) { onComplete(false, e.message) }
            }
        }
    }

    fun importSettings(uri: android.net.Uri, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (json != null) {
                    val jsonObject = org.json.JSONObject(json)

                    // Validate file, while still accepting backups created before the version marker.
                    val hasKnownSettingsKey = listOf(
                        "tmdb_api_key",
                        "tmdb_movie_folders",
                        "tmdb_tv_folders",
                        "player_engine",
                        "selected_drive_id"
                    ).any { jsonObject.has(it) }
                    if (!jsonObject.has("streamhive_settings_version") && !hasKnownSettingsKey) {
                        launch(kotlinx.coroutines.Dispatchers.Main) { onComplete(false, "Invalid settings file format") }
                        return@launch
                    }

                    // Import mapped folder names
                    if (jsonObject.has("tmdb_folder_names")) {
                        val foldersArray = jsonObject.getJSONArray("tmdb_folder_names")
                        val importedFolders = mutableListOf<MediaFileEntity>()
                        val fallbackDriveId = jsonObject.optString("selected_drive_id")
                            .takeIf { it.isNotBlank() }
                            ?: prefs.selectedDriveId.takeIf { it.isNotBlank() }
                        for (i in 0 until foldersArray.length()) {
                            val obj = foldersArray.getJSONObject(i)
                            val folderId = obj.getString("id")
                            val apiFolder = if (obj.optString("driveId").isBlank()) {
                                driveRepository.getFileByIdViaApi(folderId).getOrNull()
                            } else {
                                null
                            }
                            val driveId = obj.optString("driveId")
                                .takeIf { it.isNotBlank() }
                                ?: apiFolder?.driveId?.takeIf { it.isNotBlank() }
                                ?: fallbackDriveId
                                ?: ""
                            importedFolders.add(
                                MediaFileEntity(
                                    id = folderId,
                                    name = obj.optString("name").ifBlank { apiFolder?.name ?: folderId },
                                    mimeType = obj.optString("mimeType")
                                        .ifBlank { apiFolder?.mimeType ?: "application/vnd.google-apps.folder" },
                                    size = 0,
                                    isFolder = true,
                                    parentId = obj.optString("parentId")
                                        .ifBlank { apiFolder?.parents?.firstOrNull() ?: driveId },
                                    driveId = driveId,
                                    modifiedTime = obj.optString("modifiedTime")
                                        .ifBlank { apiFolder?.modifiedTime ?: "" },
                                    createdTime = obj.optString("createdTime")
                                        .ifBlank { apiFolder?.createdTime ?: "" }
                                )
                            )
                        }
                        if (importedFolders.isNotEmpty()) {
                            mediaFileDao.insertFiles(importedFolders)
                        }
                        jsonObject.remove("tmdb_folder_names")
                    }

                    // Import the TMDB metadata cache FIRST. The playback-history upserts below
                    // trigger the Continue Watching flow, which resolves posters by title/id
                    // against this cache — so it must already be populated, otherwise resolution
                    // falls back to a file-name search until the file is played again.
                    if (jsonObject.has("tmdb_metadata")) {
                        val metaArray = jsonObject.getJSONArray("tmdb_metadata")
                        val entities = mutableListOf<TmdbMetadataEntity>()
                        for (i in 0 until metaArray.length()) {
                            val obj = metaArray.getJSONObject(i)
                            entities.add(TmdbMetadataEntity(
                                driveFileId = obj.getString("driveFileId"),
                                tmdbId = obj.getInt("tmdbId"),
                                title = obj.getString("title"),
                                overview = obj.optString("overview").ifBlank { null },
                                posterPath = obj.optString("posterPath").ifBlank { null },
                                backdropPath = obj.optString("backdropPath").ifBlank { null },
                                rating = obj.optDouble("rating", 0.0).toFloat().takeIf { it > 0f },
                                year = obj.optString("year").ifBlank { null },
                                originalLanguage = obj.optString("originalLanguage").ifBlank { null },
                                mediaType = obj.optString("mediaType", "movie"),
                                imdbId = obj.optString("imdbId").ifBlank { null },
                                cachedAt = obj.optLong("cachedAt", System.currentTimeMillis())
                            ))
                        }
                        if (entities.isNotEmpty()) {
                            tmdbMetadataDao.insertAll(entities)
                        }
                        // Remove from JSON so prefs import doesn't fail on it
                        jsonObject.remove("tmdb_metadata")
                    }

                    // Import playback history (after the metadata cache above).
                    if (jsonObject.has("playback_history")) {
                        val historyArray = jsonObject.getJSONArray("playback_history")
                        for (i in 0 until historyArray.length()) {
                            val obj = historyArray.getJSONObject(i)
                            playbackHistoryDao.upsert(PlaybackHistoryEntity(
                                fileId = obj.getString("fileId"),
                                fileName = obj.getString("fileName"),
                                driveId = obj.getString("driveId"),
                                lastPosition = obj.getLong("lastPosition"),
                                duration = obj.getLong("duration"),
                                lastPlayedAt = obj.getLong("lastPlayedAt"),
                                posterPath = obj.optString("posterPath").ifBlank { null },
                                thumbnailUrl = obj.optString("thumbnailUrl").ifBlank { null },
                                lastPlayerEngine = obj.optString("lastPlayerEngine").ifBlank { null },
                                lastDecoderMode = obj.optString("lastDecoderMode").ifBlank { null }
                            ))
                        }
                        jsonObject.remove("playback_history")
                    }

                    // Remove validation key so AppPreferences doesn't fail parsing it
                    jsonObject.remove("streamhive_settings_version")

                    val success = prefs.importFromJson(jsonObject.toString())
                    if (success) {
                        touchCatalogSettings()
                        // Restored artwork settings must reach the poster URL builder.
                        prefs.applyPosterSourceSettings()
                        uiState = loadState()
                    }
                    launch(kotlinx.coroutines.Dispatchers.Main) { onComplete(success, null) }
                } else {
                    launch(kotlinx.coroutines.Dispatchers.Main) { onComplete(false, "Could not read file") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(kotlinx.coroutines.Dispatchers.Main) { onComplete(false, "Invalid file format") }
            }
        }
    }

    // ──── Storage & Cache ────

    @OptIn(ExperimentalCoilApi::class)
    fun calculateCacheSizes(onResult: (imageSize: Long, dbSize: Long) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var imageSize = 0L
            var dbSize = 0L
            try {
                val diskCache = context.imageLoader.diskCache
                imageSize = diskCache?.size ?: 0L
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            try {
                val dbFile = context.getDatabasePath("streamhive_database")
                if (dbFile.exists()) {
                    dbSize += dbFile.length()
                    val walFile = java.io.File(dbFile.path + "-wal")
                    if (walFile.exists()) dbSize += walFile.length()
                    val shmFile = java.io.File(dbFile.path + "-shm")
                    if (shmFile.exists()) dbSize += shmFile.length()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            launch(kotlinx.coroutines.Dispatchers.Main) { onResult(imageSize, dbSize) }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCacheAndData(onComplete: () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Clear Image Cache
                context.imageLoader.diskCache?.clear()
                context.imageLoader.memoryCache?.clear()

                // Clear Catalog DB
                tmdbMetadataDao.deleteAll()
                mediaFileDao.deleteAll()
                playbackHistoryDao.deleteAll()
                
                // Clear TMDB settings
                prefs.tmdbApiKey = ""
                prefs.tmdbMovieFolders = emptySet()
                prefs.tmdbTvFolders = emptySet()
                prefs.tmdbRecentFolders = emptySet()
                prefs.tmdbFolderOrder = emptyList()
                touchCatalogSettings()

                launch(kotlinx.coroutines.Dispatchers.Main) {
                    uiState = loadState()
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun clearPlaybackCacheAndData(onComplete: () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                playbackHistoryDao.deleteAll()
                launch(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun resetPreferences(onComplete: () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // We keep the TMDB API key and catalog folders by storing and restoring them
                val currentTmdbKey = prefs.tmdbApiKey
                val movieFolders = prefs.tmdbMovieFolders
                val tvFolders = prefs.tmdbTvFolders
                val recentFolders = prefs.tmdbRecentFolders
                val folderOrder = prefs.tmdbFolderOrder
                
                prefs.clearAll()
                
                prefs.tmdbApiKey = currentTmdbKey
                prefs.tmdbMovieFolders = movieFolders
                prefs.tmdbTvFolders = tvFolders
                prefs.tmdbRecentFolders = recentFolders
                prefs.tmdbFolderOrder = folderOrder

                launch(kotlinx.coroutines.Dispatchers.Main) {
                    uiState = loadState()
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
            }
        }
    }
}
