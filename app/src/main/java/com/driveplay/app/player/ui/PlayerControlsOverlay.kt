package com.driveplay.app.player.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    modifier: Modifier = Modifier
) {
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showResizeSelector by remember { mutableStateOf(false) }
    var showSpeedSelector by remember { mutableStateOf(false) }
    var showSubtitleDelay by remember { mutableStateOf(false) }

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
                Spacer(Modifier.width(48.dp)) // balance for back button space on right
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
                .padding(horizontal = 16.dp)
        ) {
            // Time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = FileUtils.formatDuration(currentPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                if (playbackSpeed != 1.0f) {
                    Text(
                        text = "${playbackSpeed}x",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = FileUtils.formatDuration(duration),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Seek slider
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { fraction ->
                    onSeekTo((fraction * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Control buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lock
                ControlButton(Icons.Default.Lock, "Lock") { onLockToggle() }

                // Resize
                ControlButton(Icons.Default.AspectRatio, currentResizeMode.uppercase()) {
                    showResizeSelector = true
                }

                // Audio
                ControlButton(Icons.Default.Audiotrack, "Audio") {
                    showAudioSheet = true
                }

                // Subtitle
                ControlButton(Icons.Default.Subtitles, "Sub") {
                    showSubtitleSheet = true
                }

                // Subtitle Delay
                ControlButton(Icons.Default.Timer, "${subtitleDelay}ms") {
                    showSubtitleDelay = true
                }

                // Speed
                ControlButton(Icons.Default.Speed, "${playbackSpeed}x") {
                    showSpeedSelector = true
                }
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
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
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
            Column {
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
