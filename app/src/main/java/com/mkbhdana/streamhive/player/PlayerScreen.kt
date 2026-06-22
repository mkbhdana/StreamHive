package com.mkbhdana.streamhive.player

import android.app.Activity
import android.net.Uri
import android.view.WindowInsetsController
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mkbhdana.streamhive.player.gesture.GestureIndicatorOverlay
import com.mkbhdana.streamhive.player.gesture.GestureState
import com.mkbhdana.streamhive.player.gesture.PlayerGestureHandler
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.player.ui.NextEpisodeOverlay
import com.mkbhdana.streamhive.player.ui.PlayerControlsOverlay
import com.mkbhdana.streamhive.ui.theme.AccentGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    allowEngineFallback: Boolean = true,
    switchingMessage: String? = null,
    onFallbackToMpv: ((String?) -> Unit)? = null,
    onSwitchToMpv: ((String?) -> Unit)? = null,
    navKey: com.mkbhdana.streamhive.navigation.PlayerRoute = com.mkbhdana.streamhive.navigation.PlayerRoute("", ""),
    viewModel: PlayerViewModel = hiltViewModel<PlayerViewModel, PlayerViewModel.Factory>(
        creationCallback = { factory -> factory.create(navKey) }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gestureState = remember { mutableStateOf(GestureState()) }
    var controlsInteractionActive by remember { mutableStateOf(false) }
    var controlsActivity by remember { mutableIntStateOf(0) }
    var seekPillSignal by remember { mutableIntStateOf(0) }
    var speedHoldRestoreSpeed by remember { mutableStateOf<Float?>(null) }
    var engineFallbackRequested by remember { mutableStateOf(false) }
    var keepWindowModeForHandoff by remember { mutableStateOf(false) }
    var isSwitchingPlayer by remember(switchingMessage) { mutableStateOf(switchingMessage != null) }
    var activeSwitchingMessage by remember(switchingMessage) { mutableStateOf(switchingMessage) }
    val latestPlaybackSpeed by rememberUpdatedState(uiState.playbackSpeed)
    val canFallbackToMpv = remember(allowEngineFallback, onFallbackToMpv, viewModel) {
        allowEngineFallback && onFallbackToMpv != null && viewModel.isMpvAvailable()
    }
    val canSwitchToMpv = remember(onSwitchToMpv, viewModel) {
        onSwitchToMpv != null && viewModel.isMpvAvailable()
    }
    val gesturePreviewPosition = gestureState.value
        .takeIf {
            uiState.showControls &&
                it.showSeekIndicator &&
                it.showSeekTimestamp &&
                uiState.duration > 0L
        }
        ?.seekToPosition
        ?.coerceIn(0L, uiState.duration)
    val controlsCurrentPosition = gesturePreviewPosition ?: uiState.currentPosition

    fun showQuickSeekPill(deltaMs: Long, tapCount: Int = 0) {
        val basePosition = viewModel.player?.currentPosition ?: uiState.currentPosition
        val targetPosition = (basePosition + deltaMs).coerceIn(0L, uiState.duration.coerceAtLeast(0L))
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


    LaunchedEffect(seekPillSignal) {
        if (seekPillSignal > 0) {
            delay(800)
            gestureState.value = gestureState.value.copy(showSeekIndicator = false)
        }
    }

    // Keep gesture lock state in sync with the ViewModel's lock state
    // (so manual lock/unlock via controls keeps gesture handler in sync)
    LaunchedEffect(uiState.isLocked) {
        gestureState.value = gestureState.value.copy(isLockActive = uiState.isLocked)
    }

    LaunchedEffect(uiState.error, canFallbackToMpv) {
        if (uiState.error != null && canFallbackToMpv && !engineFallbackRequested) {
            Toast.makeText(context, "There was some error in playing the file", Toast.LENGTH_SHORT).show()
            engineFallbackRequested = true
            keepWindowModeForHandoff = true
            isSwitchingPlayer = true
            activeSwitchingMessage = "Switching to MPV"
            viewModel.hideControls()
            viewModel.prepareForEngineFallback(PlayerEngine.MPV)
            (context as? Activity)?.enterPlayerWindowMode()
            onFallbackToMpv?.invoke(uiState.decoderMode)
        } else if (uiState.error != null && !canFallbackToMpv) {
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

    val coroutineScope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }

    // Intercept back navigation to instantly restore orientation
    val handleBack: () -> Unit = {
        coroutineScope.launch {
            val activity = context as? Activity
            keepWindowModeForHandoff = false
            isSwitchingPlayer = false
            activeSwitchingMessage = null
            viewModel.player?.pause()
            
            // Remove the SurfaceView immediately to prevent Compose transition layout glitches
            isClosing = true
            delay(50)
            
            activity?.exitPlayerWindowMode()
            onBack()
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadExternalSubtitle(it) }
    }

    PlayerWindowMode(restoreOnDispose = !keepWindowModeForHandoff)

    // Release player when this composable leaves composition (back navigation)
    DisposableEffect(Unit) {
        onDispose {
            speedHoldRestoreSpeed?.let { viewModel.setPlaybackSpeed(it) }
            viewModel.releasePlayer()
        }
    }

    // Auto-hide controls (paused while a panel is open)
    // Auto-hide restarts on every control interaction (controlsActivity), so the
    // 8s timeout only counts from the user's last activity, not from when shown.
    LaunchedEffect(uiState.showControls, uiState.isPlaying, uiState.isLocked, controlsInteractionActive, controlsActivity) {
        if (uiState.showControls && uiState.isPlaying && !controlsInteractionActive) {
            delay(8000)
            viewModel.hideControls()
        }
    }

    // Position update ticker
    LaunchedEffect(uiState.isPlaying) {
        while (uiState.isPlaying) {
            viewModel.updatePosition()
            delay(500)
        }
    }

    // Auto pause player on backgrounding
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    stopSpeedHold()
                    viewModel.player?.pause()
                }
                Lifecycle.Event.ON_RESUME -> viewModel.cancelExternalPlayerCleanup()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // When playback finishes with nothing to auto-play (movie / last episode), close.
    LaunchedEffect(uiState.requestClose) {
        if (uiState.requestClose) { viewModel.consumeCloseRequest(); handleBack() }
    }

    BackHandler { handleBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video surface
        viewModel.player?.let { player ->
            val resizeModeInt = when (uiState.resizeMode) {
                "fit" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                "fill" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                "zoom" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                "16:9" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                "4:3" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            val currentZoom = gestureState.value.zoomLevel
            val context = androidx.compose.ui.platform.LocalContext.current
            
            // Create an independent subtitle view
            val independentSubtitleView = remember { 
                androidx.media3.ui.SubtitleView(context)
            }
            val subtitleCueScope = rememberCoroutineScope()
            var subtitleCueJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
            val updatedSubtitleDelay by rememberUpdatedState(uiState.subtitleDelay)
            
            // Apply Subtitle Styles
            LaunchedEffect(
                uiState.subtitleColor, uiState.subtitleBgOpacity, uiState.subtitleFontSize, 
                uiState.subtitlePosition, uiState.subtitleEdgeType, uiState.subtitleEdgeSize, uiState.subtitleOutlineColor,
                uiState.libassSubtitlesEnabled, uiState.overrideAssSubtitleStyles
            ) {
                val backgroundColor = android.graphics.Color.argb(
                    (uiState.subtitleBgOpacity * 255).toInt(), 0, 0, 0
                )
                val foregroundColor = uiState.subtitleColor.toInt()
                
                val edgeTypeInt = if (uiState.subtitleEdgeSize <= 0) {
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                } else {
                    when (uiState.subtitleEdgeType.lowercase()) {
                        "outline" -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                        "depressed" -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DEPRESSED
                        "shadow" -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                        "raised" -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_RAISED
                        else -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                    }
                }
                
                val style = androidx.media3.ui.CaptionStyleCompat(
                    foregroundColor,
                    backgroundColor,
                    android.graphics.Color.TRANSPARENT,
                    edgeTypeInt,
                    uiState.subtitleOutlineColor.toInt(),
                    null
                )
                independentSubtitleView.setStyle(style)
                val applyEmbeddedAssStyles = uiState.libassSubtitlesEnabled && !uiState.overrideAssSubtitleStyles
                independentSubtitleView.setApplyEmbeddedStyles(applyEmbeddedAssStyles)
                independentSubtitleView.setApplyEmbeddedFontSizes(applyEmbeddedAssStyles)
                independentSubtitleView.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, uiState.subtitleFontSize.toFloat())
                
                // Bottom Padding/Margin logic (Position 0 = bottom, 100 = top)
                val marginPercentage = (100 - uiState.subtitlePosition) / 100f
                independentSubtitleView.setBottomPaddingFraction(marginPercentage)
                independentSubtitleView.setCues(player.currentCues.cues)
            }
            
            // Listen to ExoPlayer cues manually for the independent subtitle view
            DisposableEffect(player) {
                fun renderCues(cues: List<androidx.media3.common.text.Cue>) {
                    subtitleCueJob?.cancel()
                    val delayMs = updatedSubtitleDelay
                    if (delayMs > 0) {
                        subtitleCueJob = subtitleCueScope.launch {
                            delay(delayMs)
                            independentSubtitleView.setCues(cues)
                        }
                    } else {
                        independentSubtitleView.setCues(cues)
                    }
                }

                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                        renderCues(cueGroup.cues)
                    }
                }
                player.addListener(listener)
                renderCues(player.currentCues.cues)
                onDispose {
                    subtitleCueJob?.cancel()
                    player.removeListener(listener)
                }
            }

            if (!isClosing) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            this.resizeMode = resizeModeInt
                            this.layoutTransition = android.animation.LayoutTransition()
                            this.subtitleView?.visibility = android.view.View.GONE
                        }
                    },
                    update = { view ->
                        view.resizeMode = resizeModeInt
                        if (view.player != player) {
                            view.player = player
                        }
                        
                        // Apply zoom directly to the video frame, leaving the subtitle canvas completely untouched
                        val contentFrame = view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_content_frame)
                        contentFrame?.scaleX = currentZoom
                        contentFrame?.scaleY = currentZoom
                        
                        view.subtitleView?.visibility = android.view.View.GONE
                    },
                    onRelease = { view ->
                        view.player = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Independent subtitle canvas overlaid on top
            AndroidView(
                factory = { independentSubtitleView },
                modifier = Modifier.fillMaxSize()
            )
        }

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
        }

        // Loading indicator (modern, no text, delayed to avoid flashing on quick seeks)
        var showLoader by remember { mutableStateOf(false) }
        val showImmediateLoader = isSwitchingPlayer || (uiState.isLoading && uiState.duration <= 0L)
        LaunchedEffect(uiState.isLoading, showImmediateLoader) {
            if (showImmediateLoader) {
                showLoader = true
            } else if (uiState.isLoading) {
                delay(1000)
                showLoader = true
            } else {
                showLoader = false
            }
        }

        if (showLoader) {
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
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(64.dp)
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
                engineLabel = "ExoPlayer",
                engineColor = AccentGreen,
                isPlaying = uiState.isPlaying,
                currentPosition = controlsCurrentPosition,
                duration = uiState.duration,
                bufferedPercentage = uiState.bufferedPercentage,
                isLocked = uiState.isLocked,
                currentResizeMode = uiState.resizeMode,
                decoderMode = uiState.decoderMode,
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
                onAudioTrackSelect = { track ->
                    viewModel.selectAudioTrack(track.index, track.trackIndex)
                },
                onSubtitleTrackSelect = { track ->
                    viewModel.selectSubtitleTrack(track?.index ?: -1, track?.trackIndex ?: 0)
                },
                onSubtitleTrackRemove = viewModel::removeExternalSubtitle,
                onSubtitleDelayChange = viewModel::setSubtitleDelay,
                onSpeedChange = viewModel::setPlaybackSpeed,
                onLoadExternalSubtitle = { subtitlePicker.launch("*/*") },
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
                switchPlayerLabel = if (canSwitchToMpv) "MPV" else null,
                onSwitchPlayer = if (canSwitchToMpv) {
                    {
                        keepWindowModeForHandoff = true
                        isSwitchingPlayer = true
                        activeSwitchingMessage = "Switching to MPV"
                        viewModel.hideControls()
                        viewModel.prepareForEngineFallback(PlayerEngine.MPV)
                        (context as? Activity)?.enterPlayerWindowMode()
                        onSwitchToMpv?.invoke(uiState.decoderMode)
                    }
                } else {
                    null
                },

                onOpenExternal = {
                    viewModel.getProxyUrl()?.let { url ->
                        // Pause in-app player before launching external
                        viewModel.player?.pause()
                        viewModel.scheduleExternalPlayerCleanup()
                        com.mkbhdana.streamhive.player.proxy.StreamProxyService.start(context)
                        ExternalPlayerLauncher.launch(context, url, uiState.fileName)
                    }
                },
                episodeList = uiState.episodeList,
                onEpisodeSelect = viewModel::playEpisode,
                onPanelOpened = { controlsInteractionActive = true },
                onPanelClosed = { controlsInteractionActive = false },
                onUserInteraction = { controlsActivity++ }
            )
        }

        GestureIndicatorOverlay(
            gestureState = gestureState.value,
            modifier = Modifier.fillMaxSize()
        )

        NextEpisodeOverlay(
            nextEpisode = uiState.nextEpisode,
            currentPosition = uiState.currentPosition,
            duration = uiState.duration,
            onPlayNext = { uiState.nextEpisode?.let { viewModel.playEpisode(it.id, it.name) } }
        )
    }
}
