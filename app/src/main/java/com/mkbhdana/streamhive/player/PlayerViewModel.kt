package com.mkbhdana.streamhive.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.mkbhdana.streamhive.navigation.PlayerRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.ui.AspectRatioFrameLayout
import com.mkbhdana.streamhive.catalog.DriveRepository
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.PlaybackHistoryDao
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
import com.mkbhdana.streamhive.data.db.PerFilePlayerSettings
import com.mkbhdana.streamhive.data.db.TmdbMetadataDao
import com.mkbhdana.streamhive.data.model.DriveFile
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.player.ui.TrackInfo
import com.mkbhdana.streamhive.player.ui.TrackType
import com.mkbhdana.streamhive.settings.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
// import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class PlayerUiState(
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false,
    val fileName: String = "",
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val error: String? = null,
    val showControls: Boolean = false,
    val bufferedPercentage: Int = 0,

    // Advanced controls
    val isLocked: Boolean = false,
    val resizeMode: String = "fit",
    val playbackSpeed: Float = 1.0f,
    val subtitleDelay: Long = 0L,
    val subtitleSpeed: Float = 1.0f,

    // Track info
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),


    // Gesture settings
    val gestureSeekEnabled: Boolean = true,
    val gestureVolumeEnabled: Boolean = true,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureDoubleTapEnabled: Boolean = true,
    val gestureZoomEnabled: Boolean = true,
    val gestureSpeedPressEnabled: Boolean = true,
    val gestureLockEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val gestureSensitivity: Float = 1.0f,

    // Subtitle Style Settings
    val subtitleFontSize: Int = 18,
    val subtitleColor: Long = 0xFFFFFFFF,
    val subtitleBgOpacity: Float = 0.0f,
    val subtitlePosition: Int = 90,
    val subtitleEdgeType: String = "outline",
    val subtitleEdgeSize: Int = 0,
    val subtitleOutlineColor: Long = 0xFF000000,
    val libassSubtitlesEnabled: Boolean = false,
    val overrideAssSubtitleStyles: Boolean = false,
    val subtitleScale: Float = 1.0f,
    val subtitleFont: String = "sans-serif",
    val subtitleBold: Boolean = false,
    val subtitleItalic: Boolean = false,
    val subtitleAlignment: String = "center",

    // Tap seek
    val tapSeekDuration: Int = 10,
    
    // Decoder
    val decoderMode: String = "auto",

    // Series episodes
    val episodeList: List<MediaFileEntity> = emptyList(),
    // The next episode after the current one (null for movies / last episode).
    val nextEpisode: MediaFileEntity? = null,
    // Set when playback finished and there is nothing left to auto-play — the
    // screen should close the player (movie ended, or last episode of a series).
    val requestClose: Boolean = false
)

@UnstableApi
@HiltViewModel(assistedFactory = PlayerViewModel.Factory::class)
class PlayerViewModel @AssistedInject constructor(
    @ApplicationContext private val context: Context,
    private val driveRepository: DriveRepository,
    private val appPreferences: AppPreferences,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val mediaFileDao: com.mkbhdana.streamhive.data.db.MediaFileDao,
    private val tmdbMetadataDao: TmdbMetadataDao,
    private val streamProxyServer: com.mkbhdana.streamhive.player.proxy.StreamProxyServer,
    @Assisted private val navKey: PlayerRoute
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(navKey: PlayerRoute): PlayerViewModel
    }

    private var currentFileId: String = navKey.fileId
    private var currentFileName: String = navKey.fileName
    private val initialDecoderMode: String = normalizeDecoderMode(
        navKey.decoder.takeIf { it.isNotBlank() }
            ?: appPreferences.exoDecoder
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
            subtitleFontSize = appPreferences.exoSubtitleFontSize,
            subtitleColor = appPreferences.exoSubtitleColor,
            subtitleBgOpacity = appPreferences.exoSubtitleBgOpacity,
            subtitlePosition = appPreferences.exoSubtitlePosition,
            subtitleEdgeType = appPreferences.exoSubtitleEdgeType,
            subtitleEdgeSize = appPreferences.exoSubtitleEdgeSize,
            subtitleOutlineColor = appPreferences.exoSubtitleOutlineColor,
            libassSubtitlesEnabled = appPreferences.libassSubtitlesEnabled,
            overrideAssSubtitleStyles = appPreferences.exoOverrideAssSubtitleStyles,
            subtitleScale = appPreferences.subtitleScale,
            subtitleFont = appPreferences.subtitleFont,
            subtitleBold = appPreferences.subtitleBold,
            subtitleItalic = appPreferences.subtitleItalic,
            subtitleAlignment = appPreferences.subtitleAlignment,
            tapSeekDuration = appPreferences.tapSeekDuration,
            decoderMode = initialDecoderMode
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private val driveDataSourceFactory = DriveDataSource.Factory { driveRepository.getValidToken() }
    private var sessionDecoderMode: String = initialDecoderMode
    private var handoffPlayerEngine: PlayerEngine? = null
    private var tmdbOriginalAudioLanguage: String? = null

    // Resume playback support
    private var pendingSeekMs: Long = 0L
    private var hasResumed: Boolean = false
    private var positionSaveJob: kotlinx.coroutines.Job? = null
    private var externalPlayerCleanupJob: kotlinx.coroutines.Job? = null
    
    // Error retry mechanism
    private var retryCount = 0
    private val MAX_RETRIES = 3
    // Retries are only forgiven after playback has stayed stable for a while;
    // resetting immediately on READY lets a recurring near-end error reload forever.
    private var retryResetJob: kotlinx.coroutines.Job? = null
    private val RETRY_RESET_AFTER_MS = 30_000L
    // Errors this close to the end are treated as end-of-playback right away.
    private val END_ON_ERROR_FRACTION = 0.97f
    // After retries are exhausted, advance to the next episode from this point on.
    private val NEAR_END_FRACTION = 0.90f
    
    // Preferred track selection should run once per media item, after tracks are known.
    private var preferredTracksApplied = false
    private var tunnelingTemporarilyDisabled = false
    private val externalSubtitleConfigurations = mutableListOf<SubtitleConfiguration>()
    private val externalSubtitleNames = mutableListOf<String>()
    private var pendingExternalSubtitleTrackSelection = false
    private var externalSubtitleCount = 0

    // Session-level track language overrides (for series track carryover)
    private var sessionAudioLanguage: String? = null
    private var sessionAudioLabel: String? = null
    private var sessionSubtitleLanguage: String? = null
    private var sessionSubtitleLabel: String? = null

    // Per-file settings save job
    private var fileSettingsSaveJob: kotlinx.coroutines.Job? = null

    init {
        rememberPlaybackSelection()
        initializePlayer()
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
                val matched = allFiles.filter {
                    !it.isFolder &&
                    extractSeriesName(it.name).equals(seriesName, ignoreCase = true)
                }
                val episodes = EpisodePlaylist.build(matched, appPreferences.sourcePriorityConfig)
                val next = EpisodePlaylist.next(episodes, currentFileId, currentFileName)
                _uiState.update { it.copy(episodeList = episodes, nextEpisode = next) }
            } catch (e: Exception) {
                Log.e("PlayerVM", "Failed to fetch episodes", e)
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
            ?: "my_drive"

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

    private fun createMediaCodecSelector(mapDv7ToHevc: Boolean): MediaCodecSelector {
        if (!mapDv7ToHevc) return MediaCodecSelector.DEFAULT

        return MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaultInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )

            if (mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
                val hevcInfos = try {
                    MediaCodecSelector.DEFAULT.getDecoderInfos(
                        MimeTypes.VIDEO_H265,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                } catch (_: Exception) {
                    emptyList()
                }
                if (hevcInfos.isEmpty()) defaultInfos else (hevcInfos + defaultInfos).distinctBy { it.name }
            } else {
                defaultInfos
            }
        }
    }

    private fun DefaultRenderersFactory.applyMapDv7ToHevcIfAvailable(enabled: Boolean): DefaultRenderersFactory {
        try {
            val method = javaClass.methods.firstOrNull { method ->
                method.name == "setMapDV7ToHevc" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == java.lang.Boolean.TYPE
            }
            method?.invoke(this, enabled)
        } catch (_: Exception) {
            Log.d("PlayerVM", "setMapDV7ToHevc is not available on this Media3 renderer factory")
        }
        return this
    }

    fun playEpisode(fileId: String, fileName: String) {
        if (fileId == currentFileId) return
        
        // Capture current track selections for series carryover
        captureCurrentTrackSelections()
        
        // Save current position before switching
        savePlaybackPosition()
        saveCurrentFileSettings()
        
        currentFileId = fileId
        currentFileName = fileName
        _uiState.update { 
            it.copy(
                fileName = fileName, 
                isLoading = true,
                isPlaying = false,
                error = null,
                showControls = false,
                bufferedPercentage = 0
            ) 
        }
        
        hasResumed = false
        pendingSeekMs = 0L
        retryCount = 0
        retryResetJob?.cancel()
        preferredTracksApplied = false
        externalSubtitleConfigurations.clear()
        externalSubtitleNames.clear()
        pendingExternalSubtitleTrackSelection = false
        externalSubtitleCount = 0
        rememberPlaybackSelection()
        fetchEpisodeList()
        
        // Re-initialize player
        _player?.release()
        _player = null
        initializePlayer()
    }

    /** How far through the current item playback is (0..1), best-effort during errors. */
    private fun currentProgressFraction(): Float {
        val duration = _player?.duration?.takeIf { it > 0 } ?: _uiState.value.duration
        if (duration <= 0) return 0f
        val position = maxOf(
            _player?.currentPosition ?: 0L,
            _uiState.value.currentPosition,
            pendingSeekMs
        )
        return (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Called when the current item finishes. For a series, auto-advance to the next
     * episode; for a movie / final episode, ask the screen to close the player.
     */
    private fun onPlaybackEnded() {
        val next = _uiState.value.nextEpisode
        if (next != null) {
            // playEpisode() saves the current (finished) position on its own.
            playEpisode(next.id, next.name)
        } else {
            savePlaybackPosition()
            _uiState.update { it.copy(requestClose = true) }
        }
    }

    /** The screen calls this after it has handled [PlayerUiState.requestClose]. */
    fun consumeCloseRequest() {
        _uiState.update { it.copy(requestClose = false) }
    }

    /**
     * Capture the currently selected audio/subtitle track language and label
     * so they can be carried over to the next episode in a series.
     */
    private fun captureCurrentTrackSelections() {
        val audioTracks = _uiState.value.audioTracks
        val subtitleTracks = _uiState.value.subtitleTracks
        
        val selectedAudio = audioTracks.firstOrNull { it.isSelected }
        if (selectedAudio != null) {
            sessionAudioLanguage = selectedAudio.language
            sessionAudioLabel = selectedAudio.name
        }
        
        val selectedSubtitle = subtitleTracks.firstOrNull { it.isSelected }
        if (selectedSubtitle != null) {
            sessionSubtitleLanguage = selectedSubtitle.language
            sessionSubtitleLabel = selectedSubtitle.name
        } else {
            // No subtitle selected — remember that choice too
            sessionSubtitleLanguage = "__none__"
            sessionSubtitleLabel = null
        }
    }

    private fun initializePlayer() {
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

                val token = driveRepository.getValidToken()
                    ?: throw Exception("Not authenticated")

                driveDataSourceFactory.updateToken(token)
                tmdbOriginalAudioLanguage = resolveTmdbOriginalLanguage()

                val decoderMode = when (sessionDecoderMode) {
                    "hw" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    "sw" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                }
                val useTunneling =
                    appPreferences.tunneledPlaybackEnabled &&
                        !tunnelingTemporarilyDisabled &&
                        sessionDecoderMode == "hw"

                val trackSelector = DefaultTrackSelector(context).apply {
                    setParameters(
                        buildUponParameters()
                            .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                            .setTunnelingEnabled(useTunneling)
                            .setTrackTypeDisabled(
                                C.TRACK_TYPE_TEXT,
                                appPreferences.preferredSubtitleLanguage == "none"
                            )
                    )
                }

                val renderersFactory = DefaultRenderersFactory(context)
                    .setExtensionRendererMode(decoderMode)
                    .setMediaCodecSelector(createMediaCodecSelector(appPreferences.mapDv7ToHevc))
                    .applyMapDv7ToHevcIfAvailable(appPreferences.mapDv7ToHevc)

                val loadControl = DefaultLoadControl.Builder()
                    .setTargetBufferBytes(100 * 1024 * 1024)
                    .setBufferDurationsMs(
                        5_000,
                        90_000,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        5_000
                    )
                    .build()

                val extractorsFactory = DefaultExtractorsFactory()
                    .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                    .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

                val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

                val exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()
                    .apply {
                        val streamUrl = streamProxyServer.getStreamUrl(currentFileId)
                        val mediaItem = buildMediaItem(streamUrl)

                        setMediaItem(mediaItem)
                        
                        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build()
                        setAudioAttributes(audioAttributes, true)
                        
                        playWhenReady = true
                        prepare()
                    }

                // Load resume position (deferred until STATE_READY)
                val history = playbackHistoryDao.getByFileId(currentFileId)
                if (history != null && history.isResumeEligible && history.lastPosition > 0) {
                    pendingSeekMs = history.lastPosition
                }
                // Restore per-file settings (subtitle style, delay, track preferences)
                restorePerFileSettings(history)

                exoPlayer.addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        updateTrackInfo()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _uiState.update {
                            it.copy(
                                isLoading = playbackState == Player.STATE_BUFFERING,
                                isPlaying = exoPlayer.isPlaying,
                                duration = exoPlayer.duration.coerceAtLeast(0),
                                currentPosition = exoPlayer.currentPosition
                            )
                        }
                        if (playbackState == Player.STATE_READY) {
                            retryResetJob?.cancel()
                            retryResetJob = viewModelScope.launch {
                                kotlinx.coroutines.delay(RETRY_RESET_AFTER_MS)
                                retryCount = 0
                            }
                            // Resume to saved position once player is ready
                            if (!hasResumed && pendingSeekMs > 0) {
                                exoPlayer.seekTo(pendingSeekMs)
                                hasResumed = true
                                pendingSeekMs = 0L
                            }
                            updateTrackInfo()

                        } else if (playbackState == Player.STATE_ENDED) {
                            onPlaybackEnded()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.update { it.copy(isPlaying = isPlaying) }
                        // Start/stop periodic position saving
                        if (isPlaying) {
                            // Save immediately so continue playing shows up right away
                            savePlaybackPosition()
                            startPeriodicPositionSave()
                        } else {
                            positionSaveJob?.cancel()
                            savePlaybackPosition()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        retryResetJob?.cancel()
                        // An error in the final stretch is treated as "finished" instead of
                        // reloading: advance to the next episode / close the player.
                        if (currentProgressFraction() >= END_ON_ERROR_FRACTION) {
                            Log.w("PlayerVM", "Playback error near end of video — treating as finished", error)
                            onPlaybackEnded()
                            return
                        }
                        if (useTunneling && !tunnelingTemporarilyDisabled) {
                            tunnelingTemporarilyDisabled = true
                            val pos = _player?.currentPosition ?: 0L
                            if (pos > 0) pendingSeekMs = pos
                            hasResumed = false
                            Log.w("PlayerVM", "Tunneled playback failed. Retrying without tunneling.", error)
                            _uiState.update {
                                it.copy(
                                    isLoading = true,
                                    isPlaying = false,
                                    error = null,
                                    showControls = false
                                )
                            }
                            _player?.release()
                            _player = null
                            initializePlayer()
                            return
                        }

                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            val pos = _player?.currentPosition ?: 0L
                            if (pos > 0) pendingSeekMs = pos
                            hasResumed = false
                            Log.w("PlayerVM", "Playback error. Retrying ($retryCount/$MAX_RETRIES)", error)
                            _uiState.update {
                                it.copy(
                                    isLoading = true,
                                    isPlaying = false,
                                    error = null,
                                    showControls = false
                                )
                            }
                            _player?.prepare()
                            _player?.playWhenReady = true
                        } else if (_uiState.value.nextEpisode != null &&
                            currentProgressFraction() >= NEAR_END_FRACTION
                        ) {
                            // Retries exhausted with the episode almost over — move on to the
                            // next episode rather than looping reloads / showing an error.
                            Log.w("PlayerVM", "Retries exhausted near end of episode — advancing", error)
                            onPlaybackEnded()
                        } else {
                            _uiState.update {
                                it.copy(
                                    error = buildPlaybackErrorMessage(error),
                                    isLoading = false,
                                    isPlaying = false,
                                    showControls = false
                                )
                            }
                        }
                    }

                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                        super.onTimelineChanged(timeline, reason)

                    }
                })

                _player = exoPlayer
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        isPlaying = true,
                        error = null,
                        showControls = false
                    )
                }
                // isLoading stays true until onPlaybackStateChanged fires STATE_READY

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = false,
                        showControls = false,
                        error = "Failed to initialize player: ${e.message}"
                    )
                }
            }
        }
    }

    private fun buildPlaybackErrorMessage(error: PlaybackException): String {
        val detail = error.message
            ?: error.cause?.message
            ?: error.errorCodeName
            ?: error.toString()

        if (isUnsupportedHardwareVideoFormat(detail)) {
            return "This video profile is not supported by the device hardware decoder. Try MPV, External player, or a different release encoded as 8-bit H.264/HEVC."
        }

        return if (retryCount >= MAX_RETRIES) {
            "$detail (failed after $MAX_RETRIES retries)"
        } else {
            detail
        }
    }

    private fun isUnsupportedHardwareVideoFormat(detail: String): Boolean {
        val lower = detail.lowercase(Locale.US)
        return "mediacodecvideorenderer" in lower &&
            (
                "no_exceeds_capabilities" in lower ||
                    "exceeds_capabilities" in lower ||
                    "decoder init failed" in lower ||
                    "format_supported=no" in lower
            )
    }

    private fun updateTrackInfo() {
        val player = _player ?: return
        val tracks = player.currentTracks

        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()

        var selectedAudioLang: String? = null

        tracks.groups.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val isSelected = group.isTrackSelected(trackIndex)

                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> {
                        if (isSelected && format.language != null) {
                            selectedAudioLang = format.language
                        }
                        audioTracks.add(
                            TrackInfo(
                                index = groupIndex,
                                trackIndex = trackIndex,
                                type = TrackType.AUDIO,
                                name = format.label ?: "Track ${audioTracks.size + 1}",
                                language = format.language,
                                codec = format.codecs,
                                isSelected = isSelected
                            )
                        )
                    }
                    C.TRACK_TYPE_TEXT -> {
                        val name = format.label ?: "Subtitle ${subtitleTracks.size + 1}"
                        val externalName = externalSubtitleNames.firstOrNull { it == name }
                        val isExternal = externalName != null
                        subtitleTracks.add(
                            TrackInfo(
                                index = groupIndex,
                                trackIndex = trackIndex,
                                type = TrackType.SUBTITLE,
                                name = name,
                                language = format.language,
                                codec = format.codecs,
                                isSelected = isSelected,
                                isExternal = isExternal,
                                canRemove = isExternal,
                                sourceId = externalName
                            )
                        )
                    }
                }
            }
        }

        if (pendingExternalSubtitleTrackSelection && subtitleTracks.isEmpty()) {
            _uiState.update {
                it.copy(
                    audioTracks = audioTracks.ifEmpty { it.audioTracks },
                    subtitleTracks = it.subtitleTracks
                )
            }
            return
        }

        maybeApplyPreferredTracks(player, audioTracks, subtitleTracks, selectedAudioLang)

        val newlyAddedSubtitle = if (pendingExternalSubtitleTrackSelection && subtitleTracks.isNotEmpty()) {
            subtitleTracks.lastOrNull { it.isExternal } ?: subtitleTracks.last()
        } else {
            null
        }
        if (newlyAddedSubtitle != null) {
            pendingExternalSubtitleTrackSelection = false
            preferredTracksApplied = true
            applySubtitleTrackOverride(newlyAddedSubtitle.index, newlyAddedSubtitle.trackIndex)
        }

        _uiState.update {
            it.copy(
                audioTracks = audioTracks,
                subtitleTracks = if (newlyAddedSubtitle == null) {
                    subtitleTracks
                } else {
                    subtitleTracks.map { track ->
                        track.copy(
                            isSelected = track.index == newlyAddedSubtitle.index &&
                                track.trackIndex == newlyAddedSubtitle.trackIndex
                        )
                    }
                }
            )
        }
    }

    private fun maybeApplyPreferredTracks(
        player: ExoPlayer,
        audioTracks: List<TrackInfo>,
        subtitleTracks: List<TrackInfo>,
        selectedAudioLang: String?
    ) {
        if (preferredTracksApplied || (audioTracks.isEmpty() && subtitleTracks.isEmpty())) return

        val preferredAudioLanguage = sessionAudioLanguage ?: appPreferences.preferredAudioLanguage
        val preferredSubtitleLanguage = sessionSubtitleLanguage ?: appPreferences.preferredSubtitleLanguage
        val targetAudioLanguage = if (preferredAudioLanguage == "original") {
            tmdbOriginalAudioLanguage
        } else {
            preferredAudioLanguage
        }

        val preferredAudioTrack = if (!targetAudioLanguage.isNullOrBlank()) {
            // If we have a session label (from series carryover), prefer label match
            val labelMatch = if (sessionAudioLabel != null) {
                audioTracks.firstOrNull { it.name == sessionAudioLabel && languageMatches(it.language, targetAudioLanguage) }
            } else null
            labelMatch ?: audioTracks.firstOrNull { languageMatches(it.language, targetAudioLanguage) }
        } else {
            null
        }

        val effectiveAudioLanguage = preferredAudioTrack?.language ?: selectedAudioLang
        if (preferredAudioTrack != null && !preferredAudioTrack.isSelected) {
            applyAudioTrackOverride(preferredAudioTrack.index, preferredAudioTrack.trackIndex)
        }

        val shouldDisableSubtitles =
            preferredSubtitleLanguage == "none" ||
                preferredSubtitleLanguage == "__none__" ||
                isLanguageExcluded(effectiveAudioLanguage, appPreferences.subtitleExcludeLanguages)

        if (shouldDisableSubtitles) {
            if (subtitleTracks.any { it.isSelected } || preferredSubtitleLanguage == "none") {
                applySubtitleTrackOverride(-1, 0)
            }
        } else {
            val labelMatch = if (sessionSubtitleLabel != null) {
                subtitleTracks.firstOrNull { it.name == sessionSubtitleLabel && languageMatches(it.language, preferredSubtitleLanguage) }
            } else null
            val preferredSubtitleTrack = labelMatch ?: subtitleTracks.firstOrNull {
                languageMatches(it.language, preferredSubtitleLanguage)
            }
            if (preferredSubtitleTrack != null && !preferredSubtitleTrack.isSelected) {
                applySubtitleTrackOverride(preferredSubtitleTrack.index, preferredSubtitleTrack.trackIndex)
            }
        }

        val hasTextGroups = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
        val audioResolved = targetAudioLanguage.isNullOrBlank() || audioTracks.isNotEmpty()
        val subtitlesResolved =
            preferredSubtitleLanguage == "none" || subtitleTracks.isNotEmpty() || !hasTextGroups

        if (audioResolved && subtitlesResolved) {
            preferredTracksApplied = true
        }
    }

    private fun languageMatches(trackLanguage: String?, preferredLanguage: String): Boolean {
        if (trackLanguage.isNullOrBlank() || preferredLanguage.isBlank()) return false
        return languageAliases(trackLanguage).any { it in languageAliases(preferredLanguage) }
    }

    private fun isLanguageExcluded(trackLanguage: String?, excludedLanguages: Set<String>): Boolean {
        if (trackLanguage.isNullOrBlank() || excludedLanguages.isEmpty()) return false
        val trackAliases = languageAliases(trackLanguage)
        return excludedLanguages.any { excluded ->
            languageAliases(excluded).any { it in trackAliases }
        }
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


    fun togglePlayPause() {
        _player?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = _player ?: return
        val durationMs = player.duration
            .takeIf { it > 0L }
            ?: _uiState.value.duration
                .takeIf { it > 0L }
        val targetPosition = if (durationMs != null) {
            positionMs.coerceIn(0L, durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        player.seekTo(targetPosition)
        _uiState.update {
            it.copy(
                currentPosition = targetPosition,
                bufferedPercentage = player.bufferedPercentage,
                duration = durationMs ?: it.duration
            )
        }
    }

    fun seekForward(ms: Long = 10_000) {
        _player?.let { player ->
            val durationMs = player.duration
                .takeIf { it > 0L }
                ?: _uiState.value.duration
                    .takeIf { it > 0L }
            val targetPosition = player.currentPosition + ms
            seekTo(durationMs?.let { targetPosition.coerceAtMost(it) } ?: targetPosition)
        }
    }

    fun seekBackward(ms: Long = 10_000) {
        _player?.let { player ->
            seekTo((player.currentPosition - ms).coerceAtLeast(0L))
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }
    
    fun setDecoderMode(mode: String) {
        val normalized = normalizeDecoderMode(mode)
        if (_uiState.value.decoderMode == normalized) return
        sessionDecoderMode = normalized
        _uiState.update {
            it.copy(
                decoderMode = normalized,
                isLoading = true,
                isPlaying = false,
                error = null,
                showControls = false
            )
        }
        
        // Save current position and release old player synchronously
        val currentPos = _player?.currentPosition ?: 0L
        _player?.release()
        _player = null
        
        pendingSeekMs = currentPos
        hasResumed = false
        preferredTracksApplied = false
        
        rememberPlaybackSelection()
        initializePlayer()
    }

    fun showControls() {
        _uiState.update { it.copy(showControls = true) }
    }

    fun hideControls() {
        _uiState.update { it.copy(showControls = false) }
    }

    fun getProxyUrl(): String? = streamProxyServer.getStreamUrl(currentFileId)

    fun isMpvAvailable(): Boolean = appPreferences.isMpvAvailable()

    fun scheduleExternalPlayerCleanup() {
        externalPlayerCleanupJob?.cancel()
        externalPlayerCleanupJob = viewModelScope.launch {
            kotlinx.coroutines.delay(EXTERNAL_PLAYER_CLEANUP_DELAY_MS)
            savePlaybackPosition()
            positionSaveJob?.cancel()
            _player?.release()
            _player = null
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

    fun prepareForEngineFallback(targetEngine: PlayerEngine? = null) {
        handoffPlayerEngine = targetEngine
        _player?.pause()
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

    fun updatePosition() {
        _player?.let { player ->
            _uiState.update {
                it.copy(
                    currentPosition = player.currentPosition,
                    bufferedPercentage = player.bufferedPercentage,
                    duration = player.duration.coerceAtLeast(0)
                )
            }
        }
    }

    // â”€â”€â”€â”€ Advanced controls â”€â”€â”€â”€

    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun setResizeMode(mode: String) {
        _uiState.update { it.copy(resizeMode = mode) }
    }

    fun getAspectRatioResizeMode(): Int {
        return when (_uiState.value.resizeMode) {
            "fit" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "16:9" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            "4:3" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int = 0) {
        preferredTracksApplied = true
        applyAudioTrackOverride(groupIndex, trackIndex)
        updateTrackInfo()
        // Capture language for session carryover and trigger per-file save
        val player = _player ?: return
        val group = player.currentTracks.groups.getOrNull(groupIndex)
        if (group != null && group.type == C.TRACK_TYPE_AUDIO) {
            val format = group.getTrackFormat(trackIndex)
            sessionAudioLanguage = format.language
            sessionAudioLabel = format.label
        }
        debounceSaveFileSettings()
    }

    private fun applyAudioTrackOverride(groupIndex: Int, trackIndex: Int) {
        val player = _player ?: return
        if (groupIndex < 0) return

        val tracks = player.currentTracks
        val group = tracks.groups.getOrNull(groupIndex) ?: return

        if (group.type == C.TRACK_TYPE_AUDIO) {
            val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(override)
                .build()
        }
    }

    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int = 0) {
        preferredTracksApplied = true
        applySubtitleTrackOverride(groupIndex, trackIndex)
        updateTrackInfo()
        // Capture language for session carryover and trigger per-file save
        if (groupIndex < 0) {
            sessionSubtitleLanguage = "__none__"
            sessionSubtitleLabel = null
        } else {
            val player = _player ?: return
            val group = player.currentTracks.groups.getOrNull(groupIndex)
            if (group != null && group.type == C.TRACK_TYPE_TEXT) {
                val format = group.getTrackFormat(trackIndex)
                sessionSubtitleLanguage = format.language
                sessionSubtitleLabel = format.label
            }
        }
        debounceSaveFileSettings()
    }

    private fun applySubtitleTrackOverride(groupIndex: Int, trackIndex: Int) {
        val player = _player ?: return

        if (groupIndex < 0) {
            // Disable subtitles
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            val tracks = player.currentTracks
            val group = tracks.groups.getOrNull(groupIndex) ?: return

            if (group.type == C.TRACK_TYPE_TEXT) {
                val override = TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .addOverride(override)
                    .build()
            }
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        _uiState.update { it.copy(subtitleDelay = delayMs) }
        debounceSaveFileSettings()
    }

    fun setSubtitleFontSize(fontSize: Int) {
        _uiState.update { it.copy(subtitleFontSize = fontSize.coerceIn(10, 48)) }
        debounceSaveFileSettings()
    }

    fun setSubtitleColor(color: Long) {
        _uiState.update { it.copy(subtitleColor = color) }
        debounceSaveFileSettings()
    }

    fun setSubtitlePosition(position: Int) {
        _uiState.update { it.copy(subtitlePosition = position.coerceIn(0, 100)) }
        debounceSaveFileSettings()
    }

    fun setSubtitleOutlineColor(color: Long) {
        _uiState.update { it.copy(subtitleOutlineColor = color) }
        debounceSaveFileSettings()
    }

    fun setSubtitleBgOpacity(opacity: Float) {
        _uiState.update { it.copy(subtitleBgOpacity = opacity.coerceIn(0f, 1f)) }
        debounceSaveFileSettings()
    }

    fun setSubtitleEdgeSize(edgeSize: Int) {
        _uiState.update { it.copy(subtitleEdgeSize = edgeSize.coerceIn(0, 20)) }
        debounceSaveFileSettings()
    }

    fun setOverrideAssSubtitleStyles(enabled: Boolean) {
        _uiState.update { it.copy(overrideAssSubtitleStyles = enabled) }
        debounceSaveFileSettings()
    }

    fun setSubtitleSpeed(speed: Float) {
        _uiState.update { it.copy(subtitleSpeed = speed.coerceIn(0.25f, 4.0f)) }
    }

    fun setSubtitleScale(scale: Float) {
        _uiState.update { it.copy(subtitleScale = scale.coerceIn(0.5f, 3.0f)) }
        debounceSaveFileSettings()
    }

    fun setSubtitleFont(font: String) {
        _uiState.update { it.copy(subtitleFont = font) }
        debounceSaveFileSettings()
    }

    fun setSubtitleBold(bold: Boolean) {
        _uiState.update { it.copy(subtitleBold = bold) }
        debounceSaveFileSettings()
    }

    fun setSubtitleItalic(italic: Boolean) {
        _uiState.update { it.copy(subtitleItalic = italic) }
        debounceSaveFileSettings()
    }

    fun setSubtitleAlignment(alignment: String) {
        _uiState.update { it.copy(subtitleAlignment = alignment) }
        debounceSaveFileSettings()
    }

    fun resetSubtitleStyle() {
        _uiState.update {
            it.copy(
                subtitleFontSize = appPreferences.exoSubtitleFontSize,
                subtitleColor = appPreferences.exoSubtitleColor,
                subtitleBgOpacity = appPreferences.exoSubtitleBgOpacity,
                subtitlePosition = appPreferences.exoSubtitlePosition,
                subtitleEdgeType = appPreferences.exoSubtitleEdgeType,
                subtitleEdgeSize = appPreferences.exoSubtitleEdgeSize,
                subtitleOutlineColor = appPreferences.exoSubtitleOutlineColor,
                overrideAssSubtitleStyles = appPreferences.exoOverrideAssSubtitleStyles,
                subtitleScale = appPreferences.subtitleScale,
                subtitleFont = appPreferences.subtitleFont,
                subtitleBold = appPreferences.subtitleBold,
                subtitleItalic = appPreferences.subtitleItalic,
                subtitleAlignment = appPreferences.subtitleAlignment
            )
        }
        debounceSaveFileSettings()
    }

    fun setPlaybackSpeed(speed: Float) {
        _player?.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun loadExternalSubtitle(uri: Uri) {
        val player = _player ?: return

        val displayName = queryDisplayName(uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "External Subtitle ${externalSubtitleCount + 1}"
        externalSubtitleCount += 1
        val subtitleConfig = SubtitleConfiguration.Builder(uri)
            .setMimeType(inferSubtitleMimeType(uri))
            .setLabel(displayName)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        externalSubtitleConfigurations.add(subtitleConfig)
        externalSubtitleNames.add(displayName)

        val currentPosition = player.currentPosition
        val wasPlaying = player.playWhenReady
        pendingExternalSubtitleTrackSelection = true
        preferredTracksApplied = true
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        player.setMediaItem(buildMediaItem(streamProxyServer.getStreamUrl(currentFileId)))
        player.seekTo(currentPosition)
        player.playWhenReady = wasPlaying
        player.prepare()
    }

    fun removeExternalSubtitle(track: TrackInfo) {
        val subtitleName = track.sourceId ?: track.name
        val removeIndex = externalSubtitleNames.indexOf(subtitleName)
        if (removeIndex !in externalSubtitleConfigurations.indices) return

        externalSubtitleNames.removeAt(removeIndex)
        externalSubtitleConfigurations.removeAt(removeIndex)
        pendingExternalSubtitleTrackSelection = false
        preferredTracksApplied = true

        val player = _player ?: return
        val currentPosition = player.currentPosition
        val wasPlaying = player.playWhenReady
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, externalSubtitleConfigurations.isEmpty() && _uiState.value.subtitleTracks.none { !it.isExternal })
            .build()
        player.setMediaItem(buildMediaItem(streamProxyServer.getStreamUrl(currentFileId)))
        player.seekTo(currentPosition)
        player.playWhenReady = wasPlaying
        player.prepare()
    }

    private fun buildMediaItem(uri: String): MediaItem {
        return MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(externalSubtitleConfigurations.toList())
            .build()
    }

    private fun inferSubtitleMimeType(uri: Uri): String {
        val resolverMime = context.contentResolver.getType(uri)?.lowercase(Locale.US)
        if (resolverMime != null) {
            when {
                "vtt" in resolverMime || "webvtt" in resolverMime -> return MimeTypes.TEXT_VTT
                "ssa" in resolverMime || "ass" in resolverMime -> return MimeTypes.TEXT_SSA
                "ttml" in resolverMime || "dfxp" in resolverMime || resolverMime.endsWith("/xml") -> return MimeTypes.APPLICATION_TTML
                "subrip" in resolverMime || "srt" in resolverMime -> return MimeTypes.APPLICATION_SUBRIP
            }
        }

        val fileName = queryDisplayName(uri) ?: uri.lastPathSegment.orEmpty()
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
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

    /**
     * Explicitly release the player — called from UI on back navigation.
     * This ensures the player is destroyed immediately rather than relying
     * on ViewModel.onCleared() which Navigation 3 may not trigger promptly.
     */
    fun releasePlayer() {
        if (!playerReleased) {
            playerReleased = true
            externalPlayerCleanupJob?.cancel()
            positionSaveJob?.cancel()
            fileSettingsSaveJob?.cancel()
            _player?.pause()
            savePlaybackPosition()
            saveCurrentFileSettingsBlocking()
            _player?.release()
            _player = null
        }
    }

    private var playerReleased = false

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
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
        val player = _player ?: return
        val pos = player.currentPosition
        val dur = player.duration.coerceAtLeast(0)
        if (pos <= 0 || dur <= 0) return
        viewModelScope.launch {
            val fileEntity = mediaFileDao.getFileById(currentFileId)
            val metadata = tmdbMetadataDao.getByDriveFileId(currentFileId)
            val posterPath = metadata?.posterPath
            val thumbnailUrl = fileEntity?.thumbnailLink
            val engineName = historyPlayerEngineName()

            // Try UPDATE first (preserves savedPlayerSettings)
            playbackHistoryDao.updatePosition(
                fileId = currentFileId,
                lastPosition = pos,
                duration = dur,
                lastPlayedAt = System.currentTimeMillis(),
                posterPath = posterPath,
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
                        posterPath = posterPath,
                        thumbnailUrl = thumbnailUrl,
                        lastPlayerEngine = engineName,
                        lastDecoderMode = sessionDecoderMode
                    )
                )
            }
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
        return (handoffPlayerEngine ?: PlayerEngine.EXO_PLAYER).name
    }

    // ──── Per-file settings persistence ────

    private fun debounceSaveFileSettings() {
        fileSettingsSaveJob?.cancel()
        fileSettingsSaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2_000) // debounce 2s
            saveCurrentFileSettings()
        }
    }

    private fun buildCurrentFileSettingsJson(): String? {
        val state = _uiState.value
        val audioTracks = state.audioTracks
        val subtitleTracks = state.subtitleTracks
        val selectedAudio = audioTracks.firstOrNull { it.isSelected }
        val selectedSubtitle = subtitleTracks.firstOrNull { it.isSelected }

        val settings = PerFilePlayerSettings(
            audioTrackLanguage = selectedAudio?.language,
            audioTrackLabel = selectedAudio?.name,
            subtitleTrackLanguage = selectedSubtitle?.language,
            subtitleTrackLabel = selectedSubtitle?.name,
            subtitleDelay = state.subtitleDelay.takeIf { it != 0L },
            subtitleFontSize = state.subtitleFontSize.takeIf { it != appPreferences.exoSubtitleFontSize },
            subtitleColor = state.subtitleColor.takeIf { it != appPreferences.exoSubtitleColor },
            subtitleBgOpacity = state.subtitleBgOpacity.takeIf { it != appPreferences.exoSubtitleBgOpacity },
            subtitlePosition = state.subtitlePosition.takeIf { it != appPreferences.exoSubtitlePosition },
            subtitleEdgeType = state.subtitleEdgeType.takeIf { it != appPreferences.exoSubtitleEdgeType },
            subtitleEdgeSize = state.subtitleEdgeSize.takeIf { it != appPreferences.exoSubtitleEdgeSize },
            subtitleOutlineColor = state.subtitleOutlineColor.takeIf { it != appPreferences.exoSubtitleOutlineColor },
            overrideAssSubtitleStyles = state.overrideAssSubtitleStyles.takeIf { it != appPreferences.exoOverrideAssSubtitleStyles },
            subtitleScale = state.subtitleScale.takeIf { it != appPreferences.subtitleScale },
            subtitleFont = state.subtitleFont.takeIf { it != appPreferences.subtitleFont },
            subtitleBold = state.subtitleBold.takeIf { it != appPreferences.subtitleBold },
            subtitleItalic = state.subtitleItalic.takeIf { it != appPreferences.subtitleItalic },
            subtitleAlignment = state.subtitleAlignment.takeIf { it != appPreferences.subtitleAlignment }
        )

        return try {
            kotlinx.serialization.json.Json.encodeToString(PerFilePlayerSettings.serializer(), settings)
        } catch (_: Exception) { null }
    }

    private fun saveCurrentFileSettings() {
        val json = buildCurrentFileSettingsJson() ?: return
        viewModelScope.launch {
            playbackHistoryDao.updateFileSettings(currentFileId, json)
        }
    }

    /** Blocking variant for onCleared() — viewModelScope is about to be cancelled */
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
            kotlinx.serialization.json.Json.decodeFromString(PerFilePlayerSettings.serializer(), json)
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
                overrideAssSubtitleStyles = settings.overrideAssSubtitleStyles ?: state.overrideAssSubtitleStyles,
                subtitleScale = settings.subtitleScale ?: state.subtitleScale,
                subtitleFont = settings.subtitleFont ?: state.subtitleFont,
                subtitleBold = settings.subtitleBold ?: state.subtitleBold,
                subtitleItalic = settings.subtitleItalic ?: state.subtitleItalic,
                subtitleAlignment = settings.subtitleAlignment ?: state.subtitleAlignment
            )
        }

        // Set session language overrides from saved settings so track selection uses them
        if (sessionAudioLanguage == null && settings.audioTrackLanguage != null) {
            sessionAudioLanguage = settings.audioTrackLanguage
            sessionAudioLabel = settings.audioTrackLabel
        }
        if (sessionSubtitleLanguage == null && settings.subtitleTrackLanguage != null) {
            sessionSubtitleLanguage = settings.subtitleTrackLanguage
            sessionSubtitleLabel = settings.subtitleTrackLabel
        }
    }

    private fun normalizeDecoderMode(mode: String): String {
        return when (mode.lowercase(Locale.US)) {
            "hw", "sw", "hw+", "auto" -> mode.lowercase(Locale.US)
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
}
