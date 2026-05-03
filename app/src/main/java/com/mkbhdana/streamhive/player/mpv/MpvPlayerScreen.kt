package com.mkbhdana.streamhive.player.mpv

import android.app.Activity
import android.net.Uri
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mkbhdana.streamhive.player.PlaybackErrorOverlay
import com.mkbhdana.streamhive.player.PlayerWindowMode
import com.mkbhdana.streamhive.player.PlayerSwitchingOverlay
import com.mkbhdana.streamhive.player.enterPlayerWindowMode
import com.mkbhdana.streamhive.player.exitPlayerWindowMode
import com.mkbhdana.streamhive.player.gesture.GestureIndicatorOverlay
import com.mkbhdana.streamhive.player.gesture.GestureState
import com.mkbhdana.streamhive.player.gesture.PlayerGestureHandler
import com.mkbhdana.streamhive.player.ui.PlayerControlsOverlay
import com.mkbhdana.streamhive.ui.theme.AccentCyan
import kotlinx.coroutines.delay

@Composable
fun MpvPlayerScreen(
    onBack: () -> Unit,
    allowEngineFallback: Boolean = true,
    switchingMessage: String? = null,
    onFallbackToExo: (() -> Unit)? = null,
    onSwitchToExo: (() -> Unit)? = null,
    viewModel: MpvPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gestureState = remember { mutableStateOf(GestureState()) }
    var controlsInteractionActive by remember { mutableStateOf(false) }
    var seekPillSignal by remember { mutableIntStateOf(0) }
    var engineFallbackRequested by remember { mutableStateOf(false) }
    var keepWindowModeForHandoff by remember { mutableStateOf(false) }
    var isSwitchingPlayer by remember(switchingMessage) { mutableStateOf(switchingMessage != null) }
    var activeSwitchingMessage by remember(switchingMessage) { mutableStateOf(switchingMessage) }

    // Intercept back navigation to instantly restore orientation
    val handleBack = {
        val activity = context as? Activity
        keepWindowModeForHandoff = false
        isSwitchingPlayer = false
        activeSwitchingMessage = null
        activity?.exitPlayerWindowMode()
        onBack()
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.recoverVideoOutput()
        uri?.let { viewModel.loadExternalSubtitle(it) }
    }

    fun showQuickSeekPill(deltaMs: Long) {
        val targetPosition = (uiState.currentPosition + deltaMs).coerceIn(0L, uiState.duration.coerceAtLeast(0L))
        gestureState.value = gestureState.value.copy(
            showSeekIndicator = true,
            showVolumeIndicator = false,
            showBrightnessIndicator = false,
            showZoomIndicator = false,
            seekDeltaSeconds = (deltaMs / 1000L).toInt(),
            seekToPosition = targetPosition,
            showSeekTimestamp = false
        )
        seekPillSignal++
    }

    fun quickSeekForward() {
        val seekMs = uiState.tapSeekDuration * 1000L
        showQuickSeekPill(seekMs)
        viewModel.seekForward(seekMs)
    }

    fun quickSeekBackward() {
        val seekMs = uiState.tapSeekDuration * 1000L
        showQuickSeekPill(-seekMs)
        viewModel.seekBackward(seekMs)
    }

    PlayerWindowMode(restoreOnDispose = !keepWindowModeForHandoff)

    // Auto-hide controls (paused while a panel is open)
    LaunchedEffect(uiState.showControls, uiState.isPlaying, controlsInteractionActive) {
        if (uiState.showControls && uiState.isPlaying && !uiState.isLocked && !controlsInteractionActive) {
            delay(8000)
            viewModel.hideControls()
        }
    }

    LaunchedEffect(seekPillSignal) {
        if (seekPillSignal > 0) {
            delay(800)
            gestureState.value = gestureState.value.copy(showSeekIndicator = false)
        }
    }

    LaunchedEffect(uiState.error, allowEngineFallback, onFallbackToExo) {
        if (uiState.error != null && allowEngineFallback && onFallbackToExo != null && !engineFallbackRequested) {
            Toast.makeText(context, "There was some error in playing the file", Toast.LENGTH_SHORT).show()
            engineFallbackRequested = true
            keepWindowModeForHandoff = true
            isSwitchingPlayer = true
            activeSwitchingMessage = "Switching to Exo"
            viewModel.hideControls()
            viewModel.prepareForEngineFallback()
            (context as? Activity)?.enterPlayerWindowMode()
            onFallbackToExo()
        } else if (uiState.error != null && (!allowEngineFallback || onFallbackToExo == null)) {
            isSwitchingPlayer = false
            activeSwitchingMessage = null
        }
    }

    LaunchedEffect(isSwitchingPlayer, uiState.isLoading, uiState.duration, uiState.currentPosition, uiState.isPlaying) {
        val playerReady = !uiState.isLoading && (uiState.duration > 0L || uiState.currentPosition > 0L || uiState.isPlaying)
        if (isSwitchingPlayer && playerReady) {
            delay(220)
            isSwitchingPlayer = false
            activeSwitchingMessage = null
        }
    }

    LaunchedEffect(gestureState.value.zoomLevel) {
        viewModel.applySubtitleZoomCompensation(gestureState.value.zoomLevel)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> viewModel.suspendVideoOutputForTransientView()
                Lifecycle.Event.ON_RESUME -> viewModel.recoverVideoOutput()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler { handleBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // MPV SurfaceView surface
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { viewModel.attachSurface(it) }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = gestureState.value.zoomLevel
                    scaleY = gestureState.value.zoomLevel
                }
        )

        // Gesture layer
        PlayerGestureHandler(
            currentPosition = uiState.currentPosition,
            duration = uiState.duration,
            onToggleControls = { viewModel.toggleControls() },
            onCenterTap = { viewModel.togglePlayPause() },
            onSeekForward = { quickSeekForward() },
            onSeekBackward = { quickSeekBackward() },
            onSeekTo = { viewModel.seekTo(it) },
            onVolumeChange = { },
            onBrightnessChange = { },
            gestureState = gestureState,
            isLocked = uiState.isLocked,
            seekEnabled = uiState.gestureSeekEnabled,
            volumeEnabled = uiState.gestureVolumeEnabled,
            brightnessEnabled = uiState.gestureBrightnessEnabled,
            doubleTapEnabled = uiState.gestureDoubleTapEnabled,
            zoomEnabled = uiState.gestureZoomEnabled
        ) {
            GestureIndicatorOverlay(gestureState = gestureState.value)
        }

        // Loading
        if (uiState.isLoading || isSwitchingPlayer) {
            if (isSwitchingPlayer && activeSwitchingMessage != null) {
                PlayerSwitchingOverlay(message = activeSwitchingMessage.orEmpty())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        // Error
        if (uiState.error != null && !isSwitchingPlayer) {
            PlaybackErrorOverlay(
                errorMessage = uiState.error.orEmpty(),
                onBack = handleBack
            )
        }

        // Controls overlay
        AnimatedVisibility(
            visible = uiState.showControls && uiState.error == null && !isSwitchingPlayer,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControlsOverlay(
                fileName = uiState.fileName,
                engineLabel = "MPV Player",
                engineColor = AccentCyan,
                isPlaying = uiState.isPlaying,
                currentPosition = uiState.currentPosition,
                duration = uiState.duration,
                isLocked = uiState.isLocked,
                currentResizeMode = uiState.resizeMode,
                decoderMode = uiState.decoderMode,
                decoderOptions = listOf("hw", "hw+", "auto", "sw"),
                playbackSpeed = uiState.playbackSpeed,
                subtitleDelay = uiState.subtitleDelay,
                subtitleSpeed = uiState.subtitleSpeed,
                audioTracks = uiState.audioTracks,
                subtitleTracks = uiState.subtitleTracks,
                onBack = handleBack,
                onPlayPause = viewModel::togglePlayPause,
                onSeekForward = { quickSeekForward() },
                onSeekBackward = { quickSeekBackward() },
                onSeekTo = viewModel::seekTo,
                onLockToggle = viewModel::toggleLock,
                onResizeModeChange = viewModel::setResizeMode,
                onDecoderModeChange = viewModel::setDecoderMode,
                onAudioTrackSelect = { viewModel.selectAudioTrack(it.index) },
                onSubtitleTrackSelect = { viewModel.selectSubtitleTrack(it?.index ?: -1) },
                onSubtitleTrackRemove = viewModel::removeExternalSubtitle,
                onSubtitleDelayChange = viewModel::setSubtitleDelay,
                onSpeedChange = viewModel::setPlaybackSpeed,
                onLoadExternalSubtitle = {
                    viewModel.suspendVideoOutputForTransientView()
                    subtitlePicker.launch("*/*")
                },
                subtitleFontSize = uiState.subtitleFontSize,
                subtitleColor = uiState.subtitleColor,
                subtitlePosition = uiState.subtitlePosition,
                subtitleOutlineColor = uiState.subtitleOutlineColor,
                subtitleBgOpacity = uiState.subtitleBgOpacity,
                subtitleEdgeSize = uiState.subtitleEdgeSize,
                overrideAssSubtitleStyles = uiState.overrideAssSubtitleStyles,
                onSubtitleFontSizeChange = viewModel::setSubtitleFontSize,
                onSubtitleColorChange = viewModel::setSubtitleColor,
                onSubtitlePositionChange = viewModel::setSubtitlePosition,
                onSubtitleOutlineColorChange = viewModel::setSubtitleOutlineColor,
                onSubtitleBgOpacityChange = viewModel::setSubtitleBgOpacity,
                onSubtitleEdgeSizeChange = viewModel::setSubtitleEdgeSize,
                onOverrideAssSubtitleStylesChange = viewModel::setOverrideAssSubtitleStyles,
                onSubtitleSpeedChange = viewModel::setSubtitleSpeed,
                onSubtitleStyleReset = viewModel::resetSubtitleStyle,
                switchPlayerLabel = if (onSwitchToExo != null) "EXO" else null,
                onSwitchPlayer = if (onSwitchToExo != null) {
                    {
                        keepWindowModeForHandoff = true
                        isSwitchingPlayer = true
                        activeSwitchingMessage = "Switching to Exo"
                        viewModel.hideControls()
                        viewModel.prepareForEngineFallback()
                        (context as? Activity)?.enterPlayerWindowMode()
                        onSwitchToExo()
                    }
                } else {
                    null
                },
                onOpenExternal = {
                    viewModel.getProxyUrl()?.let { url ->
                        viewModel.pauseForExternalLaunch()
                        viewModel.suspendVideoOutputForTransientView()
                        com.mkbhdana.streamhive.player.proxy.StreamProxyService.start(context)
                        com.mkbhdana.streamhive.player.ExternalPlayerLauncher.launch(context, url, uiState.fileName)
                    }
                },
                episodeList = uiState.episodeList,
                onEpisodeSelect = viewModel::playEpisode,
                onPanelOpened = { controlsInteractionActive = true },
                onPanelClosed = { controlsInteractionActive = false }
            )
        }
    }
}
