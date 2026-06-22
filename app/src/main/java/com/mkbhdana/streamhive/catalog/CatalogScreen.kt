package com.mkbhdana.streamhive.catalog

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.mkbhdana.streamhive.ui.components.*
import com.mkbhdana.streamhive.util.FileUtils
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ScopedFolderSearchView(
    uiState: CatalogUiState,
    viewModel: CatalogViewModel,
    focusRequestSignal: Int,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    onFolderOpen: (MediaFileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val isGridView = uiState.isGridView
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var itemDetails by remember { mutableStateOf<Pair<String, Long?>?>(null) }
    val folderResults = uiState.scopedSearchResults.filter { it.isFolder }
    val fileResults = uiState.scopedSearchResults.filter { !it.isFolder }

    LaunchedEffect(focusRequestSignal) {
        delay(120)
        searchFocusRequester.requestFocus()
        keyboardController?.show()
    }

    itemDetails?.let { details ->
        val sizeText = FileUtils.formatFileSize(details.second).takeIf { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = { itemDetails = null },
            title = { Text("Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(details.first, style = MaterialTheme.typography.bodyMedium)
                    if (sizeText != null) {
                        Text(
                            "Size: $sizeText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { itemDetails = null }) { Text("OK") } },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        val currentScopeName = uiState.folderStack.lastOrNull()?.name
            ?: uiState.selectedDrive?.name
            ?: "current folder"

        OutlinedTextField(
            value = uiState.scopedSearchQuery,
            onValueChange = viewModel::updateScopedSearchQuery,
            placeholder = {
                Text(
                    "Search $currentScopeName...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .focusRequester(searchFocusRequester)
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                if (uiState.scopedSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateScopedSearchQuery("") }) {
                        Icon(Icons.Default.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.isExactSearch,
                onClick = viewModel::toggleScopedExactSearch,
                label = { Text("Exact") },
                leadingIcon = if (uiState.isExactSearch) {
                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                } else null
            )
        }

        if (uiState.isScopedSearchLoading) {
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

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !uiState.isScopedSearching -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ManageSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Search current folder",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                uiState.isScopedSearchLoading && uiState.scopedSearchResults.isEmpty() -> {
                    LoadingIndicator(
                        modifier = Modifier.fillMaxSize(),
                        message = "Searching..."
                    )
                }
                uiState.scopedSearchResults.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                isGridView -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (folderResults.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SearchSectionHeader(
                                    title = "Folders",
                                    icon = Icons.Default.Folder,
                                    count = folderResults.size
                                )
                            }
                            items(folderResults, key = { "folder-${it.id}" }) { file ->
                                FolderCard(
                                    name = file.name,
                                    onClick = { onFolderOpen(file) },
                                    onLongClick = { itemDetails = file.name to null }
                                )
                            }
                        }

                        if (fileResults.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SearchSectionHeader(
                                    title = "Files",
                                    icon = Icons.Default.VideoFile,
                                    count = fileResults.size
                                )
                            }
                            items(fileResults, key = { "file-${it.id}" }) { file ->
                                MediaCard(
                                    file = file,
                                    tmdbMetadata = null,
                                    onClick = { onPlayFile(file.id, file.name, viewModel.getPreferredEngine()) },
                                    onLongClick = { itemDetails = file.name to file.size }
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (folderResults.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = "Folders",
                                    icon = Icons.Default.Folder,
                                    count = folderResults.size
                                )
                            }
                            items(folderResults, key = { "folder-${it.id}" }) { file ->
                                SearchResultItem(
                                    file = file,
                                    onClick = { onFolderOpen(file) },
                                    onLongClick = { itemDetails = file.name to null }
                                )
                            }
                        }

                        if (fileResults.isNotEmpty()) {
                            item {
                                SearchSectionHeader(
                                    title = "Files",
                                    icon = Icons.Default.VideoFile,
                                    count = fileResults.size
                                )
                            }
                        }
                        items(fileResults, key = { "file-${it.id}" }) { file ->
                            SearchResultItem(
                                file = file,
                                onClick = {
                                    onPlayFile(file.id, file.name, viewModel.getPreferredEngine())
                                },
                                onLongClick = { itemDetails = file.name to file.size }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    icon: ImageVector,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$count items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FoldersTab(
    uiState: CatalogUiState,
    viewModel: CatalogViewModel,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    modifier: Modifier = Modifier
) {
    val isGridView = uiState.isGridView
    var itemDetails by remember { mutableStateOf<Pair<String, Long?>?>(null) }

    // Long-press tooltip dialog
    itemDetails?.let { details ->
        val sizeText = FileUtils.formatFileSize(details.second).takeIf { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = { itemDetails = null },
            title = { Text("Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(details.first, style = MaterialTheme.typography.bodyMedium)
                    if (sizeText != null) {
                        Text(
                            "Size: $sizeText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { itemDetails = null }) { Text("OK") } },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Breadcrumb
        FolderBreadcrumb(
            driveName = uiState.selectedDrive?.name ?: "All Drives",
            folderStack = uiState.folderStack,
            onNavigateToRoot = viewModel::navigateToRoot,
            onNavigateToIndex = viewModel::navigateToFolderIndex,
            onNavigateToHome = if (uiState.selectedDrive != null) viewModel::clearSelectedDrive else null,
            isLoading = uiState.isNavigating
        )

        // Refreshing indicator (subtle)
        AnimatedVisibility(visible = uiState.isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Error banner
        ErrorBanner(uiState.error, viewModel::clearError)

        // Folder-only external playback toggle.
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.selectedDrive != null) {
                FilterChip(
                    selected = uiState.playFolderFilesExternally,
                    onClick = viewModel::toggleFolderExternalPlayback,
                    label = {
                        Text("External", style = MaterialTheme.typography.labelSmall)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.SmartDisplay, null, Modifier.size(16.dp))
                    }
                )
                AssistChip(
                    onClick = viewModel::refresh,
                    label = { Text("Refresh", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)) }
                )
            }
            AssistChip(
                onClick = { viewModel.toggleGridView() },
                label = { Text(if (isGridView) "List View" else "Grid View", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView, null, Modifier.size(16.dp)) }
            )
        }

        // Files grid - conditionally wrapped in PullToRefreshBox
        val filesGridContent = @Composable {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // Offline with no cached files → centered "No Connectivity"
                    uiState.isOffline && uiState.files.isEmpty() && uiState.selectedDrive != null && !uiState.isLoading -> {
                        NoConnectivityMessage(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.isLoading && uiState.files.isEmpty() && uiState.selectedDrive != null -> {
                        LoadingIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            message = "Loading catalog..."
                        )
                    }
                    uiState.selectedDrive == null -> {
                        // Show Drives as Folders
                        if (isGridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 100.dp),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.sharedDrives, key = { it.id }) { drive ->
                                    FolderCard(
                                        name = drive.name,
                                        onClick = { viewModel.selectDrive(drive) }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(uiState.sharedDrives, key = { it.id }) { drive ->
                                    SearchResultItem(
                                        file = com.mkbhdana.streamhive.data.db.MediaFileEntity(
                                            id = drive.id,
                                            name = drive.name,
                                            mimeType = "application/vnd.google-apps.folder",
                                            driveId = drive.id,
                                            isFolder = true
                                        ),
                                        onClick = { viewModel.selectDrive(drive) },
                                        onLongClick = { itemDetails = drive.name to null }
                                    )
                                }
                            }
                        }
                    }
                    uiState.files.isEmpty() && !uiState.isLoading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.VideoLibrary, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No video files in this folder",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    else -> {
                        if (isGridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 100.dp),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(uiState.files, key = { it.id }) { file ->
                                    if (file.isFolder) {
                                        FolderCard(
                                            name = file.name,
                                            onClick = { viewModel.openFolder(file.id, file.name) },
                                            onLongClick = { itemDetails = file.name to null }
                                        )
                                    } else {
                                        MediaCard(
                                            file = file,
                                            tmdbMetadata = null,
                                            onClick = {
                                                onPlayFile(file.id, file.name, folderPlaybackEngine(uiState, viewModel))
                                            },
                                            onLongClick = { itemDetails = file.name to file.size }
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(uiState.files, key = { it.id }) { file ->
                                    SearchResultItem(
                                        file = file,
                                        onClick = {
                                            if (file.isFolder) {
                                                viewModel.openFolder(file.id, file.name)
                                            } else {
                                                onPlayFile(file.id, file.name, folderPlaybackEngine(uiState, viewModel))
                                            }
                                        },
                                        onLongClick = {
                                            itemDetails = file.name to if (!file.isFolder) file.size else null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } // end filesGridContent

        if (uiState.selectedDrive == null) {
            filesGridContent()
        } else {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                filesGridContent()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top half
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(64.dp)
                )
            }
            
            // Bottom half
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun folderPlaybackEngine(uiState: CatalogUiState, viewModel: CatalogViewModel): PlayerEngine {
    return if (uiState.playFolderFilesExternally) PlayerEngine.EXTERNAL else viewModel.getPreferredEngine()
}


@Composable
private fun ErrorBanner(error: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(visible = error != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning, null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
