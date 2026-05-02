package com.mkbhdana.streamhive.player.ui

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.viewinterop.AndroidView
import com.mkbhdana.streamhive.util.FileUtils

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
    val isSelected: Boolean = false
)

data class ChapterInfo(
    val title: String,
    val startMs: Long,
    val endMs: Long = 0L
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
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    chapters: List<ChapterInfo> = emptyList(),
    decoderMode: String = "hw+",
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
    onSubtitleDelayChange: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLoadExternalSubtitle: () -> Unit,
    switchPlayerLabel: String? = null,
    onSwitchPlayer: (() -> Unit)? = null,
    onChapterNext: () -> Unit = {},
    onChapterPrevious: () -> Unit = {},
    onChapterSelect: (Int) -> Unit = {},
    onOpenExternal: () -> Unit = {},
    episodeList: List<com.mkbhdana.streamhive.data.db.MediaFileEntity> = emptyList(),
    onEpisodeSelect: (String, String) -> Unit = { _, _ -> },
    onPanelOpened: () -> Unit = {},
    onPanelClosed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Local seek state to prevent seekbar stutter
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSpeedSelector by remember { mutableStateOf(false) }
    var showSubtitleDelay by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
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

    // Track when any panel is open and notify parent
    val isPanelOpen = showAudioSheet || showSubtitleSheet || showSpeedSelector || showSubtitleDelay || showChapterList || showDecoderSelector || showEpisodeList
    LaunchedEffect(isPanelOpen) {
        if (isPanelOpen) onPanelOpened() else onPanelClosed()
    }

    // Current chapter name
    val currentChapter = remember(chapters, currentPosition) {
        chapters.lastOrNull { it.startMs <= currentPosition }
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
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
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
                    ControlIconButton(Icons.Default.Bookmarks, "Chapters") {
                        if (chapters.isNotEmpty()) showChapterList = true
                        else Toast.makeText(context, "No chapters found", Toast.LENGTH_SHORT).show()
                    }
                    ControlIconButton(Icons.Default.MoreVert, "More") { showSubtitleDelay = true }
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
                    if (chapters.isNotEmpty()) {
                        ControlIconButton(Icons.Default.SkipPrevious, "Prev", true) { onChapterPrevious() }
                        ControlIconButton(Icons.Default.SkipNext, "Next", true) { onChapterNext() }
                    }
                }
                
                // Right Group
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlIconButton(Icons.Default.OpenInNew, "Ext", true) { onOpenExternal() }
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
                // Current Time
                Text(
                    text = FileUtils.formatDuration(if (isSeeking) (seekFraction * duration).toLong() else currentPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                
                // Seekbar Box to overlay markers on custom seekbar
                Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    val displayFraction = if (isSeeking) seekFraction
                        else if (duration > 0) currentPosition.toFloat() / duration.toFloat()
                        else 0f

                    val animatedFraction by animateFloatAsState(
                        targetValue = displayFraction,
                        animationSpec = androidx.compose.animation.core.tween(150),
                        label = "seekbar"
                    )

                    CustomSeekbar(
                        fraction = if (isSeeking) seekFraction else animatedFraction,
                        bufferedFraction = if (duration > 0) bufferedPercentage / 100f else 0f,
                        duration = duration,
                        chapters = chapters,
                        onSeek = { fraction ->
                            isSeeking = true
                            seekFraction = fraction
                        },
                        onSeekFinished = {
                            onSeekTo((seekFraction * duration).toLong())
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
        TrackSelectionSheet(
            title = "Subtitles",
            tracks = subtitleTracks,
            onSelect = { track ->
                onSubtitleTrackSelect(track)
                showSubtitleSheet = false
            },
            onDismiss = { showSubtitleSheet = false },
            showExternalOption = true,
            onLoadExternal = { onLoadExternalSubtitle(); showSubtitleSheet = false }
        )
    }

    if (showSpeedSelector) {
        SpeedSelector(
            currentSpeed = playbackSpeed,
            onSelect = { onSpeedChange(it); showSpeedSelector = false },
            onDismiss = { showSpeedSelector = false }
        )
    }

    if (showSubtitleDelay) {
        SubtitleDelaySheet(
            currentDelay = subtitleDelay,
            onDelayChange = onSubtitleDelayChange,
            onDismiss = { showSubtitleDelay = false }
        )
    }

    if (showDecoderSelector) {
        DecoderSelector(
            currentMode = decoderMode,
            onSelect = { onDecoderModeChange(it); showDecoderSelector = false },
            onDismiss = { showDecoderSelector = false }
        )
    }

    if (showChapterList && chapters.isNotEmpty()) {
        ChapterListSheet(
            chapters = chapters,
            currentPosition = currentPosition,
            onChapterSelect = { index ->
                onChapterSelect(index)
                showChapterList = false
            },
            onDismiss = { showChapterList = false }
        )
    }
}

// ──── Custom Seekbar ────

@Composable
private fun CustomSeekbar(
    fraction: Float,
    bufferedFraction: Float,
    duration: Long = 0L,
    chapters: List<ChapterInfo> = emptyList(),
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

        // Cut chapter gaps
        if (duration > 0 && chapters.isNotEmpty()) {
            chapters.forEach { chapter ->
                val chFraction = chapter.startMs.toFloat() / duration.toFloat()
                if (chFraction > 0f && chFraction < 1f) {
                    val gapX = chFraction * size.width
                    drawLine(
                        color = Color.Transparent,
                        start = Offset(gapX, trackY),
                        end = Offset(gapX, trackY + trackHeight),
                        strokeWidth = 2.dp.toPx(),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                    )
                }
            }
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
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = listOf("hw", "hw+", "sw")
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
