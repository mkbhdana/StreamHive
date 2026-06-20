package com.mkbhdana.streamhive.tv.manage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mkbhdana.streamhive.tv.auth.QrCode
import com.mkbhdana.streamhive.tv.theme.TvTextSecondaryColor

/** Settings pane: shows a QR to manage the catalog (key/folders/backup) from a phone. */
@Composable
fun TvManageScreen(viewModel: TvManageViewModel = hiltViewModel()) {
    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }
    val qr = remember(viewModel.manageUrl) { viewModel.manageUrl?.let { QrCode.generate(it, 500) } }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Box(
            modifier = Modifier.size(280.dp).clip(RoundedCornerShape(20.dp)).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            when {
                qr != null -> Image(bitmap = qr, contentDescription = "Manage QR code", modifier = Modifier.size(248.dp))
                viewModel.serverError != null -> Text("!", color = Color.Gray)
                else -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(modifier = Modifier.width(420.dp)) {
            Text("Manage on your phone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            viewModel.serverError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            } ?: run {
                Text(
                    "Scan to set your TMDB key, add/remove catalog folders, and import or export a settings backup — all from your phone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
                viewModel.manageUrl?.let {
                    Text("Or open: $it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    "Phone and TV must be on the same network. Changes apply immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TvTextSecondaryColor,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}
