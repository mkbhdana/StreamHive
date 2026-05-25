package com.mkbhdana.streamhive.catalog.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.util.FileUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoScreen(
    onBack: () -> Unit,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    navKey: com.mkbhdana.streamhive.navigation.MediaInfoRoute = com.mkbhdana.streamhive.navigation.MediaInfoRoute(""),
    viewModel: MediaInfoViewModel = hiltViewModel<MediaInfoViewModel, MediaInfoViewModel.Factory>(
        creationCallback = { factory -> factory.create(navKey) }
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFixMetadataDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.setSourcePriorityFilteringEnabled(true)
    }

    if (uiState.isLoading) {
        MediaInfoSkeletonLoading(onBack = onBack)
        return
    }

    val meta = uiState.metadata

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refreshFiles() },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // ──── Backdrop header ────
        item {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                if (meta?.backdropPath != null) {
                    AsyncImage(
                        model = meta.backdropPath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.background
                                ),
                                startY = 100f
                            )
                        )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.statusBarsPadding().padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            }
        }

        // ──── Title + metadata row ────
        if (meta != null) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-40).dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Poster
                    if (meta.posterPath != null) {
                        AsyncImage(
                            model = meta.posterPath,
                            contentDescription = null,
                            modifier = Modifier
                                .width(100.dp)
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            meta.title.ifBlank {
                                uiState.allDriveFiles.firstOrNull()?.name
                                    ?: uiState.driveFiles.firstOrNull()?.name
                                    ?: "Details"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            meta.year?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            meta.rating?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Star, null,
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("%.1f".format(it), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Text(
                                if (meta.mediaType == "tv") "Series" else "Movie",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            // Edit icon for fixing metadata
                            IconButton(
                                onClick = { showFixMetadataDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit, "Fix metadata",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ──── Overview ────
            item {
                Text(
                    meta.overview?.takeIf { it.isNotBlank() } ?: "No description available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-24).dp)
                )
            }
        } else {
            // ──── No metadata header ────
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "No TMDB Metadata",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap the edit icon to set a TMDB or IMDB ID",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showFixMetadataDialog = true }) {
                        Icon(
                            Icons.Default.Edit, "Fix metadata",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (uiState.sourcePriorityConfigured) {
            item {
                SourcePriorityStatusRow(
                    summary = uiState.sourcePrioritySummary.orEmpty(),
                    enabled = !uiState.sourcePriorityTemporarilyDisabled,
                    onEnabledChange = { enabled ->
                        viewModel.setSourcePriorityTemporarilyDisabled(!enabled)
                    }
                )
            }
        }

        // ──── TV Show: Season-grouped file listing ────
        if (meta?.mediaType == "tv" && uiState.fileSeasons.isNotEmpty()) {
            uiState.fileSeasons.forEach { season ->
                item(key = "season_header_${season.seasonNumber}") {
                    SeasonHeader(
                        season = season,
                        isExpanded = uiState.expandedSeason == season.seasonNumber,
                        onToggle = { viewModel.toggleSeasonExpanded(season.seasonNumber) }
                    )
                }
                // Show files under expanded season
                if (uiState.expandedSeason == season.seasonNumber) {
                    items(season.files, key = { "file_${it.id}" }) { file ->
                        FileListItem(
                            file = file,
                            onClick = { onPlayFile(file.id, file.name, uiState.preferredEngine) }
                        )
                    }
                }
            }
        } else {
            // ──── Movie / no-metadata: flat file list with header ────
            if (uiState.driveFiles.isNotEmpty()) {
                item {
                    Text(
                        "Files (${uiState.driveFiles.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(uiState.driveFiles, key = { "file_${it.id}" }) { file ->
                    FileListItem(
                        file = file,
                        onClick = { onPlayFile(file.id, file.name, uiState.preferredEngine) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
    } // end PullToRefreshBox

    // ──── Fix Metadata Dialog ────
    if (showFixMetadataDialog) {
        FixMetadataDialog(
            onDismiss = { showFixMetadataDialog = false },
            onSubmit = { idInput ->
                viewModel.fixMetadata(idInput)
                showFixMetadataDialog = false
            }
        )
    }
}

@Composable
private fun SourcePriorityStatusRow(
    summary: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Source Priority",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = summary.ifBlank {
                        if (enabled) "Priority on" else "Priority off. Showing all files."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

// ──── Season Header ────

@Composable
private fun SeasonHeader(
    season: FileSeason,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            season.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${season.files.size} files",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ──── File List Item (matches screenshot style) ────

@Composable
private fun FileListItem(
    file: MediaFileEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play circle icon
        Icon(
            Icons.Default.PlayCircle, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.width(12.dp))

        // File info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                // Extension badge
                file.fileExtension?.let { ext ->
                    Text(
                        ext.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                // File size
                file.size?.let { size ->
                    if (size > 0) {
                        Text(
                            FileUtils.formatFileSize(size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ──── Fix Metadata Dialog ────

@Composable
private fun FixMetadataDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var idInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Fix Metadata", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Enter a TMDB ID (numeric) or IMDB ID (e.g. tt1234567) to fix the metadata for this item.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = idInput,
                    onValueChange = { idInput = it },
                    label = { Text("TMDB / IMDB ID") },
                    placeholder = { Text("e.g. 12345 or tt1234567") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (idInput.isNotBlank()) onSubmit(idInput.trim()) },
                enabled = idInput.isNotBlank()
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

// ──── Skeleton Loading ────

@Composable
private fun infoShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "info_shimmer")
    val anim by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "info_shimmer"
    )
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset(anim - 500f, anim - 500f),
        end = Offset(anim, anim)
    )
}

@Composable
private fun InfoShimmerBox(modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(infoShimmerBrush()))
}

@Composable
private fun MediaInfoSkeletonLoading(onBack: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Backdrop skeleton
        item {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                InfoShimmerBox(Modifier.fillMaxSize())
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.background
                            ), startY = 100f
                        )
                    )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.statusBarsPadding().padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
            }
        }
        // Poster + title skeleton
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-40).dp),
                verticalAlignment = Alignment.Bottom
            ) {
                InfoShimmerBox(Modifier.width(100.dp).height(150.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    InfoShimmerBox(Modifier.fillMaxWidth(0.8f).height(24.dp))
                    Spacer(Modifier.height(8.dp))
                    InfoShimmerBox(Modifier.fillMaxWidth(0.5f).height(16.dp))
                    Spacer(Modifier.height(8.dp))
                    InfoShimmerBox(Modifier.fillMaxWidth(0.3f).height(14.dp))
                }
            }
        }
        // Overview skeleton
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-24).dp)) {
                InfoShimmerBox(Modifier.fillMaxWidth().height(12.dp))
                Spacer(Modifier.height(6.dp))
                InfoShimmerBox(Modifier.fillMaxWidth(0.9f).height(12.dp))
                Spacer(Modifier.height(6.dp))
                InfoShimmerBox(Modifier.fillMaxWidth(0.75f).height(12.dp))
            }
        }
        // File list skeleton
        item {
            InfoShimmerBox(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(100.dp).height(16.dp)
            )
        }
        items(5) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoShimmerBox(Modifier.size(36.dp).clip(CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    InfoShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
                    Spacer(Modifier.height(4.dp))
                    InfoShimmerBox(Modifier.fillMaxWidth(0.4f).height(10.dp))
                }
            }
        }
    }
}
