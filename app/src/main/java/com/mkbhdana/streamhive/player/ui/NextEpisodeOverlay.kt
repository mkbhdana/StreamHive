package com.mkbhdana.streamhive.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.focusable
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import kotlinx.coroutines.delay

private const val END_ZONE_MS = 45_000L
private const val AUTO_HIDE_MS = 12_000L

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
    returnFocus: FocusRequester? = null
) {
    if (nextEpisode == null || duration <= 0L) return

    val remaining = duration - currentPosition
    val inEndZone = remaining in 0..END_ZONE_MS

    var visible by remember(nextEpisode.id) { mutableStateOf(false) }
    var focused by remember(nextEpisode.id) { mutableStateOf(false) }
    val focusRequester = remember(nextEpisode.id) { FocusRequester() }

    // Reveal once when entering the end zone, then auto-hide.
    LaunchedEffect(inEndZone, nextEpisode.id) {
        if (inEndZone) {
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
        // Grab focus once the button is actually on screen (TV remotes).
        if (autoFocus) {
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
        }
        Surface(
            color = Color.Black.copy(alpha = 0.78f),
            contentColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            border = if (focused) BorderStroke(2.dp, Color.White) else null,
            modifier = Modifier
                .scale(if (focused) 1.05f else 1f)
                .let { base ->
                    if (autoFocus) base.focusRequester(focusRequester).focusable() else base
                }
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onPlayNext() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.background(Color.Transparent).padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(22.dp))
                Text("Next Episode", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}
