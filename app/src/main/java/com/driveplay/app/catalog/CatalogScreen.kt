package com.driveplay.app.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.driveplay.app.player.mpv.PlayerEngine
import com.driveplay.app.ui.components.*
import androidx.compose.ui.res.painterResource
import com.driveplay.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onPlayFile: (fileId: String, fileName: String, engine: PlayerEngine) -> Unit,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInfo: (driveFileId: String, mediaType: String) -> Unit = { _, _ -> },
    onNavigateToSeeAll: (category: String) -> Unit = {},
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Home, 1=Folders

    // Load home content when Home tab is selected
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            viewModel.refreshPreferences()
            viewModel.loadHomeContent()
        }
    }

    // Re-read prefs every time screen recomposes (e.g. returning from Settings)
    LaunchedEffect(Unit) {
        viewModel.refreshPreferences()
    }

    BackHandler(enabled = uiState.folderStack.isNotEmpty() && selectedTab == 1) {
        viewModel.navigateBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        Column {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = {
                                    Text(
                                        if (uiState.searchMode == SearchMode.ALL_DRIVES)
                                            "Search all drives..."
                                        else "Search current drive...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
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
                                }
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "StreamHive",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.updateSearchQuery("")
                    }) {
                        Icon(
                            if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            "Search", tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!showSearch) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { viewModel.logout(); onLogout() }) {
                            Icon(Icons.Default.Logout, "Logout", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (!showSearch) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Folder, "Folders") },
                        label = { Text("Folders") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search mode chips (visible only when searching)
            if (showSearch && uiState.searchQuery.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.searchMode == SearchMode.CURRENT_DRIVE,
                        onClick = { viewModel.setSearchMode(SearchMode.CURRENT_DRIVE) },
                        label = { Text("Current Drive") },
                        leadingIcon = if (uiState.searchMode == SearchMode.CURRENT_DRIVE) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = uiState.searchMode == SearchMode.ALL_DRIVES,
                        onClick = { viewModel.setSearchMode(SearchMode.ALL_DRIVES) },
                        label = { Text("All Drives") },
                        leadingIcon = if (uiState.searchMode == SearchMode.ALL_DRIVES) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // Search results
            if (showSearch && uiState.isSearching) {
                SearchResultsView(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPlayFile = onPlayFile,
                    onFolderNavigate = { folder ->
                        showSearch = false
                        viewModel.updateSearchQuery("")
                        selectedTab = 1 // Switch to Folders tab
                        viewModel.openFolder(folder.id, folder.name)
                    }
                )
                return@Column
            }

            // Tab content
            when (selectedTab) {
                0 -> HomeTab(
                    state = uiState,
                    onPlayFile = onPlayFile,
                    onNavigateToSettings = onNavigateToSettings,
                    onClearHistory = viewModel::clearPlaybackHistory,
                    onNavigateToInfo = onNavigateToInfo,
                    onRemoveFromContinue = viewModel::removeFromHistory,
                    onNavigateToSeeAll = onNavigateToSeeAll
                )
                1 -> FoldersTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPlayFile = onPlayFile
                )
            }
        }
    }
}

@Composable
private fun FoldersTab(
    uiState: CatalogUiState,
    viewModel: CatalogViewModel,
    onPlayFile: (String, String, PlayerEngine) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Drive selector
        if (uiState.sharedDrives.isNotEmpty() || uiState.driveSections.isNotEmpty()) {
            DriveSelector(
                drives = uiState.sharedDrives,
                selectedDrive = uiState.selectedDrive,
                onDriveSelected = { viewModel.selectDrive(it) },
                sections = uiState.driveSections,
                selectedSection = uiState.selectedSection,
                onSectionSelected = { viewModel.selectSection(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Breadcrumb
        if (uiState.selectedDrive != null && uiState.folderStack.isNotEmpty()) {
            FolderBreadcrumb(
                driveName = uiState.selectedDrive!!.name,
                folderStack = uiState.folderStack,
                onNavigateToRoot = viewModel::navigateToRoot,
                onNavigateToIndex = viewModel::navigateToFolderIndex,
                isLoading = uiState.isNavigating
            )
        }

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

        // Engine chip
        if (uiState.isMpvAvailable) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = viewModel::toggleEngine,
                    label = {
                        Text(
                            when (uiState.selectedEngine) {
                                PlayerEngine.EXO_PLAYER -> "ExoPlayer"
                                PlayerEngine.MPV -> "MPV"
                                PlayerEngine.EXTERNAL -> "External"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
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
        }

        // Files grid
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading && uiState.files.isEmpty() -> {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        message = "Loading catalog..."
                    )
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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.files, key = { it.id }) { file ->
                            MediaCard(
                                file = file,
                                tmdbMetadata = null, // No TMDB in folder view
                                onClick = {
                                    if (file.isFolder) {
                                        viewModel.openFolder(file.id, file.name)
                                    } else {
                                        onPlayFile(file.id, file.name, uiState.selectedEngine)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsView(
    uiState: CatalogUiState,
    viewModel: CatalogViewModel,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    onFolderNavigate: (com.driveplay.app.data.db.MediaFileEntity) -> Unit = {}
) {
    if (uiState.files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.SearchOff, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        return
    }

    if (uiState.searchMode == SearchMode.ALL_DRIVES && uiState.searchResults.isNotEmpty()) {
        // Grouped by drive
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.searchResults.forEach { (driveId, files) ->
                item {
                    Text(
                        viewModel.getDriveName(driveId),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(files, key = { it.id }) { file ->
                    SearchResultItem(
                        file = file,
                        onClick = {
                            if (file.isFolder) {
                                onFolderNavigate(file)
                            } else {
                                onPlayFile(file.id, file.name, uiState.selectedEngine)
                            }
                        }
                    )
                }
            }
        }
    } else {
        // Flat list
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.files, key = { it.id }) { file ->
                MediaCard(
                    file = file,
                    tmdbMetadata = uiState.tmdbMetadata[file.id],
                    onClick = {
                        if (file.isFolder) {
                            onFolderNavigate(file)
                        } else {
                            onPlayFile(file.id, file.name, uiState.selectedEngine)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    file: com.driveplay.app.data.db.MediaFileEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    maxLines = 1
                )
                if (file.fileExtension != null) {
                    Text(
                        file.fileExtension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.PlayArrow, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
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
