package com.mkbhdana.streamhive.catalog

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.ui.components.*
import androidx.compose.ui.res.painterResource
import com.mkbhdana.streamhive.R
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun CatalogScreen(
    onPlayFile: (fileId: String, fileName: String, engine: PlayerEngine) -> Unit,
    onPlayFileWithDecoder: (
        fileId: String,
        fileName: String,
        engine: PlayerEngine,
        decoderMode: String?
    ) -> Unit = { fileId, fileName, engine, _ -> onPlayFile(fileId, fileName, engine) },
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInfo: (driveFileId: String, mediaType: String) -> Unit = { _, _ -> },
    onNavigateToSeeAll: (category: String) -> Unit = {},
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // showSearch has been migrated to SearchTab
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0=Home, 1=Folders
    var searchFocusRequest by rememberSaveable { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val screenContext = LocalContext.current
    val homeListState = rememberLazyListState()

    // Smooth scroll-based app bar opacity: fully transparent at top, fully opaque after 400px scroll
    val appBarAlpha by remember {
        derivedStateOf {
            if (selectedTab != 0) 1f
            else when {
                homeListState.firstVisibleItemIndex > 0 -> 1f
                else -> (homeListState.firstVisibleItemScrollOffset / 400f).coerceIn(0f, 1f)
            }
        }
    }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val appBarColor = surfaceColor.copy(alpha = appBarAlpha)



    // Refresh preferences when Home tab is selected
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            viewModel.refreshPreferences()
        }
    }

    // Re-read prefs every time screen recomposes (e.g. returning from Settings)
    LaunchedEffect(Unit) {
        viewModel.refreshPreferences()
    }

    DisposableEffect(lifecycleOwner, selectedTab) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && selectedTab == 0) {
                viewModel.refreshPreferences()
                viewModel.loadHomeContent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showExitDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val activity = (screenContext as? android.app.Activity)

    BackHandler(enabled = true) {
        if (uiState.folderStack.isNotEmpty() && selectedTab == 1) {
            viewModel.navigateBack()
        } else if (selectedTab == 1 || selectedTab == 2) {
            selectedTab = 0
        } else {
            showExitDialog = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
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
                },
                actions = {
                    if (selectedTab == 0) {
                        // Animated refresh button for Home tab
                        val scope = rememberCoroutineScope()
                        val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
                        
                        IconButton(onClick = { 
                            if (!rotation.isRunning) {
                                scope.launch {
                                    rotation.snapTo(0f)
                                    rotation.animateTo(
                                        targetValue = 360f,
                                        animationSpec = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.LinearEasing)
                                    )
                                }
                            }
                            viewModel.refreshHomeContent(fromSwipe = false) 
                        }) {
                            Icon(
                                Icons.Default.Refresh, "Refresh",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.rotate(rotation.value)
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.Logout, "Logout", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarColor
                )
            )
        },
        bottomBar = {
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
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        searchFocusRequest++
                    },
                    icon = { Icon(Icons.Default.Search, "Search") },
                    label = { Text("Search") }
                )
            }
        }
    ) { paddingValues ->
        val scope = rememberCoroutineScope()
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                icon = { Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Exit App") },
                text = { Text("Are you sure you want to exit?") },
                confirmButton = {
                    TextButton(onClick = { activity?.finish() }) {
                        Text("Exit", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                icon = { Icon(Icons.Default.Logout, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Logout") },
                text = { Text("Are you sure you want to log out of StreamHive? You will need to sign in again.") },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                        onLogout()
                    }) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }



        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Tab content
            when (selectedTab) {
                0 -> {
                    // Pull-to-refresh for HomeTab -> same logic as refresh icon
                    // Only apply bottom padding (nav bar) so hero extends behind transparent app bar
                    PullToRefreshBox(
                        isRefreshing = uiState.isHomeRefreshing,
                        onRefresh = { viewModel.refreshHomeContent(fromSwipe = true) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = paddingValues.calculateBottomPadding())
                    ) {
                        HomeTab(
                            state = uiState,
                            onPlayFile = onPlayFile,
                            onPlayFileWithDecoder = onPlayFileWithDecoder,
                            onNavigateToSettings = onNavigateToSettings,
                            onClearHistory = viewModel::clearPlaybackHistory,
                            onNavigateToInfo = onNavigateToInfo,
                            onRemoveFromContinue = viewModel::removeFromHistory,
                            onPlayFromStart = { fileId, fileName, engine, decoderMode ->
                                scope.launch {
                                    viewModel.removeFromHistorySync(fileId)
                                    onPlayFileWithDecoder(fileId, fileName, engine, decoderMode)
                                }
                            },
                            onNavigateToSeeAll = onNavigateToSeeAll,
                            homeListState = homeListState
                        )
                    }
                }
                1 -> {
                    Box(modifier = Modifier.padding(paddingValues)) {
                        FoldersTab(
                            uiState = uiState,
                            viewModel = viewModel,
                            onPlayFile = onPlayFile
                        )
                    }
                }
                2 -> {
                    Box(modifier = Modifier.padding(paddingValues)) {
                        SearchTab(
                            state = uiState,
                            viewModel = viewModel,
                            focusRequestSignal = searchFocusRequest,
                            onPlayFile = onPlayFile,
                            onFolderNavigate = { folder ->
                                viewModel.openSearchFolder(folder.id, folder.name, folder.driveId)
                            },
                            onNavigateToInfo = onNavigateToInfo
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FoldersTab(
    uiState: CatalogUiState,
    viewModel: CatalogViewModel,
    onPlayFile: (String, String, PlayerEngine) -> Unit
) {
    val isGridView = uiState.isGridView
    var tooltipName by remember { mutableStateOf<String?>(null) }

    // Long-press tooltip dialog
    if (tooltipName != null) {
        AlertDialog(
            onDismissRequest = { tooltipName = null },
            title = { Text("Full Name", fontWeight = FontWeight.Bold) },
            text = { Text(tooltipName ?: "", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { tooltipName = null }) { Text("OK") } },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Drive selector
        if (uiState.sharedDrives.isNotEmpty()) {
            DriveSelector(
                drives = uiState.sharedDrives,
                selectedDrive = uiState.selectedDrive,
                onDriveSelected = { viewModel.selectDrive(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Breadcrumb
        if (uiState.selectedDrive != null) {
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

        // Folder-only external playback toggle.
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
            AssistChip(
                onClick = { viewModel.toggleGridView() },
                label = { Text(if (isGridView) "List View" else "Grid View", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(if (isGridView) Icons.Default.ViewList else Icons.Default.GridView, null, Modifier.size(16.dp)) }
            )
        }

        // Files grid - wrapped in PullToRefreshBox (same logic as Refresh chip)
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                // Offline with no cached files → centered "No Connectivity"
                uiState.isOffline && uiState.files.isEmpty() && !uiState.isLoading -> {
                    NoConnectivityMessage(modifier = Modifier.align(Alignment.Center))
                }
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
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.files, key = { it.id }) { file ->
                                MediaCard(
                                    file = file,
                                    tmdbMetadata = null,
                                    onClick = {
                                        if (file.isFolder) {
                                            viewModel.openFolder(file.id, file.name)
                                        } else {
                                            onPlayFile(file.id, file.name, uiState.folderPlaybackEngine())
                                        }
                                    },
                                    onLongClick = { tooltipName = file.name }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.files, key = { it.id }) { file ->
                                SearchResultItem(
                                    file = file,
                                    onClick = {
                                        if (file.isFolder) {
                                            viewModel.openFolder(file.id, file.name)
                                        } else {
                                            onPlayFile(file.id, file.name, uiState.folderPlaybackEngine())
                                        }
                                    },
                                    onLongClick = { tooltipName = file.name }
                                )
                            }
                        }
                    }
                }
            }
        }
        } // end PullToRefreshBox
    }
}

private fun CatalogUiState.folderPlaybackEngine(): PlayerEngine {
    return if (playFolderFilesExternally) PlayerEngine.EXTERNAL else selectedEngine
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
