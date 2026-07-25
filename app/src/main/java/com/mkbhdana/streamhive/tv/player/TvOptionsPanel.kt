package com.mkbhdana.streamhive.tv.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.tv.theme.TvSurfaceColor

data class TvOption(val label: String, val sublabel: String? = null, val selected: Boolean, val onClick: () -> Unit)

/**
 * Compact options panel anchored to the bottom-right of the player (not a
 * centered modal). Used for subtitle / audio / quality selection.
 */
@Composable
fun TvOptionsPanel(title: String, options: List<TvOption>, onDismiss: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
        BackHandler { onDismiss() }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(40.dp).width(380.dp),
            shape = RoundedCornerShape(16.dp),
            color = TvSurfaceColor
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                if (options.isEmpty()) {
                    Text("None available", color = Color.White.copy(alpha = 0.6f))
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(options) { index, option ->
                            TvOptionRow(option, if (index == 0) firstFocus else null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvOptionRow(option: TvOption, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = option.onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                option.label,
                color = if (focused) Color.Black else Color.White,
                fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium
            )
            option.sublabel?.let {
                Text(it, color = if (focused) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (option.selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Check, "selected", tint = if (focused) Color.Black else Color.White)
        }
    }
}
