package com.driveplay.app.player.ui

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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driveplay.app.util.FileUtils

data class TrackInfo(
    val index: Int,
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
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onLockToggle: () -> Unit,
    onResizeModeChange: (String) -> Unit,
    onAudioTrackSelect: (Int) -> Unit,
    onSubtitleTrackSelect: (Int) -> Unit,
    onSubtitleDelayChange: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLoadExternalSubtitle: () -> Unit,
    onChapterNext: () -> Unit = {},
    onChapterPrevious: () -> Unit = {},
    onChapterSelect: (Int) -> Unit = {},
    onOpenExternal: () -> Unit = {},
    onPanelOpened: () -> Unit = {},
    onPanelClosed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Local seek state to prevent seekbar stutter
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showResizeSelector by remember { mutableStateOf(false) }
    var showSpeedSelector by remember { mutableStateOf(false) }
    var showSubtitleDelay by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }

    // Track when any panel is open and notify parent
    val isPanelOpen = showAudioSheet || showSubtitleSheet || showResizeSelector || showSpeedSelector || showSubtitleDelay || showChapterList
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
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(4.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = fileName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = engineLabel,
                        color = engineColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.width(48.dp))
            }
        }

        // Center play controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSeekBackward,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    Icons.Default.Replay10, "Rewind 10s",
                    tint = Color.White, modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(
                onClick = onSeekForward,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    Icons.Default.Forward10, "Forward 10s",
                    tint = Color.White, modifier = Modifier.size(32.dp)
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
                .windowInsetsPadding(WindowInsets.displayCutout)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Chapter markers on seekbar
            if (chapters.isNotEmpty() && duration > 0) {
                val primaryColor = MaterialTheme.colorScheme.primary
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    chapters.forEach { chapter ->
                        val fraction = chapter.startMs.toFloat() / duration.toFloat()
                        val x = fraction * size.width
                        drawCircle(
                            color = primaryColor,
                            radius = 4f,
                            center = Offset(x, size.height / 2),
                            style = Fill
                        )
                    }
                }
            }

            // Wavy seekbar
            val displayFraction = if (isSeeking) seekFraction
                else if (duration > 0) currentPosition.toFloat() / duration.toFloat()
                else 0f

            val animatedFraction by animateFloatAsState(
                targetValue = displayFraction,
                animationSpec = androidx.compose.animation.core.tween(150),
                label = "seekbar"
            )

            WavySeekbar(
                fraction = if (isSeeking) seekFraction else animatedFraction,
                bufferedFraction = if (duration > 0) bufferedPercentage / 100f else 0f,
                onSeek = { fraction ->
                    isSeeking = true
                    seekFraction = fraction
                },
                onSeekFinished = {
                    onSeekTo((seekFraction * duration).toLong())
                    isSeeking = false
                },
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // Time row in capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = FileUtils.formatDuration(
                        if (isSeeking) (seekFraction * duration).toLong() else currentPosition
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                if (playbackSpeed != 1.0f) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = FileUtils.formatDuration(duration),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Control buttons row (icon-only, scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(2.dp))

                ControlIconButton(Icons.Default.Lock, "Lock") { onLockToggle() }
                ControlIconButton(Icons.Default.AspectRatio, currentResizeMode.uppercase()) { showResizeSelector = true }
                ControlIconButton(Icons.Default.Audiotrack, "Audio") { showAudioSheet = true }
                ControlIconButton(Icons.Default.Subtitles, "Sub") { showSubtitleSheet = true }
                ControlIconButton(Icons.Default.Timer, "Delay") { showSubtitleDelay = true }
                ControlIconButton(Icons.Default.Speed, "${playbackSpeed}x") { showSpeedSelector = true }
                ControlIconButton(Icons.Default.OpenInNew, "Ext") { onOpenExternal() }

                if (chapters.isNotEmpty()) {
                    ControlIconButton(Icons.Default.SkipPrevious, "Prev") { onChapterPrevious() }
                }
                ControlIconButton(Icons.Default.Bookmarks, "Ch") {
                    if (chapters.isNotEmpty()) {
                        showChapterList = true
                    } else {
                        Toast.makeText(context, "No chapters found", Toast.LENGTH_SHORT).show()
                    }
                }
                if (chapters.isNotEmpty()) {
                    ControlIconButton(Icons.Default.SkipNext, "Next") { onChapterNext() }
                }

                Spacer(Modifier.width(2.dp))
            }
        }
    }

    // ── Bottom sheets / dialogs ──

    if (showAudioSheet) {
        TrackSelectionSheet(
            title = "Audio Tracks",
            tracks = audioTracks,
            onSelect = { onAudioTrackSelect(it); showAudioSheet = false },
            onDismiss = { showAudioSheet = false }
        )
    }

    if (showSubtitleSheet) {
        TrackSelectionSheet(
            title = "Subtitles",
            tracks = subtitleTracks,
            onSelect = { onSubtitleTrackSelect(it); showSubtitleSheet = false },
            onDismiss = { showSubtitleSheet = false },
            showExternalOption = true,
            onLoadExternal = { onLoadExternalSubtitle(); showSubtitleSheet = false }
        )
    }

    if (showResizeSelector) {
        ResizeModeSelector(
            currentMode = currentResizeMode,
            onSelect = { onResizeModeChange(it); showResizeSelector = false },
            onDismiss = { showResizeSelector = false }
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

// ──── Wavy Seekbar ────

@Composable
private fun WavySeekbar(
    fraction: Float,
    bufferedFraction: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val wavePhase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // Smoothly transition amplitude: wavy when playing, straight when paused
    val targetAmplitude = if (isPlaying) 1f else 0f
    val waveAmplitudeFactor by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = androidx.compose.animation.core.tween(400),
        label = "amplitude"
    )

    var dragX by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .height(28.dp)
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
        val trackY = size.height / 2
        val trackHeight = 4.dp.toPx()
        val maxWaveAmplitude = 4.dp.toPx()
        val waveAmplitude = maxWaveAmplitude * waveAmplitudeFactor
        val waveFrequency = 0.06f
        val activeWidth = fraction * size.width
        val bufferedWidth = bufferedFraction * size.width

        // Inactive portion: very subtle, thin line after the active area
        val inactiveStartX = activeWidth
        if (inactiveStartX < size.width) {
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(inactiveStartX, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Buffered track (subtle, slightly brighter than inactive)
        if (bufferedWidth > activeWidth) {
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(activeWidth, trackY),
                end = Offset(bufferedWidth, trackY),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Active track: wavy/straight line
        if (activeWidth > 2f) {
            val wavePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, trackY)
                var x = 0f
                while (x <= activeWidth) {
                    val y = trackY + kotlin.math.sin((x * waveFrequency + wavePhase).toDouble()).toFloat() * waveAmplitude
                    lineTo(x, y)
                    x += 1.5f
                }
            }
            drawPath(
                path = wavePath,
                color = primaryColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = trackHeight,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }

        // Thumb
        val thumbRadius = 6.dp.toPx()
        val thumbX = activeWidth.coerceIn(thumbRadius, size.width - thumbRadius)
        val thumbY = if (activeWidth > 2f && waveAmplitude > 0.1f) {
            trackY + kotlin.math.sin((thumbX * waveFrequency + wavePhase).toDouble()).toFloat() * waveAmplitude
        } else trackY

        // Thumb glow
        drawCircle(
            color = primaryColor.copy(alpha = 0.25f),
            radius = thumbRadius * 1.5f,
            center = Offset(thumbX, thumbY)
        )
        // Thumb solid
        drawCircle(
            color = primaryColor,
            radius = thumbRadius,
            center = Offset(thumbX, thumbY)
        )
        // Inner dot
        drawCircle(
            color = Color.White,
            radius = thumbRadius * 0.35f,
            center = Offset(thumbX, thumbY)
        )
    }
}

// ──── Control Icon Button ────

@Composable
private fun ControlIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            icon, contentDescription,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(22.dp)
        )
    }
}

// ──── Speed Selector ────

@Composable
private fun SpeedSelector(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Speed") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(speed) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSpeed == speed,
                            onClick = { onSelect(speed) }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (speed == 1.0f) "Normal" else "${speed}x",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (currentSpeed == speed) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
