package com.mkbhdana.streamhive.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush as GfxBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.util.FileUtils

/**
 * Compact NuvioTV-style player overlay: title + a thin full-width seekbar with
 * step-up fast seek, and a single scrollable row of controls. While seeking, the
 * left (start) time label shows the seek target — there is no separate label
 * under the seekbar.
 */
@Composable
fun TvPlayerControls(
    visible: Boolean,
    fileName: String,
    sourceLabel: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    baseStepMs: Long,
    hasEpisodes: Boolean,
    hasNext: Boolean,
    canSwitchEngine: Boolean,
    seekFocusRequester: FocusRequester,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSubtitles: () -> Unit,
    onAudio: () -> Unit,
    onEpisodes: () -> Unit,
    onNext: () -> Unit,
    onSubtitleStyle: () -> Unit,
    onResize: () -> Unit,
    onSpeed: () -> Unit,
    onDecoder: () -> Unit,
    onSwitchEngine: () -> Unit,
    onExternal: () -> Unit,
    onInteraction: () -> Unit
) {
    var scrubPos by remember { mutableStateOf<Long?>(null) }
    // DOWN from the seekbar always lands on play/pause, not whichever control
    // happens to be geometrically closest.
    val playPauseFocus = remember { FocusRequester() }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GfxBrush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 28.dp)
            ) {
                Text(
                    fileName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(sourceLabel, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        FileUtils.formatDuration(scrubPos ?: currentPosition),
                        color = if (scrubPos != null) MaterialTheme.colorScheme.primary else Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(14.dp))
                    TvThinSeekBar(
                        modifier = Modifier.weight(1f),
                        currentPosition = currentPosition,
                        duration = duration,
                        baseStepMs = baseStepMs,
                        focusRequester = seekFocusRequester,
                        downFocus = playPauseFocus,
                        onSeekTo = onSeekTo,
                        onPlayPause = onPlayPause,
                        onScrubbingChange = { scrubPos = it },
                        onInteraction = onInteraction
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        FileUtils.formatDuration(duration),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvBigPlayButton(
                        isPlaying = isPlaying,
                        focusRequester = playPauseFocus,
                        onInteraction = onInteraction,
                        onClick = { onPlayPause(); onInteraction() }
                    )
                    if (hasNext) {
                        Spacer(Modifier.width(12.dp))
                        TvIconButton(Icons.Default.SkipNext, "Next episode", onInteraction) { onNext(); onInteraction() }
                    }
                    Spacer(Modifier.width(18.dp))
                    TvIconButton(Icons.Default.ClosedCaption, "Subtitles", onInteraction) { onSubtitles(); onInteraction() }
                    Spacer(Modifier.width(10.dp))
                    TvIconButton(Icons.Default.GraphicEq, "Audio", onInteraction) { onAudio(); onInteraction() }
                    if (hasEpisodes) {
                        Spacer(Modifier.width(10.dp))
                        TvIconButton(Icons.AutoMirrored.Filled.PlaylistPlay, "Episodes", onInteraction) { onEpisodes(); onInteraction() }
                    }
                    Spacer(Modifier.width(10.dp))
                    TvIconButton(Icons.Default.Brush, "Subtitle style", onInteraction) { onSubtitleStyle(); onInteraction() }
                    Spacer(Modifier.width(10.dp))
                    TvIconButton(Icons.Default.AspectRatio, "Resize", onInteraction) { onResize(); onInteraction() }
                    Spacer(Modifier.width(10.dp))
                    TvIconButton(Icons.Default.Speed, "Speed", onInteraction) { onSpeed(); onInteraction() }
                    Spacer(Modifier.width(10.dp))
                    TvIconButton(Icons.Default.Memory, "Decoder", onInteraction) { onDecoder(); onInteraction() }
                    if (canSwitchEngine) {
                        Spacer(Modifier.width(10.dp))
                        TvIconButton(Icons.Default.SwapHoriz, "Switch player", onInteraction) { onSwitchEngine(); onInteraction() }
                    }
                    Spacer(Modifier.width(10.dp))
                    TvIconButton(Icons.AutoMirrored.Filled.OpenInNew, "External player", onInteraction) { onExternal(); onInteraction() }
                }
            }
        }
    }
}

@Composable
private fun TvThinSeekBar(
    currentPosition: Long,
    duration: Long,
    baseStepMs: Long,
    focusRequester: FocusRequester,
    downFocus: FocusRequester,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onScrubbingChange: (Long?) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableStateOf(currentPosition) }

    LaunchedEffect(currentPosition) { if (!scrubbing) scrubPos = currentPosition }

    val safeDuration = duration.coerceAtLeast(1L)
    val displayPos = (if (scrubbing) scrubPos else currentPosition).coerceIn(0L, safeDuration)
    val fraction = displayPos.toFloat() / safeDuration.toFloat()

    Box(
        modifier = modifier
            .height(if (focused) 10.dp else 6.dp)
            .focusRequester(focusRequester)
            .focusProperties { down = downFocus }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionRight -> 1
                    Key.DirectionLeft -> -1
                    else -> 0
                }
                when {
                    direction != 0 && event.type == KeyEventType.KeyDown -> {
                        onInteraction()
                        scrubbing = true
                        val step = stepForRepeat(event.nativeKeyEvent.repeatCount, baseStepMs)
                        scrubPos = (scrubPos + direction * step).coerceIn(0L, safeDuration)
                        onScrubbingChange(scrubPos)
                        true
                    }
                    direction != 0 && event.type == KeyEventType.KeyUp -> {
                        onSeekTo(scrubPos)
                        scrubbing = false
                        onScrubbingChange(null)
                        true
                    }
                    (event.key == Key.DirectionCenter || event.key == Key.Enter) && event.type == KeyEventType.KeyDown -> {
                        onInteraction(); onPlayPause(); true
                    }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.22f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(if (focused) 10.dp else 6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun TvBigPlayButton(
    isPlaying: Boolean,
    focusRequester: FocusRequester,
    onInteraction: () -> Unit = {},
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.16f))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onInteraction() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = "Play/Pause",
            tint = if (focused) Color.Black else Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun TvIconButton(icon: ImageVector, description: String, onInteraction: () -> Unit = {}, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.12f))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onInteraction() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .then(if (focused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (focused) Color.Black else Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Step-up curve: the longer a direction key is held, the larger each jump. */
private fun stepForRepeat(repeatCount: Int, baseStepMs: Long): Long = when {
    repeatCount < 3 -> baseStepMs
    repeatCount < 8 -> baseStepMs * 3
    repeatCount < 15 -> baseStepMs * 6
    else -> baseStepMs * 12
}
