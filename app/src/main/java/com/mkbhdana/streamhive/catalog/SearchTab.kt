package com.mkbhdana.streamhive.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.ui.components.LoadingIndicator
import com.mkbhdana.streamhive.ui.components.MediaCard
import com.mkbhdana.streamhive.ui.components.FolderBreadcrumb
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchTab(
    state: CatalogUiState,
    viewModel: CatalogViewModel,
    focusRequestSignal: Int = 0,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    onFolderNavigate: (MediaFileEntity) -> Unit,
    onNavigateToInfo: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isGridView = state.isGridView
    var tooltipName by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf<String?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusRequestSignal, selectedSection, state.searchFolderStack.isEmpty()) {
        if (selectedSection == null && state.searchFolderStack.isEmpty()) {
            delay(120)
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BackHandler(enabled = selectedSection != null || state.searchFolderStack.isNotEmpty()) {
        if (state.searchFolderStack.isNotEmpty()) {
            viewModel.navigateBackSearchFolder()
        } else {
            selectedSection = null
        }
    }

    val allResults = if (state.searchMode == SearchMode.ALL_DRIVES) {
        state.searchResults.values.flatten()
    } else {
        state.currentDriveSearchResults
    }

    // Process TMDB, Folders, Files using cached TMDB metadata
    // Only show files in TMDB section if they belong to a TMDB-configured folder
    val tmdbFolderIds = state.tmdbConfiguredFolderIds
    val tmdbResults = allResults.filter {
        state.tmdbMetadata.containsKey(it.id) && it.parentId in tmdbFolderIds
    }
    val folderResults = allResults.filter { it.isFolder && it !in tmdbResults }
    val fileResults = allResults.filter { !it.isFolder && it !in tmdbResults }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.searchFolderStack.isNotEmpty()) {
            // Render Isolated Folder Browser
            Column(modifier = Modifier.fillMaxSize()) {
                FolderBreadcrumb(
                    driveName = "Search Results",
                    folderStack = state.searchFolderStack.map { FolderInfo(it.id, it.name) },
                    onNavigateToRoot = { viewModel.clearSearchFolderStack() },
                    onNavigateToIndex = { viewModel.navigateToSearchFolderIndex(it) }
                )

                if (state.isSearchFolderLoading) {
                    LoadingIndicator(modifier = Modifier.fillMaxSize(), message = "Loading folder...")
                } else if (state.searchFolderFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Folder is empty",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.searchFolderFiles, key = { it.id }) { file ->
                            MediaCard(
                                file = file,
                                tmdbMetadata = null,
                                onClick = {
                                    if (file.isFolder) {
                                        viewModel.openSearchFolder(file.id, file.name, file.driveId)
                                    } else {
                                        onPlayFile(file.id, file.name, viewModel.getPreferredEngine())
                                    }
                                },
                                onLongClick = { tooltipName = file.name },
                                subtitle = null
                            )
                        }
                    }
                }
            }
        } else {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Hide Search Bar when in See All mode
            if (selectedSection == null) {
                // Search Bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder = {
                        Text(
                            if (state.searchMode == SearchMode.ALL_DRIVES) "Search all drives..." else "Search current drive...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester)
                        .padding(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                )

                // Loading Indicator
                if (state.isSearchLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.searchMode == SearchMode.CURRENT_DRIVE,
                            onClick = { viewModel.setSearchMode(SearchMode.CURRENT_DRIVE) },
                            label = { Text("Current Drive") },
                            leadingIcon = if (state.searchMode == SearchMode.CURRENT_DRIVE) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = state.searchMode == SearchMode.ALL_DRIVES,
                            onClick = { viewModel.setSearchMode(SearchMode.ALL_DRIVES) },
                            label = { Text("All Drives") },
                            leadingIcon = if (state.searchMode == SearchMode.ALL_DRIVES) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            } else {
                // Header for See All
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedSection = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (selectedSection) {
                            "tmdb" -> "TMDB Results"
                            "folders" -> "Folder Results"
                            "files" -> "File Results"
                            else -> "Results"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    if (selectedSection != "tmdb") {
                        IconButton(onClick = { viewModel.toggleGridView() }) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle View",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Search Results Content
            if (state.isSearching || selectedSection != null) {
                if (allResults.isEmpty() && !state.isSearchLoading && selectedSection == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No results found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else if (state.isSearchLoading && allResults.isEmpty() && selectedSection == null) {
                    LoadingIndicator(
                        modifier = Modifier.fillMaxSize(),
                        message = "Searching..."
                    )
                } else {
                    if (selectedSection != null) {
                        // View All mode for specific section
                        val itemsToList = when (selectedSection) {
                            "tmdb" -> tmdbResults
                            "folders" -> folderResults
                            "files" -> fileResults
                            else -> emptyList()
                        }
                        
                        if (selectedSection == "tmdb") {
                            // TMDB is always grid
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 130.dp),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(itemsToList, key = { it.id }) { file ->
                                    val mediaType = state.tmdbMetadata[file.id]?.mediaType ?: "auto"
                                    TmdbPosterCard(
                                        file = file,
                                        metadata = state.tmdbMetadata[file.id],
                                        onClick = { onNavigateToInfo(file.id, mediaType) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            // Folders or Files (support toggle)
                            if (isGridView) {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 160.dp),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(itemsToList, key = { it.id }) { file ->
                                        MediaCard(
                                            file = file,
                                            tmdbMetadata = null,
                                            onClick = {
                                                if (file.isFolder) onFolderNavigate(file)
                                                else onPlayFile(file.id, file.name, viewModel.getPreferredEngine())
                                            },
                                            onLongClick = { tooltipName = file.name },
                                            subtitle = if (state.searchMode == SearchMode.ALL_DRIVES) viewModel.getDriveName(file.driveId) else null
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(itemsToList, key = { it.id }) { file ->
                                        SearchResultItem(
                                            file = file,
                                            onClick = {
                                                if (file.isFolder) onFolderNavigate(file)
                                                else onPlayFile(file.id, file.name, viewModel.getPreferredEngine())
                                            },
                                            onLongClick = { tooltipName = file.name },
                                            subtitle = if (state.searchMode == SearchMode.ALL_DRIVES) viewModel.getDriveName(file.driveId) else null
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Main Search View (3 horizontal sections)
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            if (tmdbResults.isNotEmpty()) {
                                item {
                                    TmdbHorizontalSection(
                                        title = "TMDB Search",
                                        icon = Icons.Default.Movie,
                                        files = tmdbResults.take(10),
                                        totalCount = tmdbResults.size,
                                        tmdbMetadata = state.tmdbMetadata,
                                        mediaType = "auto",
                                        onNavigateToInfo = onNavigateToInfo,
                                        onSeeAll = { selectedSection = "tmdb" }
                                    )
                                }
                            }
                            
                            if (folderResults.isNotEmpty()) {
                                item {
                                    HorizontalSearchResultSection(
                                        title = "Folders",
                                        icon = Icons.Default.Folder,
                                        files = folderResults.take(10),
                                        totalCount = folderResults.size,
                                        onSeeAll = { selectedSection = "folders" },
                                        onClick = { onFolderNavigate(it) },
                                        onLongClick = { tooltipName = it.name },
                                        showDriveName = state.searchMode == SearchMode.ALL_DRIVES,
                                        getDriveName = viewModel::getDriveName
                                    )
                                }
                            }

                            if (fileResults.isNotEmpty()) {
                                item {
                                    HorizontalSearchResultSection(
                                        title = "Files",
                                        icon = Icons.Default.VideoFile,
                                        files = fileResults.take(10),
                                        totalCount = fileResults.size,
                                        onSeeAll = { selectedSection = "files" },
                                        onClick = { onPlayFile(it.id, it.name, viewModel.getPreferredEngine()) },
                                        onLongClick = { tooltipName = it.name },
                                        showDriveName = state.searchMode == SearchMode.ALL_DRIVES,
                                        getDriveName = viewModel::getDriveName
                                    )
                                }
                            }
                            
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                        }
                    }
                }
            } else {
                // Initial Placeholder State
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MovieFilter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Search for your favorite content",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        } // closes Column
        } // closes else block

        tooltipName?.let { name ->
            androidx.compose.ui.window.Popup(
                alignment = Alignment.Center,
                onDismissRequest = { tooltipName = null }
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = name,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalSearchResultSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    files: List<MediaFileEntity>,
    totalCount: Int,
    onSeeAll: () -> Unit,
    onClick: (MediaFileEntity) -> Unit,
    onLongClick: (MediaFileEntity) -> Unit,
    showDriveName: Boolean,
    getDriveName: (String) -> String
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
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
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
                SearchResultItem(
                    file = file,
                    onClick = { onClick(file) },
                    onLongClick = { onLongClick(file) },
                    subtitle = if (showDriveName) getDriveName(file.driveId) else null,
                    modifier = Modifier.width(280.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultItem(
    file: MediaFileEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    subtitle: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Card(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (file.isFolder) Icons.Default.Folder else Icons.Default.VideoFile,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (file.fileExtension != null) {
                        Text(
                            file.fileExtension.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (subtitle != null) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
