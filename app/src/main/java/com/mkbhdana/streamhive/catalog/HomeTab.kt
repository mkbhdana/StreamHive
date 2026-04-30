package com.mkbhdana.streamhive.catalog

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.util.FileUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import android.widget.Toast
import com.mkbhdana.streamhive.ui.components.LoadingIndicator
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun HomeTab(
    state: CatalogUiState,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    onNavigateToSettings: () -> Unit,
    onClearHistory: () -> Unit = {},
    onNavigateToInfo: (String, String) -> Unit = { _, _ -> },
    onRemoveFromContinue: (String) -> Unit = {},
    onPlayFromStart: (String, String, PlayerEngine) -> Unit = onPlayFile,
    onNavigateToSeeAll: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasContinuePlaying = state.continuePlayingItems.isNotEmpty()
    val hasAnyContent = state.homeSections.isNotEmpty() || state.homeRecentlyAdded.isNotEmpty()

    if (!state.hasTmdbSetup && !hasContinuePlaying) {
        TmdbSetupPrompt(onNavigateToSettings = onNavigateToSettings, modifier = modifier)
        return
    }

    // Show skeleton while drives or home content is loading
    if ((state.isLoading || state.isHomeLoading) && !hasAnyContent && !hasContinuePlaying) {
        HomeSkeletonLoading(modifier = modifier)
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
                    onPlayFile = onPlayFile,
                    onRemoveItem = onRemoveFromContinue,
                    onPlayFromStart = onPlayFromStart
                )
            }
        }

        // ──── Recently Added ────
        if (state.homeRecentlyAdded.isNotEmpty()) {
            item {
                TmdbHorizontalSection(
                    title = "Recently Added",
                    icon = Icons.Default.NewReleases,
                    files = state.homeRecentlyAdded,
                    totalCount = state.homeRecentlyAdded.size,
                    tmdbMetadata = state.tmdbMetadata,
                    mediaType = "auto",
                    onNavigateToInfo = onNavigateToInfo,
                    onSeeAll = {}
                )
            }
        }

        // ──── Dynamic Catalog Sections ────
        state.homeSections.forEach { section ->
            item(key = "section_${section.folderId}") {
                val sectionIcon = when (section.typeLabel) {
                    "Movie" -> Icons.Default.Movie
                    "Series" -> Icons.Default.Tv
                    "Anime" -> Icons.Default.Animation
                    else -> Icons.Default.Movie
                }
                val seeAllCategory = when (section.typeLabel) {
                    "Movie" -> "movies"
                    "Series" -> "tv"
                    "Anime" -> "anime"
                    else -> "movies"
                }
                TmdbHorizontalSection(
                    title = "${section.folderName} - ${section.typeLabel}",
                    icon = sectionIcon,
                    files = section.items.take(10),
                    totalCount = section.items.size,
                    tmdbMetadata = state.tmdbMetadata,
                    mediaType = section.mediaType,
                    onNavigateToInfo = onNavigateToInfo,
                    onSeeAll = { onNavigateToSeeAll(seeAllCategory) }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.fillMaxSize(),
                        message = "Getting Ready..."
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ──── Continue Playing Section ────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinuePlayingSection(
    items: List<PlaybackHistoryEntity>,
    engine: PlayerEngine,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    onRemoveItem: (String) -> Unit = {},
    onPlayFromStart: (String, String, PlayerEngine) -> Unit = onPlayFile
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var longPressItem by remember { mutableStateOf<PlaybackHistoryEntity?>(null) }

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
                    onClick = { onPlayFile(item.fileId, item.fileName, engine) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longPressItem = item
                    }
                )
            }
        }
    }

    // Long-press options dialog
    if (longPressItem != null) {
        val item = longPressItem!!
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { longPressItem = null },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    item.fileName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                longPressItem = null
                                scope.launch {
                                    onPlayFromStart(item.fileId, item.fileName, engine)
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Replay, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Play from start", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onRemoveItem(item.fileId)
                                Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                                longPressItem = null
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(16.dp))
                        Text("Remove from history", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { longPressItem = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinuePlayingCard(
    item: PlaybackHistoryEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column {
            // Thumbnail area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VideoFile, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                // Play overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayCircleFilled, null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            // Info area
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    item.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
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
}

// ──── Last Played Section ────

@Composable
private fun LastPlayedSection(
    items: List<PlaybackHistoryEntity>,
    engine: PlayerEngine,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    onClearHistory: () -> Unit = {}
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.History, null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Last Played",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showClearConfirm = true }) {
                Icon(
                    Icons.Default.DeleteSweep, "Clear All",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { "lp_${it.fileId}" }) { item ->
                LastPlayedCard(
                    item = item,
                    onClick = { onPlayFile(item.fileId, item.fileName, engine) }
                )
            }
        }
    }

    // Clear history confirmation dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear Watch History") },
            text = { Text("This will remove all playback history and continue playing items. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearConfirm = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LastPlayedCard(
    item: PlaybackHistoryEntity,
    onClick: () -> Unit
) {
    val timeAgo = remember(item.lastPlayedAt) {
        val diff = System.currentTimeMillis() - item.lastPlayedAt
        when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 604_800_000 -> "${diff / 86_400_000}d ago"
            else -> "${diff / 604_800_000}w ago"
        }
    }

    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VideoFile, null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    timeAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            Spacer(Modifier.height(6.dp))
            // Show progress if partially watched
            if (item.progressPercent > 0.01f && !item.isCompleted) {
                LinearProgressIndicator(
                    progress = { item.progressPercent.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                )
            } else if (item.isCompleted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ──── TMDB Horizontal Section ────

@Composable
fun TmdbHorizontalSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    files: List<MediaFileEntity>,
    totalCount: Int,
    tmdbMetadata: Map<String, TmdbMetadataEntity>,
    mediaType: String = "auto",
    onNavigateToInfo: (String, String) -> Unit,
    onSeeAll: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
            if (totalCount > 10) {
                TextButton(onClick = onSeeAll) {
                    Text("See All ($totalCount)")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
                }
            } else {
                Text(
                    "$totalCount items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(files, key = { it.id }) { file ->
                TmdbPosterCard(
                    file = file,
                    metadata = tmdbMetadata[file.id],
                    onClick = { onNavigateToInfo(file.id, mediaType) },
                    modifier = Modifier.width(130.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ──── TMDB Poster Card (compact poster with name, year, tag) ────

@Composable
fun TmdbPosterCard(
    file: MediaFileEntity,
    metadata: TmdbMetadataEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Poster area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (metadata?.posterPath != null) {
                    AsyncImage(
                        model = metadata.posterPath,
                        contentDescription = metadata.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (file.thumbnailLink != null) {
                    AsyncImage(
                        model = file.thumbnailLink,
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Movie/Series tag badge
                metadata?.let { meta ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (meta.mediaType == "tv") Color(0xFF2196F3).copy(alpha = 0.9f)
                                else Color(0xFFE91E63).copy(alpha = 0.9f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (meta.mediaType == "tv") "TV" else "Movie",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Rating badge
                metadata?.rating?.let { rating ->
                    if (rating > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        rating >= 7f -> Color(0xFF4CAF50)
                                        rating >= 5f -> Color(0xFFFF9800)
                                        else -> Color(0xFFF44336)
                                    }.copy(alpha = 0.9f)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "%.1f".format(rating),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Title + Year
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = metadata?.title ?: file.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                metadata?.year?.let { year ->
                    Text(
                        text = year,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

// ──── Shimmer / Skeleton ────

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer"
    )
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim)
    )
}

@Composable
private fun ShimmerBox(modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(shimmerBrush()))
}

@Composable
fun HomeSkeletonLoading(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Continue Playing skeleton
        item {
            Column {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShimmerBox(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    ShimmerBox(Modifier.width(140.dp).height(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(3) {
                        Card(
                            modifier = Modifier.width(200.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column {
                                ShimmerBox(Modifier.fillMaxWidth().height(110.dp))
                                Column(modifier = Modifier.padding(10.dp)) {
                                    ShimmerBox(Modifier.fillMaxWidth().height(14.dp))
                                    Spacer(Modifier.height(6.dp))
                                    ShimmerBox(Modifier.fillMaxWidth(0.7f).height(10.dp))
                                    Spacer(Modifier.height(6.dp))
                                    ShimmerBox(Modifier.fillMaxWidth().height(3.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        // Category skeleton x2
        items(2) {
            Column {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShimmerBox(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    ShimmerBox(Modifier.width(100.dp).height(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(4) {
                        Card(
                            modifier = Modifier.width(130.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column {
                                ShimmerBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                                Column(modifier = Modifier.padding(8.dp)) {
                                    ShimmerBox(Modifier.fillMaxWidth().height(14.dp))
                                    Spacer(Modifier.height(4.dp))
                                    ShimmerBox(Modifier.fillMaxWidth(0.5f).height(10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
