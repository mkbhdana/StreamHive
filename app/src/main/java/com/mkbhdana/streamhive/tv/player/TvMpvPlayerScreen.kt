package com.mkbhdana.streamhive.tv.player

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mkbhdana.streamhive.navigation.MpvPlayerRoute
import com.mkbhdana.streamhive.player.ExternalPlayerLauncher
import com.mkbhdana.streamhive.player.PlayerSwitchingOverlay
import com.mkbhdana.streamhive.player.mpv.MpvPlayerViewModel
import com.mkbhdana.streamhive.player.proxy.StreamProxyService
import com.mkbhdana.streamhive.player.ui.NextEpisodeOverlay
import kotlinx.coroutines.delay

private val RESIZE_MODES_MPV = listOf("fit" to "Fit", "fill" to "Fill", "zoom" to "Zoom")
private val SPEEDS_MPV = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
private val DECODERS_MPV = listOf("hw" to "Hardware", "hw+" to "Hardware+", "auto" to "Auto", "sw" to "Software")

private enum class MpvPanel { Subtitles, Audio, Resize, Speed, Decoder, Episodes, SubtitleStyle }

@Composable
fun TvMpvPlayerScreen(
    navKey: MpvPlayerRoute,
    onBack: () -> Unit,
    onSwitchEngine: (resizeMode: String, speed: Float) -> Unit = { _, _ -> },
    viewModel: MpvPlayerViewModel = hiltViewModel<MpvPlayerViewModel, MpvPlayerViewModel.Factory>(
        key = "mpv_${navKey.instanceId}",
        creationCallback = { factory -> factory.create(navKey) }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val seekFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    var panel by remember { mutableStateOf<MpvPanel?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var switching by remember { mutableStateOf(navKey.handoff) }
    var engineSwitchTried by remember { mutableStateOf(false) }

    // Quick-seek with LEFT/RIGHT while the full controls are hidden — shows only a seekbar.
    var quickSeekTarget by remember { mutableStateOf<Long?>(null) }
    var seekBarTick by remember { mutableIntStateOf(0) }
    var showSeekBarOnly by remember { mutableStateOf(false) }
    LaunchedEffect(seekBarTick) {
        if (seekBarTick > 0) {
            showSeekBarOnly = true
            delay(1500)
            showSeekBarOnly = false
            quickSeekTarget = null
        }
    }
    val baseStepMs = (uiState.tapSeekDuration * 1000L).coerceAtLeast(1000L)
    val quickSeek: (Long) -> Unit = { delta ->
        val base = quickSeekTarget ?: uiState.currentPosition
        val target = (base + delta).coerceIn(0L, uiState.duration.coerceAtLeast(0L))
        quickSeekTarget = target
        viewModel.seekTo(target)
        seekBarTick++
    }

    // Auto-advance / close when playback finishes.
    LaunchedEffect(uiState.requestClose) {
        if (uiState.requestClose) { viewModel.consumeCloseRequest(); onBack() }
    }
    // On a fatal error, fall back to the other engine once (MPV → ExoPlayer).
    LaunchedEffect(uiState.error) {
        if (uiState.error != null && !navKey.handoff && !engineSwitchTried) {
            engineSwitchTried = true
            onSwitchEngine(uiState.resizeMode, uiState.playbackSpeed)
        }
    }

    LaunchedEffect(switching, uiState.isLoading, uiState.duration, uiState.currentPosition, uiState.isPlaying) {
        val ready = !uiState.isLoading && (uiState.duration > 0L || uiState.currentPosition > 0L || uiState.isPlaying)
        if (switching && ready) {
            // Carry the resize mode / speed over (MPV supports only fit/fill/zoom).
            if (navKey.resizeMode in setOf("fit", "fill", "zoom")) viewModel.setResizeMode(navKey.resizeMode)
            if (navKey.playbackSpeed > 0f) viewModel.setPlaybackSpeed(navKey.playbackSpeed)
            delay(250); switching = false
        }
    }
    LaunchedEffect(uiState.showControls, panel) {
        if (uiState.showControls && panel == null) runCatching { seekFocus.requestFocus() }
        else if (!uiState.showControls) runCatching { rootFocus.requestFocus() }
    }
    LaunchedEffect(uiState.showControls, uiState.isPlaying, interaction, panel) {
        if (uiState.showControls && uiState.isPlaying && panel == null) {
            delay(6000)
            viewModel.hideControls()
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    // Pause playback immediately when the app is backgrounded.
                    viewModel.pause()
                    viewModel.suspendVideoOutputForTransientView()
                }
                Lifecycle.Event.ON_RESUME -> viewModel.recoverVideoOutput()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) { onDispose { viewModel.releasePlayer() } }

    BackHandler {
        when {
            panel != null -> panel = null
            uiState.showControls -> viewModel.hideControls()
            else -> onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (!uiState.showControls && panel == null && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        // LEFT/RIGHT scrub without unhiding the full controls (seekbar only).
                        Key.DirectionLeft -> { quickSeek(-baseStepMs); true }
                        Key.DirectionRight -> { quickSeek(baseStepMs); true }
                        // OK toggles play/pause and reveals controls; DOWN/UP just reveal.
                        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                            viewModel.togglePlayPause(); viewModel.showControls(); interaction++; true
                        }
                        Key.DirectionDown, Key.DirectionUp -> {
                            viewModel.showControls(); interaction++; true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx -> SurfaceView(ctx).also { it.keepScreenOn = true; viewModel.attachSurface(it) } },
            modifier = Modifier.fillMaxSize()
        )

        if (uiState.isLoading && !switching) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center))
        }
        uiState.error?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(48.dp))
        }

        TvPlayerControls(
            visible = uiState.showControls && uiState.error == null && panel == null,
            fileName = uiState.fileName,
            sourceLabel = "MPV · ${uiState.decoderMode}",
            isPlaying = uiState.isPlaying,
            currentPosition = uiState.currentPosition,
            duration = uiState.duration,
            baseStepMs = uiState.tapSeekDuration * 1000L,
            hasEpisodes = uiState.episodeList.size > 1,
            canSwitchEngine = true,
            seekFocusRequester = seekFocus,
            onPlayPause = { viewModel.togglePlayPause(); interaction++ },
            onSeekTo = { viewModel.seekTo(it); interaction++ },
            onSubtitles = { panel = MpvPanel.Subtitles },
            onAudio = { panel = MpvPanel.Audio },
            onEpisodes = { panel = MpvPanel.Episodes },
            onSubtitleStyle = { panel = MpvPanel.SubtitleStyle },
            onResize = { panel = MpvPanel.Resize },
            onSpeed = { panel = MpvPanel.Speed },
            onDecoder = { panel = MpvPanel.Decoder },
            onSwitchEngine = { onSwitchEngine(uiState.resizeMode, uiState.playbackSpeed) },
            onExternal = {
                viewModel.getProxyUrl()?.let { url ->
                    viewModel.pauseForExternalLaunch()
                    StreamProxyService.start(context)
                    ExternalPlayerLauncher.launch(context, url, uiState.fileName)
                }
            },
            onInteraction = { interaction++ }
        )

        TvSeekBarOnly(
            visible = showSeekBarOnly && !uiState.showControls && panel == null,
            position = quickSeekTarget ?: uiState.currentPosition,
            duration = uiState.duration
        )

        NextEpisodeOverlay(
            nextEpisode = uiState.nextEpisode,
            currentPosition = uiState.currentPosition,
            duration = uiState.duration,
            onPlayNext = { uiState.nextEpisode?.let { viewModel.playEpisode(it.id, it.name) } },
            autoFocus = true,
            returnFocus = rootFocus
        )

        if (switching) {
            PlayerSwitchingOverlay(message = "Switching to MPV")
        }
    }

    when (panel) {
        MpvPanel.Subtitles -> TvOptionsPanel(
            title = "Subtitles",
            options = buildList {
                add(TvOption("Off", selected = uiState.subtitleTracks.none { it.isSelected }) {
                    viewModel.selectSubtitleTrack(-1); panel = null
                })
                uiState.subtitleTracks.forEach { t ->
                    add(TvOption(t.name, t.language, t.isSelected) { viewModel.selectSubtitleTrack(t.index); panel = null })
                }
            },
            onDismiss = { panel = null }
        )
        MpvPanel.Audio -> TvOptionsPanel(
            title = "Audio",
            options = uiState.audioTracks.map { t ->
                TvOption(t.name, t.language, t.isSelected) { viewModel.selectAudioTrack(t.index); panel = null }
            },
            onDismiss = { panel = null }
        )
        MpvPanel.Resize -> TvOptionsPanel(
            title = "Resize Mode",
            options = RESIZE_MODES_MPV.map { (v, label) ->
                TvOption(label, selected = uiState.resizeMode == v) { viewModel.setResizeMode(v); panel = null }
            },
            onDismiss = { panel = null }
        )
        MpvPanel.Speed -> TvOptionsPanel(
            title = "Playback Speed",
            options = SPEEDS_MPV.map { s ->
                TvOption("${s}x", selected = kotlin.math.abs(uiState.playbackSpeed - s) < 0.01f) { viewModel.setPlaybackSpeed(s); panel = null }
            },
            onDismiss = { panel = null }
        )
        MpvPanel.Decoder -> TvOptionsPanel(
            title = "Decoder",
            options = DECODERS_MPV.map { (v, label) ->
                TvOption(label, selected = uiState.decoderMode == v) { viewModel.setDecoderMode(v); panel = null }
            },
            onDismiss = { panel = null }
        )
        MpvPanel.Episodes -> TvOptionsPanel(
            title = "Episodes",
            options = uiState.episodeList.map { ep ->
                TvOption(ep.name, selected = ep.name == uiState.fileName) { viewModel.playEpisode(ep.id, ep.name); panel = null }
            },
            onDismiss = { panel = null }
        )
        MpvPanel.SubtitleStyle -> TvSubtitleStylePanel(
            fontSize = uiState.subtitleFontSize,
            onFontSize = viewModel::setSubtitleFontSize,
            color = uiState.subtitleColor,
            onColor = viewModel::setSubtitleColor,
            position = uiState.subtitlePosition,
            onPosition = viewModel::setSubtitlePosition,
            bgOpacity = uiState.subtitleBgOpacity,
            onBgOpacity = viewModel::setSubtitleBgOpacity,
            scale = uiState.subtitleScale,
            onScale = viewModel::setSubtitleScale,
            onDismiss = { panel = null }
        )
        null -> Unit
    }
}
