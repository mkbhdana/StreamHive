package com.driveplay.app.player.mpv

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.driveplay.app.catalog.DriveRepository
import com.driveplay.app.data.db.PlaybackHistoryDao
import com.driveplay.app.data.db.PlaybackHistoryEntity
import com.driveplay.app.player.PlayerUiState
import com.driveplay.app.player.ui.TrackInfo
import com.driveplay.app.settings.AppPreferences
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val fileId: String = savedStateHandle.get<String>("fileId") ?: ""
    private val fileName: String = java.net.URLDecoder.decode(
        savedStateHandle.get<String>("fileName") ?: "", "UTF-8"
    )

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            fileName = fileName,
            resizeMode = appPreferences.defaultResizeMode
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val mpvPlayer = MpvPlayer(context)

    init {
        setupPlayer()
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
                    }

                    override fun onDurationChanged(durationMs: Long) {
                        _uiState.update { it.copy(duration = durationMs) }
                    }

                    override fun onPositionChanged(positionMs: Long) {
                        _uiState.update { it.copy(currentPosition = positionMs) }
                    }

                    override fun onError(message: String) {
                        _uiState.update {
                            it.copy(error = "MPV Error: $message", isLoading = false)
                        }
                    }

                    override fun onBuffering(isBuffering: Boolean) {
                        _uiState.update { it.copy(isLoading = isBuffering) }
                    }

                    override fun onTracksChanged() {
                        updateTrackInfo()
                    }
                })

                val token = driveRepository.getValidToken()
                    ?: throw Exception("Not authenticated")

                val history = playbackHistoryDao.getByFileId(fileId)
                val startPosMs = if (history != null && !history.isCompleted && history.lastPosition > 0) history.lastPosition else 0L

                val streamUrl = driveRepository.getStreamUrl(fileId)
                val headers = mapOf("Authorization" to "Bearer $token")

                mpvPlayer.loadFile(streamUrl, headers)
                if (startPosMs > 0) {
                    // Try to seek immediately. In MPV, setting "time-pos" might be needed instead of "seek" command if loading async,
                    // but the command queue usually caches it.
                    mpvPlayer.seekTo(startPosMs)
                }
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
        savePlaybackPosition()
        mpvPlayer.destroy()
    }

    private fun savePlaybackPosition() {
        val pos = _uiState.value.currentPosition
        val dur = _uiState.value.duration
        if (pos <= 0 || dur <= 0) return
        viewModelScope.launch {
            playbackHistoryDao.upsert(
                PlaybackHistoryEntity(
                    fileId = fileId,
                    fileName = fileName,
                    driveId = "",
                    lastPosition = pos,
                    duration = dur,
                    lastPlayedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
