package com.mkbhdana.streamhive.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TmdbSeeAllScreen(
    title: String,
    defaultMediaType: String,
    files: List<MediaFileEntity>,
    fileMediaTypes: Map<String, String> = emptyMap(),
    tmdbMetadata: Map<String, TmdbMetadataEntity>,
    onBack: () -> Unit,
    onNavigateToInfo: (String, String) -> Unit
) {
    val sorted = files.sortedByDescending { it.modifiedTime ?: "" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "${sorted.size} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (sorted.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No items found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sorted, key = { it.id }) { file ->
                SeeAllPosterCard(
                    file = file,
                    metadata = tmdbMetadata[file.id],
                    onClick = {
                        val mediaType = fileMediaTypes[file.id]
                            ?: tmdbMetadata[file.id]?.mediaType
                            ?: defaultMediaType
                        onNavigateToInfo(file.id, mediaType)
                    }
                )
            }
        }
    }
}

@Composable
private fun SeeAllPosterCard(
    file: MediaFileEntity,
    metadata: TmdbMetadataEntity?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Poster image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (metadata?.posterPath != null) {
                    AsyncImage(
                        model = com.mkbhdana.streamhive.ui.image.PosterSource
                            .posterModel(metadata, file, size = "w500"),
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
                        modifier = Modifier.fillMaxSize(),
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
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Text(
                    text = metadata?.title ?: file.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
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
