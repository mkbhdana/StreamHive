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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
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
    seekThumbnail: Bitmap? = null,
    decoderMode: String = "hw+",
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubbing: (Long) -> Unit = {},
    onScrubbingFinished: () -> Unit = {},
    onLockToggle: () -> Unit,
    onResizeModeChange: (String) -> Unit,
    onDecoderModeChange: (String) -> Unit = {},
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
    var showDecoderSelector by remember { mutableStateOf(false) }

    // Track when any panel is open and notify parent
    val isPanelOpen = showAudioSheet || showSubtitleSheet || showResizeSelector || showSpeedSelector || showSubtitleDelay || showChapterList || showDecoderSelector
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
                .padding(top = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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

                // Time Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${FileUtils.formatDuration(currentPosition)} • ${FileUtils.formatDuration(duration)}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
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
                .windowInsetsPadding(WindowInsets.displayCutout)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        ) {
            // Control buttons row (split left and right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
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
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ControlIconButton(Icons.Default.OpenInNew, "Ext", true) { onOpenExternal() }
                    ControlIconButton(Icons.Default.AspectRatio, "Resize", true) { showResizeSelector = true }
                }
            }

            // Thumbnail preview
            if (isSeeking && seekThumbnail != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    val previewWidth = 160.dp
                    val thumbXOffset = (seekFraction * LocalConfiguration.current.screenWidthDp).dp - (previewWidth / 2)
                    
                    Image(
                        bitmap = seekThumbnail.asImageBitmap(),
                        contentDescription = "Seek Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = previewWidth, height = 90.dp)
                            .offset(x = thumbXOffset.coerceIn(0.dp, LocalConfiguration.current.screenWidthDp.dp - previewWidth))
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    )
                }
            }

            // Inline Seekbar Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Current Time
                Text(
                    text = FileUtils.formatDuration(if (isSeeking) (seekFraction * duration).toLong() else currentPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(56.dp)
                )
                
                // Seekbar Box to overlay markers on wavy seekbar
                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
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
                        duration = duration,
                        chapters = chapters,
                        onSeek = { fraction ->
                            isSeeking = true
                            seekFraction = fraction
                            onScrubbing((fraction * duration).toLong())
                        },
                        onSeekFinished = {
                            onSeekTo((seekFraction * duration).toLong())
                            isSeeking = false
                            onScrubbingFinished()
                        },
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
                
                // Total Time
                Text(
                    text = FileUtils.formatDuration(duration),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(56.dp)
                )
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

// ──── Wavy Seekbar ────

@Composable
private fun WavySeekbar(
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
        val trackY = size.height / 2
        val trackHeight = 6.dp.toPx()
        val maxWaveAmplitude = 5.dp.toPx()
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
                strokeWidth = 4.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Buffered track (subtle, slightly brighter than inactive)
        if (bufferedWidth > activeWidth) {
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(activeWidth, trackY),
                end = Offset(bufferedWidth, trackY),
                strokeWidth = 4.dp.toPx(),
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

        // Cut chapter gaps
        if (duration > 0 && chapters.isNotEmpty()) {
            chapters.forEach { chapter ->
                val chFraction = chapter.startMs.toFloat() / duration.toFloat()
                if (chFraction > 0f && chFraction < 1f) {
                    val gapX = chFraction * size.width
                    drawLine(
                        color = Color.Transparent,
                        start = Offset(gapX, trackY - 12.dp.toPx()),
                        end = Offset(gapX, trackY + 12.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                    )
                }
            }
        }

        // Thumb (Vertical Pill)
        val thumbWidth = 6.dp.toPx()
        val thumbHeight = 20.dp.toPx()
        val thumbX = activeWidth.coerceIn(thumbWidth / 2, size.width - thumbWidth / 2)
        val thumbY = if (activeWidth > 2f && waveAmplitude > 0.1f) {
            trackY + kotlin.math.sin((thumbX * waveFrequency + wavePhase).toDouble()).toFloat() * waveAmplitude
        } else trackY

        // Thumb glow
        drawRoundRect(
            color = primaryColor.copy(alpha = 0.25f),
            topLeft = Offset(thumbX - thumbWidth * 1.5f / 2, thumbY - thumbHeight * 1.5f / 2),
            size = androidx.compose.ui.geometry.Size(thumbWidth * 1.5f, thumbHeight * 1.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth * 1.5f / 2)
        )
        // Thumb solid
        drawRoundRect(
            color = primaryColor,
            topLeft = Offset(thumbX - thumbWidth / 2, thumbY - thumbHeight / 2),
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

// ──── Decoder Selector ────

@Composable
private fun DecoderSelector(
    currentMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = listOf("hw", "hw+", "sw", "auto")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hardware Decoder") },
        text = {
            Column {
                modes.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = { onSelect(mode) }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = mode.uppercase(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Normal
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
