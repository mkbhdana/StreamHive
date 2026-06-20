package com.mkbhdana.streamhive.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.tv.theme.TvSurfaceColor
import com.mkbhdana.streamhive.util.FileUtils

/**
 * "Details" popup (name + size, plus format/resolution for files and an optional
 * drive/location line) — mirrors the mobile long-press dialog.
 */
@Composable
fun TvItemInfoDialog(file: MediaFileEntity, location: String? = null, onDismiss: () -> Unit) {
    val okFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { okFocus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = TvSurfaceColor, modifier = Modifier.width(460.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text(file.name, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                val details = buildList {
                    if (file.isFolder) {
                        add("Folder")
                    } else {
                        val ext = (file.fileExtension ?: file.name.substringAfterLast('.', "")).uppercase()
                        if (ext.isNotBlank()) add("Format: $ext")
                        file.videoHeight?.takeIf { it > 0 }?.let { add("Resolution: ${it}p") }
                    }
                    file.size?.let { FileUtils.formatFileSize(it) }?.takeIf { it.isNotBlank() }?.let { add("Size: $it") }
                    location?.takeIf { it.isNotBlank() }?.let { add("Location: $it") }
                }
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f)) }
                }
                Spacer(Modifier.height(20.dp))
                var okFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .focusRequester(okFocus)
                        .onFocusChanged { okFocused = it.isFocused }
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (okFocused) Color.White else Color.White.copy(alpha = 0.12f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                ) {
                    Text("OK", color = if (okFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
