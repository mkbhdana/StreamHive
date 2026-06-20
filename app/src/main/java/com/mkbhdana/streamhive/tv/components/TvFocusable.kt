package com.mkbhdana.streamhive.tv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.tv.theme.TvWhite as TvFocusRing

/**
 * The core focus primitive for the TV UI: a clickable surface that scales and
 * draws a focus ring when it holds D-pad focus. `.clickable` already makes the
 * surface focusable and fires [onClick] on the remote's center/enter key.
 */
@Composable
fun TvFocusableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    focusedScale: Float = 1.06f,
    onFocusChanged: (Boolean) -> Unit = {},
    content: @Composable (focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "tvFocusScale")

    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .then(
                if (focused) Modifier.border(3.dp, TvFocusRing, shape) else Modifier
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        content(focused)
    }
}

/** A focusable pill button for the TV UI (e.g. "Play", "Resume"). */
@Composable
fun TvButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        focusRequester = focusRequester,
        enabled = enabled
    ) { focused ->
        val container = if (focused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant
        val content = if (focused) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(container)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content)
                Spacer(Modifier.width(10.dp))
            }
            Text(label, color = content, fontWeight = FontWeight.SemiBold)
        }
    }
}
