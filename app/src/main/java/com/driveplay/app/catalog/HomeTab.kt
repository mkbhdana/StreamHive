package com.driveplay.app.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.driveplay.app.data.db.MediaFileEntity
import com.driveplay.app.data.db.PlaybackHistoryEntity
import com.driveplay.app.data.db.TmdbMetadataEntity
import com.driveplay.app.player.mpv.PlayerEngine
import com.driveplay.app.ui.components.MediaCard
import com.driveplay.app.util.FileUtils

@Composable
fun HomeTab(
    state: CatalogUiState,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasContinuePlaying = state.continuePlayingItems.isNotEmpty()
    val hasAnyContent = state.homeMovies.isNotEmpty() ||
            state.homeTvShows.isNotEmpty() ||
            state.homeAnime.isNotEmpty()

    if (!state.hasTmdbSetup && !hasContinuePlaying) {
        TmdbSetupPrompt(onNavigateToSettings = onNavigateToSettings, modifier = modifier)
        return
    }

    if (state.isHomeLoading && !hasAnyContent && !hasContinuePlaying) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ──── Continue Playing ────
        if (hasContinuePlaying) {
            item {
                ContinuePlayingSection(
                    items = state.continuePlayingItems,
                    engine = state.selectedEngine,
                    onPlayFile = onPlayFile
                )
            }
        }

        // ──── Movies ────
        if (state.homeMovies.isNotEmpty()) {
            item {
                MediaSection(
                    title = "Movies",
                    icon = Icons.Default.Movie,
                    files = state.homeMovies,
                    tmdbMetadata = state.tmdbMetadata,
                    engine = state.selectedEngine,
                    onPlayFile = onPlayFile
                )
            }
        }

        // ──── TV Shows ────
        if (state.homeTvShows.isNotEmpty()) {
            item {
                MediaSection(
                    title = "TV Shows",
                    icon = Icons.Default.Tv,
                    files = state.homeTvShows,
                    tmdbMetadata = state.tmdbMetadata,
                    engine = state.selectedEngine,
                    onPlayFile = onPlayFile
                )
            }
        }

        // ──── Anime ────
        if (state.homeAnime.isNotEmpty()) {
            item {
                MediaSection(
                    title = "Anime",
                    icon = Icons.Default.Animation,
                    files = state.homeAnime,
                    tmdbMetadata = state.tmdbMetadata,
                    engine = state.selectedEngine,
                    onPlayFile = onPlayFile
                )
            }
        }

        if (!hasAnyContent && !hasContinuePlaying && state.hasTmdbSetup) {
            item {
                NoHomeContent(onNavigateToSettings = onNavigateToSettings)
            }
        }

        if (state.isHomeLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.5f))
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ──── Continue Playing Section ────

@Composable
private fun ContinuePlayingSection(
    items: List<PlaybackHistoryEntity>,
    engine: PlayerEngine,
    onPlayFile: (String, String, PlayerEngine) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayCircle, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Continue Playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.fileId }) { item ->
                ContinuePlayingCard(
                    item = item,
                    onClick = { onPlayFile(item.fileId, item.fileName, engine) }
                )
            }
        }
    }
}

@Composable
private fun ContinuePlayingCard(
    item: PlaybackHistoryEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VideoFile, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.PlayArrow, null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                item.fileName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            // Progress bar
            LinearProgressIndicator(
                progress = { item.progressPercent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    FileUtils.formatDuration(item.lastPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    FileUtils.formatDuration(item.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ──── Media Section ────

@Composable
private fun MediaSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    files: List<MediaFileEntity>,
    tmdbMetadata: Map<String, TmdbMetadataEntity>,
    engine: PlayerEngine,
    onPlayFile: (String, String, PlayerEngine) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${files.size} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(files, key = { it.id }) { file ->
                MediaCard(
                    file = file,
                    onClick = {
                        if (!file.isFolder) {
                            onPlayFile(file.id, file.name, engine)
                        }
                    },
                    tmdbMetadata = tmdbMetadata[file.id],
                    modifier = Modifier.width(160.dp)
                )
            }
        }
    }
}

// ──── Empty states ────

@Composable
private fun TmdbSetupPrompt(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Text("Set Up Your Catalog", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Add your TMDB API key and map folders to Movies, TV Shows, or Anime to see beautiful metadata here.\n\nOr start watching videos — they'll appear in Continue Playing!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Button(
                    onClick = onNavigateToSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Settings")
                }
            }
        }
    }
}

@Composable
private fun NoHomeContent(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text("No Folders Mapped", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Go to Settings → TMDB Metadata and add folders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = onNavigateToSettings,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Settings")
            }
        }
    }
}
