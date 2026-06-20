package com.mkbhdana.streamhive.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mkbhdana.streamhive.tv.theme.TvSurfaceColor

/**
 * D-pad-friendly "Update Available" dialog mirroring the mobile prompt.
 * The Download button takes initial focus.
 */
@Composable
fun TvUpdateDialog(
    versionName: String,
    targetAbi: String,
    isDownloading: Boolean,
    downloadProgress: Int,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val downloadFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { downloadFocus.requestFocus() } }

    Dialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = TvSurfaceColor,
            modifier = Modifier.width(440.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.SystemUpdate, null, tint = Color.White, modifier = Modifier.padding(bottom = 10.dp))
                Text("Update Available", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(
                    buildString {
                        append("StreamHive v$versionName is available.")
                        if (targetAbi.isNotBlank()) append("\nAPK: $targetAbi")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 22.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TvUpdateButton(
                        label = if (isDownloading) "Downloading $downloadProgress%" else "Download & Install",
                        focusRequester = downloadFocus,
                        primary = true,
                        enabled = !isDownloading,
                        onClick = onDownload
                    )
                    Spacer(Modifier.width(14.dp))
                    TvUpdateButton(
                        label = "Later",
                        enabled = !isDownloading,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun TvUpdateButton(
    label: String,
    focusRequester: FocusRequester? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val container = when {
        !enabled -> Color.White.copy(alpha = 0.06f)
        focused -> Color.White
        primary -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val content = when {
        !enabled -> Color.White.copy(alpha = 0.5f)
        focused -> Color.Black
        else -> Color.White
    }
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(container)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 26.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = content, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
    }
}
