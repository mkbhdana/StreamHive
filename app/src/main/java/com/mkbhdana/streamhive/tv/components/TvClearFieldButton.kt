package com.mkbhdana.streamhive.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/**
 * Focusable "clear" (✕) button placed inside a TV search field's trailing slot.
 * The text field swallows left/right for the cursor, so the field hops focus here
 * explicitly; this button then hops back (left) or onward (right).
 */
@Composable
fun TvClearFieldButton(
    focusRequester: FocusRequester,
    onLeft: () -> Unit,
    onRight: (() -> Unit)? = null,
    onClear: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> { onLeft(); true }
                        Key.DirectionRight -> if (onRight != null) { onRight(); true } else false
                        else -> false
                    }
                } else false
            }
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.12f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClear
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = "Clear",
            tint = if (focused) Color.Black else Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(18.dp)
        )
    }
}
