package com.mkbhdana.streamhive.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleDelaySheet(
    currentDelay: Long,
    onDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        HideBottomSheetSystemUI()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Subtitle Delay",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(24.dp))

            // Current delay display
            Text(
                text = "${currentDelay}ms",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (currentDelay == 0L) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))

            // Fine controls: ±50ms
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DelayButton("-500ms") { onDelayChange(currentDelay - 500) }
                DelayButton("-100ms") { onDelayChange(currentDelay - 100) }
                DelayButton("-50ms") { onDelayChange(currentDelay - 50) }

                // Reset button
                FilledTonalButton(
                    onClick = { onDelayChange(0) },
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                }

                DelayButton("+50ms") { onDelayChange(currentDelay + 50) }
                DelayButton("+100ms") { onDelayChange(currentDelay + 100) }
                DelayButton("+500ms") { onDelayChange(currentDelay + 500) }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Positive = subtitle appears later\nNegative = subtitle appears earlier",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DelayButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
