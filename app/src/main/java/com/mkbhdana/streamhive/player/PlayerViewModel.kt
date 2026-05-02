package com.mkbhdana.streamhive.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
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
import com.mkbhdana.streamhive.data.db.PlaybackHistoryDao
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataDao
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

    // Track info
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),

    // Chapter info
    val chapters: List<com.mkbhdana.streamhive.player.ui.ChapterInfo> = emptyList(),

    // Gesture settings
    val gestureSeekEnabled: Boolean = true,
    val gestureVolumeEnabled: Boolean = true,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureDoubleTapEnabled: Boolean = true,
    val gestureZoomEnabled: Boolean = true,

    // Subtitle Style Settings
    val subtitleFontSize: Int = 18,
    val subtitleColor: Long = 0xFFFFFFFF,
    val subtitleBgOpacity: Float = 0.0f,
    val subtitlePosition: Int = 90,
    val subtitleEdgeType: String = "outline",
    val subtitleEdgeSize: Int = 0,
    val subtitleOutlineColor: Long = 0xFF000000,

    // Tap seek
    val tapSeekDuration: Int = 10,
    
    // Decoder
    val decoderMode: String = "auto",

    // Series episodes
    val episodeList: List<com.mkbhdana.streamhive.data.db.MediaFileEntity> = emptyList()
)

@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
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

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            fileName = currentFileName,
            resizeMode = appPreferences.defaultResizeMode,
            gestureSeekEnabled = appPreferences.gestureSeekEnabled,
            gestureVolumeEnabled = appPreferences.gestureVolumeEnabled,
            gestureBrightnessEnabled = appPreferences.gestureBrightnessEnabled,
            gestureDoubleTapEnabled = appPreferences.gestureDoubleTapEnabled,
            gestureZoomEnabled = appPreferences.gestureZoomEnabled,
            subtitleFontSize = appPreferences.subtitleFontSize,
            subtitleColor = appPreferences.subtitleColor,
            subtitleBgOpacity = appPreferences.subtitleBgOpacity,
            subtitlePosition = appPreferences.subtitlePosition,
            subtitleEdgeType = appPreferences.subtitleEdgeType,
            subtitleEdgeSize = appPreferences.subtitleEdgeSize,
            subtitleOutlineColor = appPreferences.subtitleOutlineColor,
            tapSeekDuration = appPreferences.tapSeekDuration,
            decoderMode = appPreferences.defaultDecoder
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private val driveDataSourceFactory = DriveDataSource.Factory { driveRepository.getValidToken() }
    private var sessionDecoderMode: String = appPreferences.defaultDecoder

    // Resume playback support
    private var pendingSeekMs: Long = 0L
    private var hasResumed: Boolean = false
    private var positionSaveJob: kotlinx.coroutines.Job? = null
    
    // Error retry mechanism
    private var retryCount = 0
    private val MAX_RETRIES = 3
    
    // Preferred track selection should run once per media item, after tracks are known.
    private var preferredTracksApplied = false
    private var tunnelingTemporarilyDisabled = false

    init {
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
                val currentFile = mediaFileDao.getFileById(currentFileId) ?: return@launch
                val allFiles = mediaFileDao.getFilesByFolderSync(currentFile.driveId, currentFile.parentId)
                val seriesName = extractSeriesName(currentFileName)
                val episodes = allFiles.filter { 
                    !it.isFolder && 
                    extractSeriesName(it.name).equals(seriesName, ignoreCase = true) 
                }
                _uiState.update { it.copy(episodeList = episodes) }
            } catch (e: Exception) {
                Log.e("PlayerVM", "Failed to fetch episodes", e)
            }
        }
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
        
        // Save current position before switching
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
                bufferedPercentage = 0,
                chapters = emptyList() // clear chapters for new file
            ) 
        }
        
        hasResumed = false
        pendingSeekMs = 0L
        preferredTracksApplied = false
        
        // Re-initialize player
        _player?.release()
        _player = null
        initializePlayer()
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
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        70_000,
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
                        Log.d("ExoPlayer", "Stream URL: $streamUrl")
                        val mediaItem = MediaItem.fromUri(streamUrl)

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
                if (history != null && !history.isCompleted && history.lastPosition > 0) {
                    pendingSeekMs = history.lastPosition
                }

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
                            retryCount = 0 // Reset retries on successful playback
                            // Resume to saved position once player is ready
                            if (!hasResumed && pendingSeekMs > 0) {
                                exoPlayer.seekTo(pendingSeekMs)
                                hasResumed = true
                                pendingSeekMs = 0L
                            }
                            updateTrackInfo()
                            extractChapters()
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
                        extractChapters()
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

        return if (retryCount >= MAX_RETRIES) {
            "$detail (failed after $MAX_RETRIES retries)"
        } else {
            detail
        }
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
                        subtitleTracks.add(
                            TrackInfo(
                                index = groupIndex,
                                trackIndex = trackIndex,
                                type = TrackType.SUBTITLE,
                                name = format.label ?: "Subtitle ${subtitleTracks.size + 1}",
                                language = format.language,
                                codec = format.codecs,
                                isSelected = isSelected
                            )
                        )
                    }
                }
            }
        }

        maybeApplyPreferredTracks(player, audioTracks, subtitleTracks, selectedAudioLang)

        _uiState.update {
            it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks)
        }
    }

    private fun maybeApplyPreferredTracks(
        player: ExoPlayer,
        audioTracks: List<TrackInfo>,
        subtitleTracks: List<TrackInfo>,
        selectedAudioLang: String?
    ) {
        if (preferredTracksApplied || (audioTracks.isEmpty() && subtitleTracks.isEmpty())) return

        val preferredAudioLanguage = appPreferences.preferredAudioLanguage
        val preferredSubtitleLanguage = appPreferences.preferredSubtitleLanguage

        val preferredAudioTrack = if (preferredAudioLanguage != "original") {
            audioTracks.firstOrNull { languageMatches(it.language, preferredAudioLanguage) }
        } else {
            null
        }

        val effectiveAudioLanguage = preferredAudioTrack?.language ?: selectedAudioLang
        if (preferredAudioTrack != null && !preferredAudioTrack.isSelected) {
            applyAudioTrackOverride(preferredAudioTrack.index, preferredAudioTrack.trackIndex)
        }

        val shouldDisableSubtitles =
            preferredSubtitleLanguage == "none" ||
                isLanguageExcluded(effectiveAudioLanguage, appPreferences.subtitleExcludeLanguages)

        if (shouldDisableSubtitles) {
            if (subtitleTracks.any { it.isSelected } || preferredSubtitleLanguage == "none") {
                applySubtitleTrackOverride(-1, 0)
            }
        } else {
            val preferredSubtitleTrack = subtitleTracks.firstOrNull {
                languageMatches(it.language, preferredSubtitleLanguage)
            }
            if (preferredSubtitleTrack != null && !preferredSubtitleTrack.isSelected) {
                applySubtitleTrackOverride(preferredSubtitleTrack.index, preferredSubtitleTrack.trackIndex)
            }
        }

        val hasTextGroups = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
        val audioResolved = preferredAudioLanguage == "original" || audioTracks.isNotEmpty()
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

    private fun extractChapters() {
        val player = _player ?: return

        try {
            val timeline = player.currentTimeline
            if (timeline.windowCount > 0) {
                val window = androidx.media3.common.Timeline.Window()
                timeline.getWindow(0, window)
                
                // Fast path: if chapters are already populated
                if (_uiState.value.chapters.isNotEmpty()) return
                
                val chapters = mutableListOf<com.mkbhdana.streamhive.player.ui.ChapterInfo>()
                val mediaItem = window.mediaItem
                val extras = mediaItem.mediaMetadata.extras

                // ExoPlayer exposes embedded chapters through MediaMetadata extras (matroska/mkv)
                // For formats without embedded chapters, this list will be empty
                if (extras != null) {
                    val chapterCount = extras.getInt("chapter_count", 0)
                    for (i in 0 until chapterCount) {
                        val title = extras.getString("chapter_title_$i") ?: "Chapter ${i + 1}"
                        val startMs = extras.getLong("chapter_start_$i", 0L)
                        val endMs = extras.getLong("chapter_end_$i", 0L)
                        chapters.add(com.mkbhdana.streamhive.player.ui.ChapterInfo(title, startMs, endMs))
                    }
                }
                
                // Virtual Chapters Fallback removed
                
                if (chapters.isNotEmpty()) {
                    _uiState.update { it.copy(chapters = chapters) }
                }
            }
        } catch (e: Exception) {
            Log.w("PlayerVM", "Chapter extraction failed: ${e.message}")
        }
    }

    fun seekToChapter(index: Int) {
        val chapters = _uiState.value.chapters
        if (index in chapters.indices) {
            _player?.seekTo(chapters[index].startMs)
        }
    }

    fun seekToNextChapter() {
        val chapters = _uiState.value.chapters
        val currentPos = _player?.currentPosition ?: return
        val nextChapter = chapters.firstOrNull { it.startMs > currentPos + 1000 }
        if (nextChapter != null) {
            _player?.seekTo(nextChapter.startMs)
        }
    }

    fun seekToPreviousChapter() {
        val chapters = _uiState.value.chapters
        val currentPos = _player?.currentPosition ?: return
        // Go to start of current chapter if > 3s in, else previous chapter
        val currentIdx = chapters.indexOfLast { it.startMs <= currentPos }
        if (currentIdx >= 0) {
            if (currentPos - chapters[currentIdx].startMs > 3000 || currentIdx == 0) {
                _player?.seekTo(chapters[currentIdx].startMs)
            } else {
                _player?.seekTo(chapters[currentIdx - 1].startMs)
            }
        }
    }

    fun togglePlayPause() {
        _player?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        _player?.seekTo(positionMs)
    }

    fun seekForward(ms: Long = 10_000) {
        _player?.let { it.seekTo((it.currentPosition + ms).coerceAtMost(it.duration)) }
    }

    fun seekBackward(ms: Long = 10_000) {
        _player?.let { it.seekTo((it.currentPosition - ms).coerceAtLeast(0)) }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }
    
    fun setDecoderMode(mode: String) {
        if (_uiState.value.decoderMode == mode) return
        sessionDecoderMode = mode
        _uiState.update {
            it.copy(
                decoderMode = mode,
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

    fun prepareForEngineFallback() {
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
        // Note: ExoPlayer doesn't have native subtitle delay; this would require re-sync logic
        // For MPV, the command is: sub-delay <seconds>
    }

    fun setPlaybackSpeed(speed: Float) {
        _player?.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun loadExternalSubtitle(uri: Uri) {
        val player = _player ?: return
        val currentItem = player.currentMediaItem ?: return

        val subtitleConfig = SubtitleConfiguration.Builder(uri)
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val newItem = currentItem.buildUpon()
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        val currentPosition = player.currentPosition
        player.setMediaItem(newItem)
        player.seekTo(currentPosition)
        player.prepare()
    }

    override fun onCleared() {
        super.onCleared()
        positionSaveJob?.cancel()
        savePlaybackPosition()
        
        // Revert to releasing on the main thread to prevent IllegalStateException.
        // ExoPlayer must be released on the thread it was created on.
        _player?.release()
        _player = null
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
            playbackHistoryDao.upsert(
                PlaybackHistoryEntity(
                    fileId = currentFileId,
                    fileName = currentFileName,
                    driveId = fileEntity?.driveId ?: "",
                    lastPosition = pos,
                    duration = dur,
                    lastPlayedAt = System.currentTimeMillis(),
                    posterPath = metadata?.posterPath,
                    thumbnailUrl = fileEntity?.thumbnailLink
                )
            )
        }
    }
}
