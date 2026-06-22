package com.mkbhdana.streamhive.player.ui

import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.viewinterop.AndroidView
import com.mkbhdana.streamhive.util.FileUtils
import java.util.Locale
import kotlin.math.abs

@Composable
fun HideBottomSheetSystemUI() {
    AndroidView(
        factory = { ctx ->
            object : android.view.View(ctx) {
                override fun onAttachedToWindow() {
                    super.onAttachedToWindow()
                    var parentView = this.parent
                    var dialogWindow: android.view.Window? = null
                    while (parentView != null) {
                        if (parentView is DialogWindowProvider) {
                            dialogWindow = parentView.window
                            break
                        }
                        parentView = parentView.parent
                    }
                    dialogWindow?.let { win ->
                        WindowCompat.setDecorFitsSystemWindows(win, false)
                        WindowInsetsControllerCompat(win, win.decorView).apply {
                            hide(WindowInsetsCompat.Type.systemBars())
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }
                }
            }
        },
        modifier = androidx.compose.ui.Modifier.size(0.dp)
    )
}

enum class TrackType {
    AUDIO,
    SUBTITLE
}

data class TrackInfo(
    val index: Int,
    val trackIndex: Int = 0,
    val type: TrackType = TrackType.AUDIO,
    val name: String,
    val language: String? = null,
    val codec: String? = null,
    val isSelected: Boolean = false,
    val isExternal: Boolean = false,
    val canRemove: Boolean = false,
    val sourceId: String? = null
)

@Composable
fun PlayerControlsOverlay(
    fileName: String,
    engineLabel: String,
    engineColor: Color,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPercentage: Int = 0,
    isLocked: Boolean,
    currentResizeMode: String,
    playbackSpeed: Float,
    subtitleDelay: Long,
    subtitleSpeed: Float = 1.0f,
    hapticFeedbackEnabled: Boolean = true,
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,

    decoderMode: String = "hw+",
    decoderOptions: List<String> = listOf("hw", "hw+", "sw"),
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onLockToggle: () -> Unit,
    onResizeModeChange: (String) -> Unit,
    onDecoderModeChange: (String) -> Unit = {},
    onAudioTrackSelect: (TrackInfo) -> Unit,
    onSubtitleTrackSelect: (TrackInfo?) -> Unit,
    onSubtitleTrackRemove: (TrackInfo) -> Unit = {},
    onSubtitleDelayChange: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLoadExternalSubtitle: () -> Unit,
    subtitleFontSize: Int = 18,
    subtitleColor: Long = 0xFFFFFFFF,
    subtitlePosition: Int = 90,
    subtitleOutlineColor: Long = 0xFF000000,
    subtitleBgOpacity: Float = 0f,
    subtitleEdgeSize: Int = 0,
    overrideAssSubtitleStyles: Boolean = false,
    onSubtitleFontSizeChange: (Int) -> Unit = {},
    onSubtitleColorChange: (Long) -> Unit = {},
    onSubtitlePositionChange: (Int) -> Unit = {},
    onSubtitleOutlineColorChange: (Long) -> Unit = {},
    onSubtitleBgOpacityChange: (Float) -> Unit = {},
    onSubtitleEdgeSizeChange: (Int) -> Unit = {},
    onOverrideAssSubtitleStylesChange: (Boolean) -> Unit = {},
    onSubtitleSpeedChange: (Float) -> Unit = {},
    onSubtitleStyleReset: () -> Unit = {},
    subtitleScale: Float = 1.0f,
    subtitleBold: Boolean = false,
    subtitleItalic: Boolean = false,
    subtitleAlignment: String = "center",
    onSubtitleScaleChange: (Float) -> Unit = {},
    onSubtitleBoldChange: (Boolean) -> Unit = {},
    onSubtitleItalicChange: (Boolean) -> Unit = {},
    onSubtitleAlignmentChange: (String) -> Unit = {},
    switchPlayerLabel: String? = null,
    onSwitchPlayer: (() -> Unit)? = null,

    onOpenExternal: () -> Unit = {},
    episodeList: List<com.mkbhdana.streamhive.data.db.MediaFileEntity> = emptyList(),
    onEpisodeSelect: (String, String) -> Unit = { _, _ -> },
    onPanelOpened: () -> Unit = {},
    onPanelClosed: () -> Unit = {},
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Local seek state to prevent seekbar stutter
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var pendingSeekPosition by remember { mutableStateOf<Long?>(null) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSubtitleStyleSidebar by remember { mutableStateOf(false) }
    var showSubtitleDelaySidebar by remember { mutableStateOf(false) }
    var showSpeedSelector by remember { mutableStateOf(false) }

    var showDecoderSelector by remember { mutableStateOf(false) }
    var showEpisodeList by remember { mutableStateOf(false) }
    var showResizePill by remember { mutableStateOf(false) }
    var resizePillText by remember { mutableStateOf("") }

    LaunchedEffect(showResizePill) {
        if (showResizePill) {
            kotlinx.coroutines.delay(1000)
            showResizePill = false
        }
    }

    // Hold pendingSeekPosition until player actually catches up (with a minimum hold time)
    LaunchedEffect(pendingSeekPosition) {
        val pending = pendingSeekPosition ?: return@LaunchedEffect
        // Give the player time to start seeking before checking
        kotlinx.coroutines.delay(500)
        // Then wait for the player position to settle near the target
        kotlinx.coroutines.withTimeoutOrNull(5000) {
            while (abs(currentPosition - pending) > SEEK_POSITION_SETTLE_TOLERANCE_MS) {
                kotlinx.coroutines.delay(100)
            }
        }
        pendingSeekPosition = null
    }

    // Track when any panel is open and notify parent
    val isPanelOpen = showAudioSheet || showSubtitleSheet || showSubtitleStyleSidebar ||
        showSubtitleDelaySidebar || showSpeedSelector || showDecoderSelector || showEpisodeList
    LaunchedEffect(isPanelOpen) {
        if (isPanelOpen) onPanelOpened() else onPanelClosed()
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Lock mode: only show unlock button
    if (isLocked) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(
                onClick = onLockToggle,
                modifier = Modifier
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.LockOpen, "Unlock", tint = Color.White)
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Top gradient + title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp, top = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))

                // File Name Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = fileName,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))

                // Top Right Icons Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Decoder Text Button
                    TextButton(onClick = { showDecoderSelector = true }) {
                        Text(decoderMode.uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    if (episodeList.size > 1) {
                        ControlIconButton(Icons.Default.VideoLibrary, "Episodes") { showEpisodeList = true }
                    }
                    ControlIconButton(Icons.Default.Audiotrack, "Audio") { showAudioSheet = true }
                    ControlIconButton(Icons.Default.Subtitles, "Sub") { showSubtitleSheet = true }

                }
            }
        }

        // Center play controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSeekBackward,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    Icons.Default.Replay10, "Rewind 10s",
                    tint = Color.White, modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            IconButton(
                onClick = onSeekForward,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    Icons.Default.Forward10, "Forward 10s",
                    tint = Color.White, modifier = Modifier.size(36.dp)
                )
            }
        }

        // Bottom area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                // Touching the bottom controls (seekbar / buttons) resets the parent's
                // auto-hide timer. Scoped to this cluster so it never blocks the gesture
                // layer in the rest of the screen.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onUserInteraction()
                    }
                }
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(6.dp)
                .padding(horizontal = 8.dp)
                .padding(bottom = 4.dp)
        ) {
            // Control buttons row (split left and right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Group
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ControlIconButton(Icons.Default.Lock, "Lock", true) { onLockToggle() }
                    ControlIconButton(Icons.Default.Speed, "Speed", true) { showSpeedSelector = true }

                }
                
                // Right Group
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlIconButton(Icons.AutoMirrored.Filled.OpenInNew, "Ext", true) { onOpenExternal() }
                    if (switchPlayerLabel != null && onSwitchPlayer != null) {
                        PlayerSwitchButton(
                            label = switchPlayerLabel,
                            color = engineColor,
                            onClick = onSwitchPlayer
                        )
                    }
                    
                    val resizeIcon = when (currentResizeMode) {
                        "fill" -> Icons.Default.Fullscreen
                        "zoom" -> Icons.Default.ZoomIn
                        "16:9" -> Icons.Default.Crop169
                        "4:3" -> Icons.Default.Crop54
                        else -> Icons.Default.AspectRatio
                    }
                    ControlIconButton(resizeIcon, "Resize", true) {
                        val modes = listOf("fit", "fill", "zoom", "16:9", "4:3")
                        val nextMode = modes[(modes.indexOf(currentResizeMode) + 1) % modes.size]
                        onResizeModeChange(nextMode)
                        resizePillText = if (nextMode == "zoom") "Crop" else nextMode.replaceFirstChar { it.uppercase() }
                        showResizePill = true
                    }
                }
            }

            // Inline Seekbar Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayPosition = when {
                    isSeeking -> (seekFraction * duration).toLong()
                    pendingSeekPosition != null -> pendingSeekPosition ?: currentPosition
                    else -> currentPosition
                }.coerceIn(0L, duration.coerceAtLeast(0L))

                // Current Time
                Text(
                    text = FileUtils.formatDuration(displayPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                
                // Seekbar Box to overlay markers on custom seekbar
                Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    // Direct fraction — no animation. Seekbar responds instantly.
                    val seekbarFraction = when {
                        isSeeking -> seekFraction
                        pendingSeekPosition != null -> if (duration > 0) (pendingSeekPosition ?: 0L).toFloat() / duration.toFloat() else 0f
                        else -> if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                    }

                    CustomSeekbar(
                        fraction = seekbarFraction,
                        bufferedFraction = if (duration > 0) bufferedPercentage / 100f else 0f,
                        duration = duration,

                        onSeek = { fraction ->
                            isSeeking = true
                            seekFraction = fraction
                        },
                        onSeekFinished = {
                            val targetPosition = (seekFraction * duration).toLong()
                                .coerceIn(0L, duration.coerceAtLeast(0L))
                            pendingSeekPosition = targetPosition
                            onSeekTo(targetPosition)
                            isSeeking = false
                        },
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                
                // Total Time
                Text(
                    text = FileUtils.formatDuration(duration),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        
        // Resize Mode Pill
        AnimatedVisibility(
            visible = showResizePill,
            enter = fadeIn() + slideInVertically(initialOffsetY = { 20 }),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = resizePillText,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(
            visible = showSubtitleStyleSidebar,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SubtitleStyleSidebar(
                isMpv = engineLabel == "MPV" || engineLabel.lowercase().contains("mpv"),
                subtitleFontSize = subtitleFontSize,
                subtitleColor = subtitleColor,
                subtitlePosition = subtitlePosition,
                subtitleBgOpacity = subtitleBgOpacity,
                overrideAssSubtitleStyles = overrideAssSubtitleStyles,
                subtitleScale = subtitleScale,
                subtitleBold = subtitleBold,
                subtitleItalic = subtitleItalic,
                subtitleAlignment = subtitleAlignment,
                onSubtitleFontSizeChange = onSubtitleFontSizeChange,
                onSubtitleColorChange = onSubtitleColorChange,
                onSubtitlePositionChange = onSubtitlePositionChange,
                onSubtitleBgOpacityChange = onSubtitleBgOpacityChange,
                onOverrideAssSubtitleStylesChange = onOverrideAssSubtitleStylesChange,
                onSubtitleScaleChange = onSubtitleScaleChange,
                onSubtitleBoldChange = onSubtitleBoldChange,
                onSubtitleItalicChange = onSubtitleItalicChange,
                onSubtitleAlignmentChange = onSubtitleAlignmentChange,
                hapticsEnabled = hapticFeedbackEnabled,
                onReset = onSubtitleStyleReset,
                onDismiss = { showSubtitleStyleSidebar = false }
            )
        }

        AnimatedVisibility(
            visible = showSubtitleDelaySidebar,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            SubtitleDelaySidebar(
                currentPosition = currentPosition,
                currentDelay = subtitleDelay,
                subtitleSpeed = subtitleSpeed,
                onDelayChange = onSubtitleDelayChange,
                onSubtitleSpeedChange = onSubtitleSpeedChange,
                onDismiss = { showSubtitleDelaySidebar = false }
            )
        }

        // Episode List Sidebar
        AnimatedVisibility(
            visible = showEpisodeList,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Episodes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showEpisodeList = false }) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                    HorizontalDivider()
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(episodeList.size) { index ->
                            val episode = episodeList[index]
                            val isPlaying = episode.name == fileName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showEpisodeList = false
                                        onEpisodeSelect(episode.id, episode.name)
                                    }
                                    .background(if (isPlaying) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isPlaying) {
                                    Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = episode.name,
                                    color = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Bottom sheets / dialogs ──

    if (showAudioSheet) {
        TrackSelectionSheet(
            title = "Audio Tracks",
            tracks = audioTracks,
            onSelect = { track ->
                track?.let(onAudioTrackSelect)
                showAudioSheet = false
            },
            onDismiss = { showAudioSheet = false }
        )
    }

    if (showSubtitleSheet) {
        SubtitleSelectionSheet(
            tracks = subtitleTracks,
            onSelect = { track ->
                onSubtitleTrackSelect(track)
                showSubtitleSheet = false
            },
            onRemoveExternal = onSubtitleTrackRemove,
            onAddExternal = {
                onLoadExternalSubtitle()
                showSubtitleSheet = false
            },
            onOpenStyle = {
                showSubtitleSheet = false
                showSubtitleStyleSidebar = true
                showSubtitleDelaySidebar = false
            },
            onOpenDelay = {
                showSubtitleSheet = false
                showSubtitleDelaySidebar = true
                showSubtitleStyleSidebar = false
            },
            onDismiss = { showSubtitleSheet = false }
        )
    }

    if (showSpeedSelector) {
        SpeedSelector(
            currentSpeed = playbackSpeed,
            onSelect = { onSpeedChange(it); showSpeedSelector = false },
            onDismiss = { showSpeedSelector = false }
        )
    }

    if (showDecoderSelector) {
        DecoderSelector(
            currentMode = decoderMode,
            modes = decoderOptions,
            onSelect = { onDecoderModeChange(it); showDecoderSelector = false },
            onDismiss = { showDecoderSelector = false }
        )
    }


}

// ──── Custom Seekbar ────

private const val SEEK_POSITION_SETTLE_TOLERANCE_MS = 1_000L

@Composable
private fun CustomSeekbar(
    fraction: Float,
    bufferedFraction: Float,
    duration: Long = 0L,

    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var dragX by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .height(28.dp)
            .graphicsLayer { alpha = 0.99f } // Required for BlendMode.Clear to only clear the canvas layer
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragX = offset.x
                        onSeek((dragX / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onSeekFinished() },
                    onDragCancel = { onSeekFinished() },
                    onHorizontalDrag = { _, dragAmount ->
                        dragX = (dragX + dragAmount).coerceIn(0f, size.width.toFloat())
                        onSeek((dragX / size.width).coerceIn(0f, 1f))
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newFraction)
                    onSeekFinished()
                }
            }
    ) {
        val trackHeight = 12.dp.toPx()
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
        val trackY = (size.height - trackHeight) / 2
        val activeWidth = fraction * size.width
        val bufferedWidth = bufferedFraction * size.width

        // Inactive background (full pill track)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.15f),
            topLeft = Offset(0f, trackY),
            size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
            cornerRadius = cornerRadius
        )

        // Buffered background
        if (bufferedWidth > 0f) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(0f, trackY),
                size = androidx.compose.ui.geometry.Size(bufferedWidth, trackHeight),
                cornerRadius = cornerRadius
            )
        }

        // Active track
        if (activeWidth > 0f) {
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(0f, trackY),
                size = androidx.compose.ui.geometry.Size(activeWidth, trackHeight),
                cornerRadius = cornerRadius
            )
        }



        // Thumb (Vertical Pill)
        val thumbWidth = 8.dp.toPx()
        val thumbHeight = 18.dp.toPx()
        val thumbX = activeWidth.coerceIn(thumbWidth / 2, size.width - thumbWidth / 2)
        val thumbY = (size.height - thumbHeight) / 2

        // Thumb glow
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.25f),
            topLeft = Offset(thumbX - thumbWidth * 1.5f / 2, (size.height - thumbHeight * 1.5f) / 2),
            size = androidx.compose.ui.geometry.Size(thumbWidth * 1.5f, thumbHeight * 1.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth * 1.5f / 2)
        )
        // Thumb solid
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(thumbX - thumbWidth / 2, thumbY),
            size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth / 2)
        )
    }
}

// ──── Control Icon Button ────

@Composable
private fun ControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    hasBackground: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .then(
                if (hasBackground) Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                else Modifier
            )
    ) {
        Icon(
            icon, contentDescription,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PlayerSwitchButton(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(50)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleSelectionSheet(
    tracks: List<TrackInfo>,
    onSelect: (TrackInfo?) -> Unit,
    onRemoveExternal: (TrackInfo) -> Unit,
    onAddExternal: () -> Unit,
    onOpenStyle: () -> Unit,
    onOpenDelay: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
    val embeddedTracks = tracks.filterNot { it.isExternal }
    val externalTracks = tracks.filter { it.isExternal }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        HideBottomSheetSystemUI()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 14.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subtitles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = onAddExternal,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("+ Add")
                }
                IconButton(onClick = onOpenStyle) {
                    Icon(Icons.Default.Palette, "Subtitle style")
                }
                IconButton(onClick = onOpenDelay) {
                    Icon(Icons.Default.MoreTime, "Subtitle delay")
                }
            }

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                item {
                    SubtitleSheetSectionTitle("Embedded")
                    SubtitleSheetTrackRow(
                        selected = tracks.none { it.isSelected },
                        title = "None",
                        subtitle = "Disabled",
                        onClick = { onSelect(null) }
                    )
                }

                if (embeddedTracks.isEmpty()) {
                    item {
                        Text(
                            text = "No embedded subtitles",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                } else {
                    items(embeddedTracks.size) { index ->
                        val track = embeddedTracks[index]
                        SubtitleSheetTrackRow(
                            selected = track.isSelected,
                            title = track.name,
                            subtitle = trackSubtitle(track),
                            onClick = { onSelect(track) }
                        )
                    }
                }

                if (externalTracks.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SubtitleSheetSectionTitle("External")
                    }
                    items(externalTracks.size) { index ->
                        val track = externalTracks[index]
                        SubtitleSheetTrackRow(
                            selected = track.isSelected,
                            title = track.name,
                            subtitle = trackSubtitle(track),
                            onClick = { onSelect(track) },
                            trailing = {
                                IconButton(onClick = { onRemoveExternal(track) }) {
                                    Icon(Icons.Default.Delete, "Remove external subtitle")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleSheetSectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun SubtitleSheetTrackRow(
    selected: Boolean,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
        if (selected && trailing == null) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SubtitleStyleSidebar(
    isMpv: Boolean,
    subtitleFontSize: Int,
    subtitleColor: Long,
    subtitlePosition: Int,
    subtitleBgOpacity: Float,
    overrideAssSubtitleStyles: Boolean,
    subtitleScale: Float,
    subtitleBold: Boolean,
    subtitleItalic: Boolean,
    subtitleAlignment: String,
    onSubtitleFontSizeChange: (Int) -> Unit,
    onSubtitleColorChange: (Long) -> Unit,
    onSubtitlePositionChange: (Int) -> Unit,
    onSubtitleBgOpacityChange: (Float) -> Unit,
    onOverrideAssSubtitleStylesChange: (Boolean) -> Unit,
    onSubtitleScaleChange: (Float) -> Unit,
    onSubtitleBoldChange: (Boolean) -> Unit,
    onSubtitleItalicChange: (Boolean) -> Unit,
    onSubtitleAlignmentChange: (String) -> Unit,
    hapticsEnabled: Boolean,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val switchHapticView = LocalView.current

    Surface(
        modifier = Modifier
            .padding(end = 16.dp)
            .fillMaxHeight(0.86f)
            .widthIn(min = 300.dp, max = 430.dp)
            .clip(RoundedCornerShape(30.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SidebarHeader(title = "Subtitle Style", onDismiss = onDismiss)
            SidebarSlider(
                title = "Font Size",
                valueText = "$subtitleFontSize sp",
                value = subtitleFontSize.toFloat(),
                valueRange = 10f..48f,
                onValueChange = { onSubtitleFontSizeChange(it.toInt()) },
                hapticsEnabled = hapticsEnabled
            )
            if (isMpv) {
                SidebarSlider(
                    title = "Scale (MPV Only)",
                    valueText = String.format("%.1fx", subtitleScale),
                    value = subtitleScale,
                    valueRange = 0.5f..3.0f,
                    onValueChange = onSubtitleScaleChange,
                    hapticsEnabled = hapticsEnabled
                )

                // Bold / Italic row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = subtitleBold,
                        onClick = { onSubtitleBoldChange(!subtitleBold) },
                        label = { Text("Bold", fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = subtitleItalic,
                        onClick = { onSubtitleItalicChange(!subtitleItalic) },
                        label = { Text("Italic", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic) },
                        leadingIcon = {
                            Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Alignment row
                Column {
                    Text(
                        text = "Alignment (MPV Only)",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "left" to Icons.AutoMirrored.Filled.FormatAlignLeft,
                            "center" to Icons.Default.FormatAlignCenter,
                            "right" to Icons.AutoMirrored.Filled.FormatAlignRight
                        ).forEach { (key, icon) ->
                            FilterChip(
                                selected = subtitleAlignment == key,
                                onClick = { onSubtitleAlignmentChange(key) },
                                label = { Icon(icon, contentDescription = key, modifier = Modifier.size(20.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            ColorChoiceRow(
                title = "Subtitle Color",
                selectedColor = subtitleColor,
                onSelect = onSubtitleColorChange
            )
            SidebarSlider(
                title = "Position",
                valueText = "$subtitlePosition",
                value = subtitlePosition.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { onSubtitlePositionChange(it.toInt()) },
                hapticsEnabled = hapticsEnabled
            )
            SidebarSlider(
                title = "Background",
                valueText = "${(subtitleBgOpacity * 100).toInt()}%",
                value = subtitleBgOpacity,
                valueRange = 0f..1f,
                onValueChange = onSubtitleBgOpacityChange,
                hapticsEnabled = hapticsEnabled
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Override ASS/SSA",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = overrideAssSubtitleStyles,
                    onCheckedChange = { enabled ->
                        if (hapticsEnabled) switchHapticView.performSwitchHaptic()
                        onOverrideAssSubtitleStylesChange(enabled)
                    }
                )
            }
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleDelaySidebar(
    currentPosition: Long,
    currentDelay: Long,
    subtitleSpeed: Float,
    onDelayChange: (Long) -> Unit,
    onSubtitleSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var manualDelay by remember(currentDelay) { mutableStateOf(formatSeconds(currentDelay / 1000f)) }
    var manualSpeed by remember(subtitleSpeed) { mutableStateOf(formatSpeed(subtitleSpeed)) }
    var voiceHeardAt by remember { mutableStateOf<Long?>(null) }
    var textSeenAt by remember { mutableStateOf<Long?>(null) }

    fun updateDelayFromManual() {
        manualDelay.toFloatOrNull()?.let { seconds ->
            onDelayChange((seconds * 1000).toLong())
        }
    }

    fun updateSpeedFromManual() {
        manualSpeed.toFloatOrNull()?.let { speed ->
            onSubtitleSpeedChange(speed)
        }
    }

    fun markVoiceHeard() {
        voiceHeardAt = currentPosition
        val textAt = textSeenAt
        if (textAt != null) {
            onDelayChange(currentDelay + (currentPosition - textAt))
            voiceHeardAt = null
            textSeenAt = null
        }
    }

    fun markTextSeen() {
        textSeenAt = currentPosition
        val voiceAt = voiceHeardAt
        if (voiceAt != null) {
            onDelayChange(currentDelay + (voiceAt - currentPosition))
            voiceHeardAt = null
            textSeenAt = null
        }
    }

    fun resetDelay() {
        voiceHeardAt = null
        textSeenAt = null
        onDelayChange(0L)
        onSubtitleSpeedChange(1.0f)
    }

    Surface(
        modifier = Modifier
            .padding(end = 16.dp)
            .fillMaxHeight(0.86f)
            .widthIn(min = 320.dp, max = 500.dp)
            .clip(RoundedCornerShape(30.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SidebarHeader(title = "Subtitle delay", onDismiss = onDismiss)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(-500L, -100L, -50L, 50L, 100L, 500L).forEach { delta ->
                    FilterChip(
                        selected = false,
                        onClick = { onDelayChange(currentDelay + delta) },
                        label = { Text(formatDelta(delta)) }
                    )
                }
            }
            StepperInputRow(
                label = "Delay",
                value = manualDelay,
                suffix = "s",
                onValueChange = { value ->
                    manualDelay = sanitizeDecimalInput(value)
                },
                onDecrease = {
                    onDelayChange(currentDelay - 100L)
                },
                onIncrease = {
                    onDelayChange(currentDelay + 100L)
                },
                onApply = ::updateDelayFromManual
            )
            StepperInputRow(
                label = "Speed",
                value = manualSpeed,
                suffix = null,
                onValueChange = { value ->
                    manualSpeed = sanitizeDecimalInput(value, allowNegative = false)
                },
                onDecrease = {
                    onSubtitleSpeedChange((subtitleSpeed - 0.05f).coerceIn(0.25f, 4.0f))
                },
                onIncrease = {
                    onSubtitleSpeedChange((subtitleSpeed + 0.05f).coerceIn(0.25f, 4.0f))
                },
                onApply = ::updateSpeedFromManual
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 50.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = ::markVoiceHeard,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Voice heard")
                }
                Button(
                    onClick = ::markTextSeen,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Text seen")
                }
            }
            Button(
                onClick = ::resetDelay,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset")
            }
        }
    }
}

@Composable
private fun SidebarHeader(
    title: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorChoiceRow(
    title: String,
    selectedColor: Long,
    onSelect: (Long) -> Unit
) {
    val colors = listOf(
        0xFFFFFFFF,
        0xFFFFEB3B,
        0xFFFFB5C5,
        0xFF80DEEA,
        0xFFA5D6A7,
        0xFFFFAB91,
        0xFF000000
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            colors.forEach { colorValue ->
                val selected = selectedColor == colorValue
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(colorValue))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { onSelect(colorValue) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = if (colorValue == 0xFF000000) Color.White else Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepperInputRow(
    label: String,
    value: String,
    suffix: String?,
    onValueChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onApply: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RoundStepperButton(Icons.Default.Remove, "Decrease $label", onDecrease)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) onApply()
                }
        )
        if (suffix != null) {
            Text(suffix, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        RoundStepperButton(Icons.Default.Add, "Increase $label", onIncrease)
    }
}

@Composable
private fun RoundStepperButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun HapticSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    hapticsEnabled: Boolean = true
) {
    val view = LocalView.current
    var lastStep by remember(valueRange.start, valueRange.endInclusive, steps) {
        mutableIntStateOf(sliderHapticStep(value, valueRange, steps))
    }

    Slider(
        value = value,
        onValueChange = { newValue ->
            val nextStep = sliderHapticStep(newValue, valueRange, steps)
            if (hapticsEnabled && nextStep != lastStep) {
                view.performSliderHaptic()
                lastStep = nextStep
            } else if (!hapticsEnabled) {
                lastStep = nextStep
            }
            onValueChange(newValue)
        },
        valueRange = valueRange,
        steps = steps,
        modifier = modifier
    )
}

private fun View.performSliderHaptic() {
    isHapticFeedbackEnabled = true
    val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    if (!performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, flags)) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, flags)
    }
}

private fun View.performSwitchHaptic() {
    isHapticFeedbackEnabled = true
    performHapticFeedback(
        HapticFeedbackConstants.VIRTUAL_KEY,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
    )
}

private fun sliderHapticStep(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
): Int {
    val rangeSize = valueRange.endInclusive - valueRange.start
    if (rangeSize <= 0f) return 0
    val intervals = if (steps > 0) steps + 1 else 20
    val fraction = ((value - valueRange.start) / rangeSize).coerceIn(0f, 1f)
    return (fraction * intervals).toInt()
}

@Composable
private fun SidebarSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    hapticsEnabled: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(valueText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        HapticSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            hapticsEnabled = hapticsEnabled
        )
    }
}

private fun trackSubtitle(track: TrackInfo): String? {
    return listOfNotNull(
        track.language?.takeIf { it.isNotBlank() },
        track.codec?.takeIf { it.isNotBlank() }
    ).joinToString(" / ").takeIf { it.isNotBlank() }
}

private fun formatDelta(deltaMs: Long): String {
    return "${if (deltaMs > 0) "+" else ""}${deltaMs}ms"
}

private fun formatSeconds(seconds: Float): String {
    return String.format(Locale.US, "%.1f", seconds)
}

private fun formatSpeed(speed: Float): String {
    return String.format(Locale.US, "%.2f", speed)
}

private fun sanitizeDecimalInput(value: String, allowNegative: Boolean = true): String {
    val builder = StringBuilder()
    var hasDecimal = false
    value.forEachIndexed { index, char ->
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !hasDecimal -> {
                builder.append(char)
                hasDecimal = true
            }
            char == '-' && allowNegative && index == 0 -> builder.append(char)
        }
    }
    return builder.toString()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SpeedSelector(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        HideBottomSheetSystemUI()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Playback Speed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                speeds.forEach { speed ->
                    FilterChip(
                        selected = currentSpeed == speed,
                        onClick = { onSelect(speed) },
                        label = { Text(if (speed == 1.0f) "Normal" else "${speed}x") }
                    )
                }
            }
        }
    }
}

// ──── Decoder Selector ────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecoderSelector(
    currentMode: String,
    modes: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        HideBottomSheetSystemUI()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Hardware Decoder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            modes.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(mode) }
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == mode,
                        onClick = { onSelect(mode) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = mode.uppercase(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
