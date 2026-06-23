package com.mkbhdana.streamhive.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import kotlinx.coroutines.delay

private const val SHOW_AT_FRACTION = 0.92f
private const val AUTO_HIDE_MS = 12_000L
private const val SWITCH_SUPPRESS_MS = 2500L

/**
 * Floating "Next Episode" button that surfaces near the end of a series episode and
 * auto-hides after a few seconds. Shared by the mobile and TV players.
 *
 * @param autoFocus request focus when shown (TV remotes).
 * @param returnFocus where to send focus when the button hides (TV remotes).
 */
@Composable
fun BoxScope.NextEpisodeOverlay(
    nextEpisode: MediaFileEntity?,
    currentPosition: Long,
    duration: Long,
    onPlayNext: () -> Unit,
    autoFocus: Boolean = false,
    returnFocus: FocusRequester? = null,
    containerColor: Color = Color.White,
    contentColor: Color = Color.Black
) {
    if (nextEpisode == null || duration <= 0L) return

    // Show once playback passes 92% of the episode.
    val nearEnd = currentPosition in 0..duration && currentPosition >= (duration * SHOW_AT_FRACTION).toLong()

    var visible by remember(nextEpisode.id) { mutableStateOf(false) }
    var focused by remember(nextEpisode.id) { mutableStateOf(false) }
    val focusRequester = remember(nextEpisode.id) { FocusRequester() }

    // Suppress briefly right after an episode switch, so the auto-advance at end-of-video
    // doesn't flash this button for the freshly-loaded next episode.
    var justSwitched by remember(nextEpisode.id) { mutableStateOf(true) }
    LaunchedEffect(nextEpisode.id) {
        justSwitched = true
        delay(SWITCH_SUPPRESS_MS)
        justSwitched = false
    }

    // Reveal once when crossing the threshold, then auto-hide.
    LaunchedEffect(nearEnd, justSwitched, nextEpisode.id) {
        if (nearEnd && !justSwitched) {
            visible = true
            delay(AUTO_HIDE_MS)
            visible = false
            if (autoFocus) runCatching { returnFocus?.requestFocus() }
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { it / 2 },
        exit = fadeOut() + slideOutHorizontally { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 32.dp, bottom = 120.dp)
    ) {
        // Keep focus on the button while it's shown, so OK clicks it immediately and
        // neither the controls' seekbar nor the root view can steal focus back —
        // regardless of whether the controls were visible when it appeared.
        if (autoFocus) {
            LaunchedEffect(Unit) {
                while (true) {
                    if (visible && !focused) runCatching { focusRequester.requestFocus() }
                    delay(150)
                }
            }
        }
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(percent = 50),
            border = if (focused) BorderStroke(2.dp, contentColor) else null,
            modifier = Modifier
                .scale(if (focused) 1.08f else 1f)
                .then(if (autoFocus) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                // clickable is itself focusable AND handles DPAD-CENTER/Enter, so the
                // focus request lands on the same node that consumes OK → onPlayNext.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onPlayNext() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(22.dp))
                Text("Next Episode", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}
