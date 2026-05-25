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
    onFallbackToExo: ((String?) -> Unit)? = null,
    onSwitchToExo: ((String?) -> Unit)? = null,
    navKey: com.mkbhdana.streamhive.navigation.MpvPlayerRoute = com.mkbhdana.streamhive.navigation.MpvPlayerRoute("", ""),
    viewModel: MpvPlayerViewModel = hiltViewModel<MpvPlayerViewModel, MpvPlayerViewModel.Factory>(
        creationCallback = { factory -> factory.create(navKey) }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gestureState = remember { mutableStateOf(GestureState()) }
    var controlsInteractionActive by remember { mutableStateOf(false) }
    var seekPillSignal by remember { mutableIntStateOf(0) }
    var speedHoldRestoreSpeed by remember { mutableStateOf<Float?>(null) }
    var engineFallbackRequested by remember { mutableStateOf(false) }
    var keepWindowModeForHandoff by remember { mutableStateOf(false) }
    var isSwitchingPlayer by remember(switchingMessage) { mutableStateOf(switchingMessage != null) }
    var activeSwitchingMessage by remember(switchingMessage) { mutableStateOf(switchingMessage) }
    val latestPlaybackSpeed by rememberUpdatedState(uiState.playbackSpeed)

    // Intercept back navigation to instantly restore orientation
    val handleBack = {
        val activity = context as? Activity
        keepWindowModeForHandoff = false
        isSwitchingPlayer = false
        activeSwitchingMessage = null
        viewModel.pause()
        activity?.exitPlayerWindowMode()
        onBack()
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.recoverVideoOutput()
        uri?.let { viewModel.loadExternalSubtitle(it) }
    }

    fun showQuickSeekPill(deltaMs: Long, tapCount: Int = 0) {
        val targetPosition = (uiState.currentPosition + deltaMs).coerceIn(0L, uiState.duration.coerceAtLeast(0L))
        gestureState.value = gestureState.value.copy(
            showSeekIndicator = true,
            showVolumeIndicator = false,
            showBrightnessIndicator = false,
            showZoomIndicator = false,
            showSpeedIndicator = false,
            showLockIndicator = false,
            seekDeltaSeconds = (deltaMs / 1000L).toInt(),
            seekToPosition = targetPosition,
            showSeekTimestamp = false,
            tapChainCount = tapCount
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

    fun startSpeedHold() {
        if (speedHoldRestoreSpeed == null) {
            speedHoldRestoreSpeed = latestPlaybackSpeed
            viewModel.setPlaybackSpeed(2.0f)
        }
    }

    fun stopSpeedHold() {
        val restoreSpeed = speedHoldRestoreSpeed ?: return
        speedHoldRestoreSpeed = null
        viewModel.setPlaybackSpeed(restoreSpeed)
    }

    // Release player when this composable leaves composition (back navigation)
    DisposableEffect(Unit) {
        onDispose {
            speedHoldRestoreSpeed?.let { viewModel.setPlaybackSpeed(it) }
            viewModel.releasePlayer()
        }
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

    // Keep gesture lock state in sync with the ViewModel's lock state
    LaunchedEffect(uiState.isLocked) {
        gestureState.value = gestureState.value.copy(isLockActive = uiState.isLocked)
    }

    LaunchedEffect(uiState.error, allowEngineFallback, onFallbackToExo) {
        if (uiState.error != null && allowEngineFallback && onFallbackToExo != null && !engineFallbackRequested) {
            Toast.makeText(context, "There was some error in playing the file", Toast.LENGTH_SHORT).show()
            engineFallbackRequested = true
            keepWindowModeForHandoff = true
            isSwitchingPlayer = true
            activeSwitchingMessage = "Switching to Exo"
            viewModel.hideControls()
            viewModel.prepareForEngineFallback(PlayerEngine.EXO_PLAYER)
            (context as? Activity)?.enterPlayerWindowMode()
            onFallbackToExo(uiState.decoderMode)
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
        viewModel.setVideoZoom(gestureState.value.zoomLevel)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    stopSpeedHold()
                    viewModel.suspendVideoOutputForTransientView()
                }
                Lifecycle.Event.ON_STOP -> {
                    stopSpeedHold()
                    viewModel.suspendVideoOutputForTransientView()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.cancelExternalPlayerCleanup()
                    viewModel.recoverVideoOutput()
                }
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
            modifier = Modifier.fillMaxSize()
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
            onSpeedHoldStart = { startSpeedHold() },
            onSpeedHoldEnd = { stopSpeedHold() },
            onLockToggle = { viewModel.toggleLock() },
            onProgressiveTapSeek = { isForward, tapCount ->
                val seekMs = uiState.tapSeekDuration * 1000L
                if (isForward) viewModel.seekForward(seekMs) else viewModel.seekBackward(seekMs)
                val cumulativeSec = tapCount * uiState.tapSeekDuration
                showQuickSeekPill(
                    deltaMs = if (isForward) cumulativeSec * 1000L else -cumulativeSec * 1000L,
                    tapCount = tapCount
                )
            },
            gestureState = gestureState,
            isLocked = uiState.isLocked,
            seekEnabled = uiState.gestureSeekEnabled,
            volumeEnabled = uiState.gestureVolumeEnabled,
            brightnessEnabled = uiState.gestureBrightnessEnabled,
            doubleTapEnabled = uiState.gestureDoubleTapEnabled,
            zoomEnabled = uiState.gestureZoomEnabled,
            speedPressEnabled = uiState.gestureSpeedPressEnabled,
            lockPressEnabled = uiState.gestureLockEnabled,
            hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
            gestureSensitivity = uiState.gestureSensitivity,
            tapSeekDuration = uiState.tapSeekDuration
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
                hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                audioTracks = uiState.audioTracks,
                subtitleTracks = uiState.subtitleTracks,
                onBack = handleBack,
                onPlayPause = viewModel::togglePlayPause,
                onSeekForward = { quickSeekForward() },
                onSeekBackward = { quickSeekBackward() },
                onSeekTo = viewModel::seekTo,
                onLockToggle = viewModel::toggleLock,
                onResizeModeChange = { mode ->
                    viewModel.setResizeMode(mode)
                    // Reset pinch zoom when resize mode is changed
                    gestureState.value = gestureState.value.copy(zoomLevel = 1f)
                },
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

                subtitleBgOpacity = uiState.subtitleBgOpacity,
                subtitleEdgeSize = uiState.subtitleEdgeSize,
                overrideAssSubtitleStyles = uiState.overrideAssSubtitleStyles,
                onSubtitleFontSizeChange = viewModel::setSubtitleFontSize,
                onSubtitleColorChange = viewModel::setSubtitleColor,
                onSubtitlePositionChange = viewModel::setSubtitlePosition,

                onSubtitleBgOpacityChange = viewModel::setSubtitleBgOpacity,
                onSubtitleEdgeSizeChange = viewModel::setSubtitleEdgeSize,
                onOverrideAssSubtitleStylesChange = viewModel::setOverrideAssSubtitleStyles,
                onSubtitleSpeedChange = viewModel::setSubtitleSpeed,
                onSubtitleStyleReset = viewModel::resetSubtitleStyle,
                subtitleScale = uiState.subtitleScale,

                subtitleBold = uiState.subtitleBold,
                subtitleItalic = uiState.subtitleItalic,
                subtitleAlignment = uiState.subtitleAlignment,
                onSubtitleScaleChange = viewModel::setSubtitleScale,

                onSubtitleBoldChange = viewModel::setSubtitleBold,
                onSubtitleItalicChange = viewModel::setSubtitleItalic,
                onSubtitleAlignmentChange = viewModel::setSubtitleAlignment,
                switchPlayerLabel = if (onSwitchToExo != null) "EXO" else null,
                onSwitchPlayer = if (onSwitchToExo != null) {
                    {
                        keepWindowModeForHandoff = true
                        isSwitchingPlayer = true
                        activeSwitchingMessage = "Switching to Exo"
                        viewModel.hideControls()
                        viewModel.prepareForEngineFallback(PlayerEngine.EXO_PLAYER)
                        (context as? Activity)?.enterPlayerWindowMode()
                        onSwitchToExo(uiState.decoderMode)
                    }
                } else {
                    null
                },
                onOpenExternal = {
                    viewModel.getProxyUrl()?.let { url ->
                        viewModel.pauseForExternalLaunch()
                        viewModel.scheduleExternalPlayerCleanup()
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
