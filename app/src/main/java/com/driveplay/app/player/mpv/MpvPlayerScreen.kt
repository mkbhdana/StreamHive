package com.driveplay.app.player.mpv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.SurfaceView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.driveplay.app.player.gesture.GestureIndicatorOverlay
import com.driveplay.app.player.gesture.GestureState
import com.driveplay.app.player.gesture.PlayerGestureHandler
import com.driveplay.app.player.ui.PlayerControlsOverlay
import com.driveplay.app.ui.theme.AccentCyan
import kotlinx.coroutines.delay

@Composable
fun MpvPlayerScreen(
    onBack: () -> Unit,
    viewModel: MpvPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val gestureState = remember { mutableStateOf(GestureState()) }
    var controlsInteractionActive by remember { mutableStateOf(false) }

    // Intercept back navigation to instantly restore orientation
    val handleBack = {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onBack()
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadExternalSubtitle(it) }
    }

    // Force landscape + immersive mode
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }



    // Auto-hide controls (paused while a panel is open)
    LaunchedEffect(uiState.showControls, uiState.isPlaying, controlsInteractionActive) {
        if (uiState.showControls && uiState.isPlaying && !uiState.isLocked && !controlsInteractionActive) {
            delay(8000)
            viewModel.hideControls()
        }
    }

    BackHandler { handleBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // MPV SurfaceView
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { surface ->
                    viewModel.attachSurface(surface)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture layer
        PlayerGestureHandler(
            currentPosition = uiState.currentPosition,
            duration = uiState.duration,
            onToggleControls = { viewModel.toggleControls() },
            onSeekForward = { viewModel.seekForward() },
            onSeekBackward = { viewModel.seekBackward() },
            onSeekTo = { viewModel.seekTo(it) },
            onVolumeChange = { },
            onBrightnessChange = { },
            gestureState = gestureState,
            isLocked = uiState.isLocked
        ) {
            GestureIndicatorOverlay(gestureState = gestureState.value)
        }

        // Loading
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }

        // Error
        if (uiState.error != null) {
            Card(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(uiState.error ?: "", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go Back") }
                }
            }
        }

        // Controls overlay
        AnimatedVisibility(
            visible = uiState.showControls && uiState.error == null,
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
                playbackSpeed = uiState.playbackSpeed,
                subtitleDelay = uiState.subtitleDelay,
                audioTracks = uiState.audioTracks,
                subtitleTracks = uiState.subtitleTracks,
                onBack = handleBack,
                onPlayPause = viewModel::togglePlayPause,
                onSeekForward = viewModel::seekForward,
                onSeekBackward = viewModel::seekBackward,
                onSeekTo = viewModel::seekTo,
                onLockToggle = viewModel::toggleLock,
                onResizeModeChange = viewModel::setResizeMode,
                onAudioTrackSelect = viewModel::selectAudioTrack,
                onSubtitleTrackSelect = viewModel::selectSubtitleTrack,
                onSubtitleDelayChange = viewModel::setSubtitleDelay,
                onSpeedChange = viewModel::setPlaybackSpeed,
                onLoadExternalSubtitle = { subtitlePicker.launch("*/*") },
                onOpenExternal = {
                    viewModel.getProxyUrl()?.let { url ->
                        com.driveplay.app.player.ExternalPlayerLauncher.launch(context, url, uiState.fileName)
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
