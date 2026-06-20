package com.mkbhdana.streamhive.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.tv.theme.TvSurfaceColor

private val SUB_COLORS = listOf(
    0xFFFFFFFFL to "White", 0xFFFFFF00L to "Yellow", 0xFF00E5FFL to "Cyan",
    0xFF69F0AEL to "Green", 0xFFFF5252L to "Red"
)

/** Compact subtitle-style editor shown over the player (bottom-right). */
@Composable
fun TvSubtitleStylePanel(
    fontSize: Int,
    onFontSize: (Int) -> Unit,
    color: Long,
    onColor: (Long) -> Unit,
    position: Int,
    onPosition: (Int) -> Unit,
    bgOpacity: Float,
    onBgOpacity: (Float) -> Unit,
    scale: Float,
    onScale: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
        BackHandler { onDismiss() }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(40.dp).width(440.dp),
            shape = RoundedCornerShape(16.dp),
            color = TvSurfaceColor
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Subtitle Style",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                StepperRow("Size", "$fontSize", firstFocus,
                    onMinus = { onFontSize((fontSize - 1).coerceIn(10, 48)) },
                    onPlus = { onFontSize((fontSize + 1).coerceIn(10, 48)) })
                StepperRow("Position", "$position",
                    onMinus = { onPosition((position - 5).coerceIn(0, 100)) },
                    onPlus = { onPosition((position + 5).coerceIn(0, 100)) })
                StepperRow("Background", "${(bgOpacity * 100).toInt()}%",
                    onMinus = { onBgOpacity((bgOpacity - 0.1f).coerceIn(0f, 1f)) },
                    onPlus = { onBgOpacity((bgOpacity + 0.1f).coerceIn(0f, 1f)) })
                StepperRow("Scale", "%.1f".format(scale),
                    onMinus = { onScale((scale - 0.1f).coerceIn(0.5f, 3.0f)) },
                    onPlus = { onScale((scale + 0.1f).coerceIn(0.5f, 3.0f)) })
                Spacer(Modifier.height(10.dp))
                Text("Color", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SUB_COLORS.forEach { (value, _) ->
                        ColorDot(colorValue = value, selected = value == color) { onColor(value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, focusRequester: FocusRequester? = null, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        MiniButton(Icons.Default.Remove, onMinus, focusRequester)
        Text(value, color = Color.White, modifier = Modifier.width(64.dp).padding(horizontal = 10.dp), style = MaterialTheme.typography.titleMedium)
        MiniButton(Icons.Default.Add, onPlus)
    }
}

@Composable
private fun MiniButton(icon: ImageVector, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.14f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ColorDot(colorValue: Long, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(colorValue))
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused || selected) Modifier.border(3.dp, if (focused) Color.White else Color.White.copy(alpha = 0.6f), CircleShape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}
