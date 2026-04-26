package com.driveplay.app.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ResizeOption(
    val key: String,
    val label: String,
    val description: String,
    val icon: ImageVector
)

private val resizeOptions = listOf(
    ResizeOption("fit", "Fit", "Fit inside screen, no crop", Icons.Default.FitScreen),
    ResizeOption("fill", "Fill", "Fill screen, may crop edges", Icons.Default.Fullscreen),
    ResizeOption("zoom", "Zoom", "Zoom to fill width", Icons.Default.ZoomOutMap),
    ResizeOption("16:9", "16:9", "Force 16:9 aspect ratio", Icons.Default.Crop169),
    ResizeOption("4:3", "4:3", "Force 4:3 aspect ratio", Icons.Default.CropPortrait)
)

@Composable
fun ResizeModeSelector(
    currentMode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Screen Resize", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                resizeOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option.key) }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == option.key,
                            onClick = { onSelect(option.key) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            option.icon, null,
                            tint = if (currentMode == option.key)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (currentMode == option.key) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
