package com.mkbhdana.streamhive.tv.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mkbhdana.streamhive.catalog.info.MediaInfoViewModel
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.navigation.MediaInfoRoute
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.tv.components.TvButton
import com.mkbhdana.streamhive.tv.components.posterModel
import com.mkbhdana.streamhive.tv.components.backdropModel
import com.mkbhdana.streamhive.tv.theme.TmdbAccentColor
import com.mkbhdana.streamhive.tv.theme.TvBackgroundColor
import com.mkbhdana.streamhive.tv.theme.TvCardColor
import com.mkbhdana.streamhive.tv.theme.TvDimens
import com.mkbhdana.streamhive.tv.theme.TvWhite
import com.mkbhdana.streamhive.util.FileUtils

/**
 * Full-bleed detail screen. Movies show a row of file "versions" + an info card
 * for the highlighted one; series show season chips → episode cards → info card.
 * Reuses [MediaInfoViewModel].
 */
@Composable
fun TvMediaInfoScreen(
    navKey: MediaInfoRoute,
    onPlay: (fileId: String, fileName: String, engine: PlayerEngine, decoder: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: MediaInfoViewModel = hiltViewModel<MediaInfoViewModel, MediaInfoViewModel.Factory>(
        key = "media_${navKey.driveFileId}",
        creationCallback = { factory -> factory.create(navKey) }
    )
) {
    val state by viewModel.uiState.collectAsState()
    val engine = viewModel.getPreferredEngine()
    val playFocus = remember { FocusRequester() }
    val poster = posterModel(state.metadata, state.driveFiles.firstOrNull())

    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        // The keyed ViewModel loads once on creation and is reused across navigations.
        // Returning from playback no longer auto-refreshes; the user pulls fresh data
        // on demand with the refresh button (top-right).
        viewModel.setSourcePriorityFilteringEnabled(true)
    }
    LaunchedEffect(state.isLoading, state.driveFiles) {
        if (!state.isLoading && state.driveFiles.isNotEmpty()) runCatching { playFocus.requestFocus() }
    }

    val isSeries = state.fileSeasons.isNotEmpty()
    // Read the season from the ViewModel (not local state) so it survives the refresh()
    // and re-composition that happen when returning from playback.
    val selectedSeason = state.expandedSeason ?: state.fileSeasons.firstOrNull()?.seasonNumber
    val episodes = if (isSeries) {
        state.fileSeasons.firstOrNull { it.seasonNumber == selectedSeason }?.files.orEmpty()
    } else {
        state.driveFiles
    }
    var selectedFile by remember(episodes) { mutableStateOf(episodes.firstOrNull()) }

    Box(modifier = Modifier.fillMaxSize().background(TvBackgroundColor)) {
        backdropModel(state.metadata, state.driveFiles.firstOrNull())?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to TvBackgroundColor, 0.35f to TvBackgroundColor.copy(alpha = 0.75f), 0.7f to Color.Transparent)
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.3f to Color.Transparent, 1f to TvBackgroundColor)
            )
        )

        if (state.isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(TvDimens.Overscan),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = state.metadata?.title ?: state.driveFiles.firstOrNull()?.name ?: "Details",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val typeLabel = if (isSeries) "Series" else "Movie"
                Text(
                    listOfNotNull(typeLabel, state.metadata?.year, "${state.driveFiles.size} file(s)").joinToString("  •  "),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                state.metadata?.rating?.takeIf { it > 0f }?.let {
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Default.Star, contentDescription = "TMDB rating", tint = TmdbAccentColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("%.1f".format(it), color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            state.metadata?.overview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.55f)
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvButton(
                    label = "Play",
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        val f = selectedFile ?: state.driveFiles.firstOrNull()
                        if (f != null) onPlay(f.id, f.name, engine, null)
                    },
                    focusRequester = playFocus
                )
                if (state.sourcePriorityConfigured) {
                    Spacer(Modifier.width(12.dp))
                    TvButton(
                        label = if (state.sourcePriorityTemporarilyDisabled) "Best Source: Off" else "Best Source: On",
                        onClick = { viewModel.setSourcePriorityTemporarilyDisabled(!state.sourcePriorityTemporarilyDisabled) }
                    )
                }
            }

            // ── File browser ──
            if (isSeries && state.fileSeasons.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.fileSeasons, key = { it.seasonNumber }) { season ->
                        TvSeasonChip(
                            label = season.label,
                            selected = season.seasonNumber == selectedSeason,
                            onClick = { viewModel.selectSeason(season.seasonNumber) }
                        )
                    }
                }
            }

            if (episodes.size > 1) {
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(episodes, key = { it.id }) { file ->
                        TvVariantCard(
                            posterUrl = poster,
                            label = if (isSeries) episodeLabel(file.name) else variantLabel(file.name, episodes.indexOf(file)),
                            onFocused = { selectedFile = file },
                            onClick = { onPlay(file.id, file.name, engine, null) }
                        )
                    }
                }
            }

            (selectedFile ?: state.driveFiles.firstOrNull())?.let { file ->
                Spacer(Modifier.height(14.dp))
                TvFileInfoCard(file)
            }
        }

        // Manual refresh — re-fetch files/metadata on demand if something is missing.
        TvRefreshButton(
            onClick = { viewModel.refresh() },
            modifier = Modifier.align(Alignment.TopEnd).padding(TvDimens.Overscan)
        )
    }
}

@Composable
private fun TvRefreshButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.Black.copy(alpha = 0.45f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = "Refresh",
            tint = if (focused) Color.Black else Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun TvSeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (focused) Color.Black else Color.White,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun TvVariantCard(posterUrl: String?, label: String, onFocused: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .width(150.dp)
            .height(86.dp)
            .clip(shape)
            .background(TvCardColor)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .then(if (focused) Modifier.border(3.dp, TvWhite, shape) else Modifier)
    ) {
        if (posterUrl != null) {
            AsyncImage(posterUrl, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))
            )
        )
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        )
    }
}

@Composable
private fun TvFileInfoCard(file: MediaFileEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(10.dp))
            .background(TvCardColor.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Full filename — wraps onto multiple lines, never truncated with an ellipsis.
        Text(
            file.name,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            softWrap = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(5.dp))
        val info = listOfNotNull(
            formatLabel(file),
            file.size?.let { FileUtils.formatFileSize(it) }.takeIf { !it.isNullOrBlank() },
            file.videoDurationMs?.takeIf { it > 0 }?.let { FileUtils.formatDuration(it) }
        ).joinToString("   •   ")
        Text(info, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatLabel(file: MediaFileEntity): String {
    val ext = (file.fileExtension ?: file.name.substringAfterLast('.', "")).uppercase()
    var res = Regex("""(?i)\b(2160p|1440p|1080p|720p|480p|360p|4k|uhd|fhd)\b""").find(file.name)?.value?.uppercase()
    res = when (res) {
        "4K", "UHD" -> "2160P"
        "FHD" -> "1080P"
        else -> res
    }
    if (res == null) {
        val h = file.videoHeight
        res = when {
            h == null -> null
            h >= 2000 -> "2160P"
            h >= 1400 -> "1440P"
            h >= 1000 -> "1080P"
            h >= 700 -> "720P"
            h >= 470 -> "480P"
            else -> null
        }
    }
    return listOfNotNull(ext.takeIf { it.isNotBlank() }, res).joinToString(" · ").ifBlank { "Video" }
}

private fun episodeLabel(name: String): String {
    Regex("""(?i)\bS(\d{1,2})\s*E(\d{1,2})\b""").find(name)?.let {
        return "S%02dE%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    Regex("""(?i)\b(\d{1,2})x(\d{1,2})\b""").find(name)?.let {
        return "S%02dE%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    return name.substringBeforeLast('.').take(10)
}

private fun variantLabel(name: String, index: Int): String {
    Regex("""(?i)\b(2160p|1080p|720p|480p|4k)\b""").find(name)?.let { return it.value.uppercase() }
    return "File ${index + 1}"
}
