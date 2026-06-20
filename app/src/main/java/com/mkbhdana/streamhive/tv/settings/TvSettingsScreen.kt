package com.mkbhdana.streamhive.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mkbhdana.streamhive.settings.SettingsViewModel
import com.mkbhdana.streamhive.tv.components.TvUpdateDialog
import com.mkbhdana.streamhive.tv.manage.TvManageScreen
import com.mkbhdana.streamhive.tv.theme.TvBackgroundColor
import com.mkbhdana.streamhive.tv.theme.TvDimens
import com.mkbhdana.streamhive.tv.theme.TvSurfaceColor

enum class TvSettingsCategory(val label: String, val subtitle: String, val icon: ImageVector) {
    Player("Player", "Engine, decoder, resize mode", Icons.Default.SmartDisplay),
    Subtitles("Audio & Subtitles", "Languages, sizes, LibASS", Icons.Default.Subtitles),
    Tmdb("TMDB Metadata", "API key and catalog folders", Icons.Default.Movie),
    Manage("Manage on Phone", "Key, folders, backup via QR", Icons.Default.QrCode2),
    Storage("Storage & Cache", "Manage cached data", Icons.Default.Storage),
    About("About & Account", "Updates and sign out", Icons.Default.AccountCircle)
}

/**
 * Two-pane TV settings (NuvioTV style): pill category buttons on the left, a
 * titled detail pane on the right. Reuses the shared [SettingsViewModel].
 * Pressing LEFT in the right pane returns to the category list.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSettingsScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var selected by remember { mutableStateOf(TvSettingsCategory.Player) }
    val leftReq = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { leftReq.requestFocus() } }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackgroundColor)
            .padding(top = TvDimens.Overscan, start = TvDimens.Overscan, bottom = TvDimens.Overscan)
    ) {
        // Left: category pills
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .focusRequester(leftReq)
                .focusRestorer()
                .focusGroup()
        ) {
            TvSettingsCategory.entries.forEach { category ->
                TvCategoryPill(
                    label = category.label,
                    icon = category.icon,
                    selected = selected == category,
                    onSelected = { selected = category }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.width(28.dp))

        // Right: header + content container. LEFT exits to the category list.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = TvDimens.Overscan)
                .focusGroup()
                .focusProperties { left = leftReq }
        ) {
            Text(
                selected.label,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                selected.subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(TvSurfaceColor)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                when (selected) {
                    TvSettingsCategory.Player -> TvPlayerPane(viewModel)
                    TvSettingsCategory.Subtitles -> TvSubtitlesPane(viewModel)
                    TvSettingsCategory.Tmdb -> TvTmdbPane(viewModel)
                    TvSettingsCategory.Manage -> TvManageScreen()
                    TvSettingsCategory.Storage -> TvStoragePane(viewModel)
                    TvSettingsCategory.About -> TvAboutPane(viewModel, onLogout)
                }
            }
        }
    }

    // Surfaced after a manual "Check for Updates" finds a newer build.
    viewModel.uiState.availableUpdate?.let { update ->
        TvUpdateDialog(
            versionName = update.versionName,
            targetAbi = update.targetAbi,
            isDownloading = viewModel.uiState.isDownloadingUpdate,
            downloadProgress = viewModel.uiState.updateDownloadProgress,
            onDownload = viewModel::downloadAndInstallUpdate,
            onDismiss = viewModel::dismissUpdatePrompt
        )
    }
}

@Composable
private fun TvCategoryPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onSelected: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(30.dp)
    val container = when {
        focused -> Color.White.copy(alpha = 0.14f)
        selected -> Color.White.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(container)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelected()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelected
            )
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp)
        )
    }
}
