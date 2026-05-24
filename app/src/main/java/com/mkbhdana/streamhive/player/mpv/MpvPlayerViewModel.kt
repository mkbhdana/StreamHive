package com.mkbhdana.streamhive.player.mpv

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Surface
import android.view.SurfaceView
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkbhdana.streamhive.catalog.DriveRepository
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.PlaybackHistoryDao
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataDao
import com.mkbhdana.streamhive.data.model.DriveFile
import com.mkbhdana.streamhive.player.PlayerUiState
import com.mkbhdana.streamhive.player.ui.TrackInfo
import com.mkbhdana.streamhive.settings.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MpvPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveRepository: DriveRepository,
    private val appPreferences: AppPreferences,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val mediaFileDao: com.mkbhdana.streamhive.data.db.MediaFileDao,
    private val tmdbMetadataDao: TmdbMetadataDao,
    private val streamProxyServer: com.mkbhdana.streamhive.player.proxy.StreamProxyServer,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var currentFileId: String = savedStateHandle.get<String>("fileId") ?: ""
    private var currentFileName: String = java.net.URLDecoder.decode(
        savedStateHandle.get<String>("fileName") ?: "", "UTF-8"
    )
    private val initialDecoderMode: String = normalizeDecoderMode(
        decodeDecoderRouteValue(savedStateHandle.get<String>("decoder"))
            ?: appPreferences.defaultDecoder
    )

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            fileName = currentFileName,
            resizeMode = appPreferences.defaultResizeMode,
            gestureSeekEnabled = appPreferences.gestureSeekEnabled,
            gestureVolumeEnabled = appPreferences.gestureVolumeEnabled,
            gestureBrightnessEnabled = appPreferences.gestureBrightnessEnabled,
            gestureDoubleTapEnabled = appPreferences.gestureDoubleTapEnabled,
            gestureZoomEnabled = appPreferences.gestureZoomEnabled,
            gestureSpeedPressEnabled = appPreferences.gestureSpeedPressEnabled,
            gestureLockEnabled = appPreferences.gestureLockEnabled,
            hapticFeedbackEnabled = appPreferences.hapticFeedbackEnabled,
            gestureSensitivity = appPreferences.gestureSensitivity,
            subtitleFontSize = appPreferences.subtitleFontSize,
            subtitleColor = appPreferences.subtitleColor,
            subtitleBgOpacity = appPreferences.subtitleBgOpacity,
            subtitlePosition = appPreferences.subtitlePosition,
            subtitleEdgeType = appPreferences.subtitleEdgeType,
            subtitleEdgeSize = appPreferences.subtitleEdgeSize,
            subtitleOutlineColor = appPreferences.subtitleOutlineColor,
            libassSubtitlesEnabled = appPreferences.libassSubtitlesEnabled,
            overrideAssSubtitleStyles = appPreferences.overrideAssSubtitleStyles,
            tapSeekDuration = appPreferences.tapSeekDuration,
            decoderMode = initialDecoderMode
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val mpvPlayer = MpvPlayer(context)
    private var sessionDecoderMode: String = initialDecoderMode
    private var handoffPlayerEngine: PlayerEngine? = null

    // Resume playback support
    private var pendingSeekMs: Long = 0L
    private var hasResumed: Boolean = false
    private var positionSaveJob: kotlinx.coroutines.Job? = null
    private var externalPlayerCleanupJob: kotlinx.coroutines.Job? = null
    
    // Error retry mechanism
    private var retryCount = 0
    private val MAX_RETRIES = 3
    private var preferredTracksApplied = false
    private var pendingExternalSubtitleTrackRefresh = false
    private var tmdbOriginalAudioLanguage: String? = null

    // Per-file / session-level track overrides (from saved settings or series carryover)
    private var sessionAudioLanguage: String? = null
    private var sessionAudioLabel: String? = null
    private var sessionSubtitleLanguage: String? = null
    private var sessionSubtitleLabel: String? = null
    private val baseSubtitleScale: Double
        get() = (_uiState.value.subtitleFontSize.coerceIn(10, 48) / 18.0).coerceIn(0.55, 2.7)

    init {
        rememberPlaybackSelection()
        setupPlayer()
        fetchEpisodeList()
    }

    private fun extractSeriesName(name: String): String {
        val regex = Regex("""(?i)(.*?)[.\s-_]*(?:S\d{1,2}\s*E\d{1,2}|\d{1,2}x\d{1,2}|Season\s*\d+\s*Episode\s*\d+)""")
        val match = regex.find(name)
        return if (match != null) match.groupValues[1].trim() else name.substringBeforeLast('.')
    }

    private fun fetchEpisodeList() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val currentFile = resolveCurrentFileForEpisodes() ?: return@launch
                val allFiles = loadEpisodeSiblings(currentFile)
                val seriesName = extractSeriesName(currentFileName)
                val episodes = allFiles.filter { 
                    !it.isFolder && 
                    extractSeriesName(it.name).equals(seriesName, ignoreCase = true) 
                }
                _uiState.update { it.copy(episodeList = episodes) }
            } catch (e: Exception) {
                // Log or handle error
            }
        }
    }

    private suspend fun resolveCurrentFileForEpisodes(): MediaFileEntity? {
        mediaFileDao.getFileById(currentFileId)?.let { return it }

        val history = playbackHistoryDao.getByFileId(currentFileId)
        val apiFile = driveRepository.getFileByIdViaApi(currentFileId).getOrNull() ?: return null
        val parentId = apiFile.parents?.firstOrNull()
        val driveId = apiFile.driveId?.takeIf { it.isNotBlank() }
            ?: history?.driveId?.takeIf { it.isNotBlank() }
            ?: appPreferences.selectedDriveId.takeIf { it.isNotBlank() }
            ?: "system_root"

        val entity = apiFile.toMediaFileEntity(
            driveId = driveId,
            parentId = parentId ?: driveId
        )
        mediaFileDao.insertFile(entity)
        return entity
    }

    private suspend fun loadEpisodeSiblings(currentFile: MediaFileEntity): List<MediaFileEntity> {
        val cached = mediaFileDao.getFilesByFolderSync(currentFile.driveId, currentFile.parentId)
        if (cached.count { !it.isFolder } > 1 || currentFile.parentId.isNullOrBlank()) {
            return cached
        }

        driveRepository.listFilesInDrive(currentFile.driveId, currentFile.parentId)
        val refreshed = mediaFileDao.getFilesByFolderSync(currentFile.driveId, currentFile.parentId)
        return refreshed.ifEmpty { cached.ifEmpty { listOf(currentFile) } }
    }

    private fun DriveFile.toMediaFileEntity(driveId: String, parentId: String): MediaFileEntity {
        return MediaFileEntity(
            id = id,
            name = name,
            mimeType = mimeType,
            size = size,
            thumbnailLink = thumbnailLink,
            modifiedTime = modifiedTime,
            createdTime = createdTime,
            parentId = parentId,
            driveId = driveId,
            fileExtension = fileExtension,
            isFolder = isFolder,
            videoWidth = videoMediaMetadata?.width,
            videoHeight = videoMediaMetadata?.height,
            videoDurationMs = videoMediaMetadata?.durationMillis
        )
    }

    fun playEpisode(fileId: String, fileName: String) {
        if (fileId == currentFileId) return
        
        savePlaybackPosition()
        currentFileId = fileId
        currentFileName = fileName
        
        _uiState.update { 
            it.copy(
                fileName = fileName, 
                isLoading = true,
                isPlaying = false,
                error = null,
                showControls = false,
                currentPosition = 0L,
                bufferedPercentage = 0
            ) 
        }
        
        hasResumed = false
        pendingSeekMs = 0L
        pendingExternalSubtitleTrackRefresh = false
        preferredTracksApplied = false
        rememberPlaybackSelection()
        fetchEpisodeList()

        viewModelScope.launch {
            tmdbOriginalAudioLanguage = resolveTmdbOriginalLanguage()
            val streamUrl = streamProxyServer.getStreamUrl(currentFileId)
            mpvPlayer.loadFile(streamUrl)
            mpvPlayer.play()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isPlaying = true,
                    error = null,
                    showControls = false
                )
            }
        }
    }

    private fun setupPlayer() {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        isPlaying = false,
                        error = null,
                        showControls = false
                    )
                }

                tmdbOriginalAudioLanguage = resolveTmdbOriginalLanguage()

                mpvPlayer.setEventListener(object : MpvPlayer.EventListener {
                    override fun onPropertyChange(property: String, value: Any?) {}

                    override fun onPlaybackStateChanged(isPlaying: Boolean) {
                        _uiState.update { it.copy(isPlaying = isPlaying) }
                        if (isPlaying) {
                            savePlaybackPosition()
                            startPeriodicPositionSave()
                        } else {
                            positionSaveJob?.cancel()
                            savePlaybackPosition()
                        }
                    }

                    override fun onDurationChanged(durationMs: Long) {
                        _uiState.update {
                            it.copy(
                                duration = durationMs,
                                isLoading = if (durationMs > 0) false else it.isLoading
                            )
                        }
                    }

                    override fun onPositionChanged(positionMs: Long) {
                        _uiState.update {
                            it.copy(
                                currentPosition = positionMs,
                                isLoading = if (positionMs > 0 || it.duration > 0) false else it.isLoading
                            )
                        }
                        // Reset retries once we have a valid playback position
                        if (positionMs > 0) retryCount = 0
                        // Resume to saved position once we get a valid position (file loaded)
                        if (!hasResumed && pendingSeekMs > 0 && positionMs >= 0) {
                            mpvPlayer.seekTo(pendingSeekMs)
                            hasResumed = true
                            pendingSeekMs = 0L
                        }
                    }

                    override fun onError(message: String) {
                        if (isRecoverablePlaybackError(message) && retryCount < MAX_RETRIES) {
                            retryCount++
                            val pos = _uiState.value.currentPosition
                            if (pos > 0) pendingSeekMs = pos
                            hasResumed = false
                            android.util.Log.w("MpvVM", "MPV Error. Retrying ($retryCount/$MAX_RETRIES): $message")
                            _uiState.update {
                                it.copy(
                                    isLoading = true,
                                    isPlaying = false,
                                    error = null,
                                    showControls = false
                                )
                            }
                            val streamUrl = streamProxyServer.getStreamUrl(currentFileId)
                            mpvPlayer.loadFile(streamUrl)
                            mpvPlayer.play()
                        } else {
                            val retrySuffix = if (retryCount >= MAX_RETRIES) {
                                " (Failed after $MAX_RETRIES retries)"
                            } else {
                                ""
                            }
                            _uiState.update {
                                it.copy(
                                    error = "MPV Error: $message$retrySuffix",
                                    isLoading = false,
                                    isPlaying = false,
                                    showControls = false
                                )
                            }
                        }
                    }

                    override fun onBuffering(isBuffering: Boolean) {
                        _uiState.update {
                            val hasLoadedMedia = it.duration > 0 || it.currentPosition > 0
                            it.copy(
                                isLoading = when {
                                    isBuffering -> true
                                    hasLoadedMedia -> false
                                    else -> it.isLoading
                                }
                            )
                        }
                    }

                    override fun onTracksChanged() {
                        updateTrackInfo()
                    }
                })

                mpvPlayer.initialize(
                    useLibassSubtitles = appPreferences.libassSubtitlesEnabled,
                    overrideAssStyles = appPreferences.overrideAssSubtitleStyles,
                    decoderMode = sessionDecoderMode
                )
                applySubtitleStyle()

                val history = playbackHistoryDao.getByFileId(currentFileId)
                restorePerFileSettings(history)
                val startPosMs = if (history != null && history.isResumeEligible && history.lastPosition > 0) history.lastPosition else 0L
                if (startPosMs > 0) {
                    pendingSeekMs = startPosMs
                }

                // Use proxy URL — no auth headers needed
                val streamUrl = streamProxyServer.getStreamUrl(currentFileId)
                mpvPlayer.loadFile(streamUrl)
                mpvPlayer.play()
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        isPlaying = true,
                        error = null,
                        showControls = false
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = false,
                        showControls = false,
                        error = "Failed to start MPV player: ${e.message}"
                    )
                }
            }
        }
    }

    private fun isRecoverablePlaybackError(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return "library not found" !in lowerMessage &&
            "failed to initialize" !in lowerMessage &&
            "not initialized" !in lowerMessage
    }

    private fun updateTrackInfo() {
        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()

        val trackCount = mpvPlayer.getTrackCount()
        for (i in 0 until trackCount) {
            val type = mpvPlayer.getTrackType(i)
            val title = mpvPlayer.getTrackTitle(i)
            val lang = mpvPlayer.getTrackLang(i)
            val codec = mpvPlayer.getTrackCodec(i)
            val id = mpvPlayer.getTrackId(i)
            val selected = mpvPlayer.isTrackSelected(i)

            when (type) {
                "audio" -> {
                    audioTracks.add(
                        TrackInfo(
                            index = id,
                            name = title.ifBlank { "Audio ${audioTracks.size + 1}" },
                            language = lang.ifBlank { null },
                            codec = codec.ifBlank { null },
                            isSelected = selected
                        )
                    )
                }
                "sub" -> {
                    val isExternal = mpvPlayer.isTrackExternal(i)
                    val externalFileName = mpvPlayer.getTrackExternalFileName(i)
                        .substringAfterLast('/')
                        .takeIf { it.isNotBlank() }
                    val name = title.ifBlank {
                        externalFileName ?: "Subtitle ${subtitleTracks.size + 1}"
                    }
                    subtitleTracks.add(
                        TrackInfo(
                            index = id,
                            name = name,
                            language = lang.ifBlank { null },
                            codec = codec.ifBlank { null },
                            isSelected = selected,
                            isExternal = isExternal,
                            canRemove = isExternal,
                            sourceId = id.toString()
                        )
                    )
                }
            }
        }

        if (pendingExternalSubtitleTrackRefresh && subtitleTracks.isEmpty()) {
            _uiState.update {
                it.copy(
                    audioTracks = audioTracks.ifEmpty { it.audioTracks },
                    subtitleTracks = it.subtitleTracks
                )
            }
            return
        }
        if (pendingExternalSubtitleTrackRefresh && subtitleTracks.isNotEmpty()) {
            pendingExternalSubtitleTrackRefresh = false
        }

        val preferredAudioTrack = maybeApplyPreferredAudioTrack(audioTracks)
        val displayedAudioTracks = if (preferredAudioTrack != null) {
            audioTracks.map { track ->
                track.copy(isSelected = track.index == preferredAudioTrack.index)
            }
        } else {
            audioTracks
        }

        _uiState.update {
            it.copy(audioTracks = displayedAudioTracks, subtitleTracks = subtitleTracks)
        }
    }

    private fun maybeApplyPreferredAudioTrack(audioTracks: List<TrackInfo>): TrackInfo? {
        if (preferredTracksApplied || audioTracks.isEmpty()) return null

        // Session overrides (from per-file settings) take priority
        if (sessionAudioLanguage != null) {
            // Try label match first for precise selection
            val labelMatch = if (sessionAudioLabel != null) {
                audioTracks.firstOrNull { it.name == sessionAudioLabel }
            } else null
            val langMatch = labelMatch ?: audioTracks.firstOrNull {
                languageMatches(it.language, sessionAudioLanguage!!)
            }
            preferredTracksApplied = true
            if (langMatch != null && !langMatch.isSelected) {
                mpvPlayer.setAudioTrack(langMatch.index)
                return langMatch
            }
            return null
        }

        val preferredAudioLanguage = appPreferences.preferredAudioLanguage
        val targetAudioLanguage = if (preferredAudioLanguage == "original") {
            tmdbOriginalAudioLanguage
        } else {
            preferredAudioLanguage
        }

        if (targetAudioLanguage.isNullOrBlank()) {
            preferredTracksApplied = true
            return null
        }

        val preferredTrack = audioTracks.firstOrNull {
            languageMatches(it.language, targetAudioLanguage)
        }
        preferredTracksApplied = true
        if (preferredTrack != null && !preferredTrack.isSelected) {
            mpvPlayer.setAudioTrack(preferredTrack.index)
            return preferredTrack
        }
        return null
    }

    private fun languageMatches(trackLanguage: String?, preferredLanguage: String): Boolean {
        if (trackLanguage.isNullOrBlank() || preferredLanguage.isBlank()) return false
        return languageAliases(trackLanguage).any { it in languageAliases(preferredLanguage) }
    }

    private fun languageAliases(language: String): Set<String> {
        val normalized = language
            .lowercase(Locale.US)
            .substringBefore("-")
            .substringBefore("_")
        return when (normalized) {
            "eng", "en" -> setOf("eng", "en")
            "kor", "ko", "kr" -> setOf("kor", "ko", "kr")
            "jpn", "ja", "jp" -> setOf("jpn", "ja", "jp")
            "mal", "ml" -> setOf("mal", "ml")
            "tam", "ta" -> setOf("tam", "ta")
            "hin", "hi" -> setOf("hin", "hi")
            "spa", "es" -> setOf("spa", "es")
            "fra", "fre", "fr" -> setOf("fra", "fre", "fr")
            "deu", "ger", "de" -> setOf("deu", "ger", "de")
            "por", "pt" -> setOf("por", "pt")
            "ita", "it" -> setOf("ita", "it")
            "rus", "ru" -> setOf("rus", "ru")
            "ara", "ar" -> setOf("ara", "ar")
            "zho", "chi", "zh", "cmn" -> setOf("zho", "chi", "zh", "cmn")
            "tha", "th" -> setOf("tha", "th")
            else -> setOf(normalized)
        }
    }

    private suspend fun resolveTmdbOriginalLanguage(): String? {
        tmdbMetadataDao.getByDriveFileId(currentFileId)
            ?.originalLanguage
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val parentId = mediaFileDao.getFileById(currentFileId)
            ?.parentId
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return tmdbMetadataDao.getByDriveFileId(parentId)
            ?.originalLanguage
            ?.takeIf { it.isNotBlank() }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        mpvPlayer.attachSurface(surfaceView)
    }

    fun attachSurface(surface: Surface, width: Int, height: Int) {
        mpvPlayer.attachSurface(surface, width, height)
    }

    fun updateSurfaceSize(width: Int, height: Int) {
        mpvPlayer.updateSurfaceSize(width, height)
    }

    fun detachSurface() {
        mpvPlayer.detachSurface()
    }

    fun refreshSurface() {
        mpvPlayer.refreshSurface()
    }

    fun suspendVideoOutputForTransientView() {
        mpvPlayer.suspendVideoOutputForTransientView()
    }

    fun recoverVideoOutput() {
        mpvPlayer.recoverVideoOutput()
    }

    fun detachSurfaceForPause() {
        mpvPlayer.detachSurfaceForPause()
    }

    fun togglePlayPause() = mpvPlayer.togglePlayPause()

    fun seekTo(positionMs: Long) = mpvPlayer.seekTo(positionMs)

    fun seekForward(ms: Long = 10_000) = mpvPlayer.seekRelative(ms)

    fun seekBackward(ms: Long = 10_000) = mpvPlayer.seekRelative(-ms)

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun showControls() {
        _uiState.update { it.copy(showControls = true) }
    }

    fun hideControls() {
        _uiState.update { it.copy(showControls = false) }
    }

    fun getProxyUrl(): String? = streamProxyServer.getStreamUrl(currentFileId)

    fun prepareForEngineFallback(targetEngine: PlayerEngine? = null) {
        handoffPlayerEngine = targetEngine
        mpvPlayer.pause()
        savePlaybackPosition()
        _uiState.update {
            it.copy(
                isLoading = true,
                isPlaying = false,
                error = null,
                showControls = false
            )
        }
    }

    fun pauseForExternalLaunch() {
        mpvPlayer.pause()
        savePlaybackPosition()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun scheduleExternalPlayerCleanup() {
        externalPlayerCleanupJob?.cancel()
        externalPlayerCleanupJob = viewModelScope.launch {
            kotlinx.coroutines.delay(EXTERNAL_PLAYER_CLEANUP_DELAY_MS)
            savePlaybackPosition()
            positionSaveJob?.cancel()
            mpvPlayer.destroy()
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isLoading = false,
                    showControls = false
                )
            }
        }
    }

    fun cancelExternalPlayerCleanup() {
        externalPlayerCleanupJob?.cancel()
        externalPlayerCleanupJob = null
    }

    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun setResizeMode(mode: String) {
        _uiState.update { it.copy(resizeMode = mode) }
    }

    fun selectAudioTrack(trackId: Int) {
        preferredTracksApplied = true
        mpvPlayer.setAudioTrack(trackId)
        updateTrackInfo()
        debounceSaveFileSettings()
    }

    fun selectSubtitleTrack(trackId: Int) {
        preferredTracksApplied = true
        if (trackId < 0) {
            mpvPlayer.disableSubtitles()
        } else {
            mpvPlayer.setSubtitleTrack(trackId)
        }
        updateTrackInfo()
        debounceSaveFileSettings()
    }

    fun removeExternalSubtitle(track: TrackInfo) {
        if (!track.isExternal) return
        mpvPlayer.removeSubtitleTrack(track.index)
        pendingExternalSubtitleTrackRefresh = true
        updateTrackInfo()
        viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            updateTrackInfo()
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        _uiState.update { it.copy(subtitleDelay = delayMs) }
        mpvPlayer.setSubtitleDelay(delayMs / 1000.0)
        debounceSaveFileSettings()
    }

    fun setSubtitleFontSize(fontSize: Int) {
        _uiState.update { it.copy(subtitleFontSize = fontSize.coerceIn(10, 48)) }
        applySubtitleStyle()
        debounceSaveFileSettings()
    }

    fun setSubtitleColor(color: Long) {
        _uiState.update { it.copy(subtitleColor = color) }
        applySubtitleStyle()
        debounceSaveFileSettings()
    }

    fun setSubtitlePosition(position: Int) {
        _uiState.update { it.copy(subtitlePosition = position.coerceIn(0, 100)) }
        applySubtitleStyle()
        debounceSaveFileSettings()
    }

    fun setSubtitleOutlineColor(color: Long) {
        _uiState.update { it.copy(subtitleOutlineColor = color) }
        applySubtitleStyle()
        debounceSaveFileSettings()
    }

    fun setSubtitleBgOpacity(opacity: Float) {
        _uiState.update { it.copy(subtitleBgOpacity = opacity.coerceIn(0f, 1f)) }
        applySubtitleStyle()
        debounceSaveFileSettings()
    }

    fun setSubtitleEdgeSize(edgeSize: Int) {
        _uiState.update { it.copy(subtitleEdgeSize = edgeSize.coerceIn(0, 20)) }
        applySubtitleStyle()
        debounceSaveFileSettings()
    }

    fun setOverrideAssSubtitleStyles(enabled: Boolean) {
        _uiState.update { it.copy(overrideAssSubtitleStyles = enabled) }
        mpvPlayer.setAssStyleOverride(enabled)
        applySubtitleStyle()
        debounceSaveFileSettings()
    }

    fun setSubtitleSpeed(speed: Float) {
        val normalizedSpeed = speed.coerceIn(0.25f, 4.0f)
        _uiState.update { it.copy(subtitleSpeed = normalizedSpeed) }
        mpvPlayer.setSubtitleSpeed(normalizedSpeed)
    }

    fun resetSubtitleStyle() {
        _uiState.update {
            it.copy(
                subtitleFontSize = appPreferences.subtitleFontSize,
                subtitleColor = appPreferences.subtitleColor,
                subtitleBgOpacity = appPreferences.subtitleBgOpacity,
                subtitlePosition = appPreferences.subtitlePosition,
                subtitleEdgeType = appPreferences.subtitleEdgeType,
                subtitleEdgeSize = appPreferences.subtitleEdgeSize,
                subtitleOutlineColor = appPreferences.subtitleOutlineColor,
                overrideAssSubtitleStyles = appPreferences.overrideAssSubtitleStyles
            )
        }
        mpvPlayer.setAssStyleOverride(appPreferences.overrideAssSubtitleStyles)
        applySubtitleStyle()
        debounceSaveFileSettings()
    }

    fun applySubtitleZoomCompensation(zoomLevel: Float) {
        mpvPlayer.setSubScale(baseSubtitleScale / zoomLevel.coerceAtLeast(0.1f))
    }

    private fun applySubtitleStyle() {
        val state = _uiState.value
        mpvPlayer.setSubtitleStyle(
            fontSize = state.subtitleFontSize,
            color = state.subtitleColor,
            backgroundOpacity = state.subtitleBgOpacity,
            position = state.subtitlePosition,
            edgeType = state.subtitleEdgeType,
            edgeSize = state.subtitleEdgeSize,
            outlineColor = state.subtitleOutlineColor
        )
    }

    fun setPlaybackSpeed(speed: Float) {
        mpvPlayer.setSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun setDecoderMode(mode: String) {
        val normalized = normalizeDecoderMode(mode)
        if (_uiState.value.decoderMode == normalized) return
        sessionDecoderMode = normalized
        _uiState.update {
            it.copy(
                decoderMode = normalized,
                error = null
            )
        }
        rememberPlaybackSelection()
        mpvPlayer.setDecoderMode(normalized)
    }

    fun loadExternalSubtitle(uri: Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val pathResult = runCatching { copySubtitleToCache(uri) }
            if (pathResult.isFailure) {
                val error = pathResult.exceptionOrNull()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Could not load subtitle: ${error?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }
            val path = pathResult.getOrThrow()

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                pendingExternalSubtitleTrackRefresh = true
                mpvPlayer.addExternalSubtitle(path)
                applySubtitleStyle()
                updateTrackInfo()
            }
            kotlinx.coroutines.delay(250)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                updateTrackInfo()
            }
            kotlinx.coroutines.delay(500)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                updateTrackInfo()
            }
        }
    }

    private fun copySubtitleToCache(uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.path ?: uri.toString()
        }

        val displayName = queryDisplayName(uri)
        val extension = displayName
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?: inferSubtitleExtension(uri)
        val baseName = displayName
            ?.substringBeforeLast('.', displayName)
            ?.takeIf { it.isNotBlank() }
            ?: "external_subtitle"
        val safeName = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val subtitleDir = File(context.cacheDir, "mpv_subtitles").apply { mkdirs() }
        val output = File(subtitleDir, "${System.currentTimeMillis()}_$safeName.$extension")

        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open subtitle file")
        input.use { source ->
            output.outputStream().use { sink ->
                source.copyTo(sink)
            }
        }
        return output.absolutePath
    }

    private fun inferSubtitleExtension(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)?.lowercase(Locale.US)
        return when {
            mime == null -> "srt"
            "vtt" in mime || "webvtt" in mime -> "vtt"
            "ssa" in mime -> "ssa"
            "ass" in mime -> "ass"
            "ttml" in mime || "dfxp" in mime || mime.endsWith("/xml") -> "ttml"
            else -> "srt"
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    private fun normalizeDecoderMode(mode: String): String {
        return when (mode.lowercase()) {
            "hw", "sw", "hw+", "auto" -> mode.lowercase()
            else -> "hw+"
        }
    }

    private fun decodeDecoderRouteValue(value: String?): String? {
        val raw = value?.takeIf { it.isNotBlank() } ?: return null
        return if ('%' in raw) {
            runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        } else {
            raw
        }
    }

    override fun onCleared() {
        super.onCleared()
        externalPlayerCleanupJob?.cancel()
        positionSaveJob?.cancel()
        fileSettingsSaveJob?.cancel()
        savePlaybackPosition()
        saveCurrentFileSettingsBlocking()
        mpvPlayer.destroy()
    }

    private companion object {
        private const val EXTERNAL_PLAYER_CLEANUP_DELAY_MS = 2 * 60 * 1000L
    }

    private fun startPeriodicPositionSave() {
        positionSaveJob?.cancel()
        positionSaveJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000)
                savePlaybackPosition()
            }
        }
    }

    private fun savePlaybackPosition() {
        val pos = _uiState.value.currentPosition
        val dur = _uiState.value.duration
        if (pos <= 0 || dur <= 0) return
        viewModelScope.launch {
            val fileEntity = mediaFileDao.getFileById(currentFileId)
            val thumbnailUrl = fileEntity?.thumbnailLink
            val engineName = historyPlayerEngineName()

            // Try UPDATE first (preserves savedPlayerSettings)
            playbackHistoryDao.updatePosition(
                fileId = currentFileId,
                lastPosition = pos,
                duration = dur,
                lastPlayedAt = System.currentTimeMillis(),
                posterPath = null,
                thumbnailUrl = thumbnailUrl,
                lastPlayerEngine = engineName,
                lastDecoderMode = sessionDecoderMode
            )

            // If the row doesn't exist yet, INSERT it
            val existing = playbackHistoryDao.getByFileId(currentFileId)
            if (existing == null) {
                playbackHistoryDao.upsert(
                    PlaybackHistoryEntity(
                        fileId = currentFileId,
                        fileName = currentFileName,
                        driveId = fileEntity?.driveId ?: "",
                        lastPosition = pos,
                        duration = dur,
                        lastPlayedAt = System.currentTimeMillis(),
                        thumbnailUrl = thumbnailUrl,
                        lastPlayerEngine = engineName,
                        lastDecoderMode = sessionDecoderMode
                    )
                )
            }
        }
    }

    // ──── Per-file settings persistence ────

    private var fileSettingsSaveJob: kotlinx.coroutines.Job? = null

    private fun debounceSaveFileSettings() {
        fileSettingsSaveJob?.cancel()
        fileSettingsSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2_000)
            saveCurrentFileSettings()
        }
    }

    private fun buildCurrentFileSettingsJson(): String? {
        val state = _uiState.value
        val audioTracks = state.audioTracks
        val subtitleTracks = state.subtitleTracks
        val selectedAudio = audioTracks.firstOrNull { it.isSelected }
        val selectedSubtitle = subtitleTracks.firstOrNull { it.isSelected }

        val settings = com.mkbhdana.streamhive.data.db.PerFilePlayerSettings(
            audioTrackLanguage = selectedAudio?.language,
            audioTrackLabel = selectedAudio?.name,
            subtitleTrackLanguage = selectedSubtitle?.language,
            subtitleTrackLabel = selectedSubtitle?.name,
            subtitleDelay = state.subtitleDelay.takeIf { it != 0L },
            subtitleFontSize = state.subtitleFontSize.takeIf { it != appPreferences.subtitleFontSize },
            subtitleColor = state.subtitleColor.takeIf { it != appPreferences.subtitleColor },
            subtitleBgOpacity = state.subtitleBgOpacity.takeIf { it != appPreferences.subtitleBgOpacity },
            subtitlePosition = state.subtitlePosition.takeIf { it != appPreferences.subtitlePosition },
            subtitleEdgeType = state.subtitleEdgeType.takeIf { it != appPreferences.subtitleEdgeType },
            subtitleEdgeSize = state.subtitleEdgeSize.takeIf { it != appPreferences.subtitleEdgeSize },
            subtitleOutlineColor = state.subtitleOutlineColor.takeIf { it != appPreferences.subtitleOutlineColor },
            overrideAssSubtitleStyles = state.overrideAssSubtitleStyles.takeIf { it != appPreferences.overrideAssSubtitleStyles }
        )

        return try {
            kotlinx.serialization.json.Json.encodeToString(
                com.mkbhdana.streamhive.data.db.PerFilePlayerSettings.serializer(), settings
            )
        } catch (_: Exception) { null }
    }

    private fun saveCurrentFileSettings() {
        val json = buildCurrentFileSettingsJson() ?: return
        viewModelScope.launch {
            playbackHistoryDao.updateFileSettings(currentFileId, json)
        }
    }

    private fun saveCurrentFileSettingsBlocking() {
        val json = buildCurrentFileSettingsJson() ?: return
        val fileId = currentFileId
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            playbackHistoryDao.updateFileSettings(fileId, json)
        }
    }

    private fun restorePerFileSettings(history: PlaybackHistoryEntity?) {
        val json = history?.savedPlayerSettings ?: return
        val settings = try {
            kotlinx.serialization.json.Json.decodeFromString(
                com.mkbhdana.streamhive.data.db.PerFilePlayerSettings.serializer(), json
            )
        } catch (_: Exception) { return }

        _uiState.update { state ->
            state.copy(
                subtitleDelay = settings.subtitleDelay ?: state.subtitleDelay,
                subtitleFontSize = settings.subtitleFontSize ?: state.subtitleFontSize,
                subtitleColor = settings.subtitleColor ?: state.subtitleColor,
                subtitleBgOpacity = settings.subtitleBgOpacity ?: state.subtitleBgOpacity,
                subtitlePosition = settings.subtitlePosition ?: state.subtitlePosition,
                subtitleEdgeType = settings.subtitleEdgeType ?: state.subtitleEdgeType,
                subtitleEdgeSize = settings.subtitleEdgeSize ?: state.subtitleEdgeSize,
                subtitleOutlineColor = settings.subtitleOutlineColor ?: state.subtitleOutlineColor,
                overrideAssSubtitleStyles = settings.overrideAssSubtitleStyles ?: state.overrideAssSubtitleStyles
            )
        }
        // Apply restored subtitle styles to MPV
        applySubtitleStyle()
        // Apply subtitle delay to MPV if restored
        if (settings.subtitleDelay != null && settings.subtitleDelay != 0L) {
            mpvPlayer.setSubtitleDelay(settings.subtitleDelay / 1000.0)
        }

        // Set session language overrides so track selection uses them
        if (sessionAudioLanguage == null && settings.audioTrackLanguage != null) {
            sessionAudioLanguage = settings.audioTrackLanguage
            sessionAudioLabel = settings.audioTrackLabel
        }
        if (sessionSubtitleLanguage == null && settings.subtitleTrackLanguage != null) {
            sessionSubtitleLanguage = settings.subtitleTrackLanguage
            sessionSubtitleLabel = settings.subtitleTrackLabel
        }
    }

    private fun rememberPlaybackSelection() {
        viewModelScope.launch {
            playbackHistoryDao.updatePlaybackSelection(
                fileId = currentFileId,
                playerEngine = historyPlayerEngineName(),
                decoderMode = sessionDecoderMode
            )
        }
    }

    private fun historyPlayerEngineName(): String {
        return (handoffPlayerEngine ?: PlayerEngine.MPV).name
    }
}
