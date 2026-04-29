package com.mkbhdana.streamhive.player.mpv

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkbhdana.streamhive.catalog.DriveRepository
import com.mkbhdana.streamhive.data.db.PlaybackHistoryDao
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
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
import javax.inject.Inject

@HiltViewModel
class MpvPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveRepository: DriveRepository,
    private val appPreferences: AppPreferences,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val mediaFileDao: com.mkbhdana.streamhive.data.db.MediaFileDao,
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
            resizeMode = appPreferences.defaultResizeMode
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val mpvPlayer = MpvPlayer(context)

    // Resume playback support
    private var pendingSeekMs: Long = 0L
    private var hasResumed: Boolean = false
    private var positionSaveJob: kotlinx.coroutines.Job? = null
    
    // Error retry mechanism
    private var retryCount = 0
    private val MAX_RETRIES = 3

    init {
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
                val currentFile = mediaFileDao.getFileById(currentFileId) ?: return@launch
                val allFiles = mediaFileDao.getFilesByFolderSync(currentFile.driveId, currentFile.parentId)
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

    fun playEpisode(fileId: String, fileName: String) {
        if (fileId == currentFileId) return
        
        savePlaybackPosition()
        currentFileId = fileId
        currentFileName = fileName
        
        _uiState.update { 
            it.copy(
                fileName = fileName, 
                isLoading = true,
                currentPosition = 0L,
                bufferedPercentage = 0
            ) 
        }
        
        hasResumed = false
        pendingSeekMs = 0L
        
        val streamUrl = streamProxyServer.getStreamUrl(currentFileId)
        mpvPlayer.loadFile(streamUrl)
        mpvPlayer.play()
    }

    private fun setupPlayer() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                mpvPlayer.initialize()

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
                        _uiState.update { it.copy(duration = durationMs) }
                    }

                    override fun onPositionChanged(positionMs: Long) {
                        _uiState.update { it.copy(currentPosition = positionMs) }
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
                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            val pos = _uiState.value.currentPosition
                            if (pos > 0) pendingSeekMs = pos
                            hasResumed = false
                            android.util.Log.w("MpvVM", "MPV Error. Retrying ($retryCount/$MAX_RETRIES): $message")
                            val streamUrl = streamProxyServer.getStreamUrl(currentFileId)
                            mpvPlayer.loadFile(streamUrl)
                            mpvPlayer.play()
                        } else {
                            _uiState.update {
                                it.copy(error = "MPV Error: $message (Failed after $MAX_RETRIES retries)", isLoading = false)
                            }
                        }
                    }

                    override fun onBuffering(isBuffering: Boolean) {
                        _uiState.update { it.copy(isLoading = isBuffering) }
                    }

                    override fun onTracksChanged() {
                        updateTrackInfo()
                    }
                })

                val history = playbackHistoryDao.getByFileId(currentFileId)
                val startPosMs = if (history != null && !history.isCompleted && history.lastPosition > 0) history.lastPosition else 0L
                if (startPosMs > 0) {
                    pendingSeekMs = startPosMs
                }

                // Use proxy URL — no auth headers needed
                val streamUrl = streamProxyServer.getStreamUrl(currentFileId)
                mpvPlayer.loadFile(streamUrl)
                mpvPlayer.play()

                _uiState.update { it.copy(isLoading = false) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to start MPV player: ${e.message}"
                    )
                }
            }
        }
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
                    subtitleTracks.add(
                        TrackInfo(
                            index = id,
                            name = title.ifBlank { "Subtitle ${subtitleTracks.size + 1}" },
                            language = lang.ifBlank { null },
                            codec = codec.ifBlank { null },
                            isSelected = selected
                        )
                    )
                }
            }
        }

        _uiState.update {
            it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks)
        }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        mpvPlayer.attachSurface(surfaceView)
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

    fun toggleLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun setResizeMode(mode: String) {
        _uiState.update { it.copy(resizeMode = mode) }
    }

    fun selectAudioTrack(trackId: Int) {
        mpvPlayer.setAudioTrack(trackId)
        updateTrackInfo()
    }

    fun selectSubtitleTrack(trackId: Int) {
        if (trackId < 0) {
            mpvPlayer.disableSubtitles()
        } else {
            mpvPlayer.setSubtitleTrack(trackId)
        }
        updateTrackInfo()
    }

    fun setSubtitleDelay(delayMs: Long) {
        _uiState.update { it.copy(subtitleDelay = delayMs) }
        mpvPlayer.setSubtitleDelay(delayMs / 1000.0)
    }

    fun setPlaybackSpeed(speed: Float) {
        mpvPlayer.setSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun loadExternalSubtitle(uri: Uri) {
        val path = uri.toString()
        mpvPlayer.addExternalSubtitle(path)
    }

    override fun onCleared() {
        super.onCleared()
        positionSaveJob?.cancel()
        savePlaybackPosition()
        mpvPlayer.destroy()
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
            playbackHistoryDao.upsert(
                PlaybackHistoryEntity(
                    fileId = currentFileId,
                    fileName = currentFileName,
                    driveId = fileEntity?.driveId ?: "",
                    lastPosition = pos,
                    duration = dur,
                    lastPlayedAt = System.currentTimeMillis(),
                    thumbnailUrl = fileEntity?.thumbnailLink
                )
            )
        }
    }
}
