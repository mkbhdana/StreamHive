package com.mkbhdana.streamhive.tv.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * D-pad long-press: fires [onLongClick] on a held center/Menu key, consuming the
 * release so it doesn't also trigger the regular click. Combine with a normal
 * `.clickable {}` for the short-press action.
 */
fun Modifier.longPressable(onLongClick: () -> Unit): Modifier = composed {
    var centerDownAt by remember { mutableStateOf(0L) }
    onPreviewKeyEvent { event ->
        when (event.key) {
            Key.Menu -> if (event.type == KeyEventType.KeyDown) { onLongClick(); true } else false
            Key.DirectionCenter, Key.Enter -> when (event.type) {
                KeyEventType.KeyDown -> { if (centerDownAt == 0L) centerDownAt = System.currentTimeMillis(); false }
                KeyEventType.KeyUp -> {
                    val held = System.currentTimeMillis() - centerDownAt
                    centerDownAt = 0L
                    if (held > 450L) { onLongClick(); true } else false
                }
                else -> false
            }
            else -> false
        }
    }
}
