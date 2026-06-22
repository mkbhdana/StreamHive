package com.mkbhdana.streamhive.navigation

import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.mkbhdana.streamhive.auth.AuthScreen
import com.mkbhdana.streamhive.catalog.*
import com.mkbhdana.streamhive.catalog.info.MediaInfoScreen
import com.mkbhdana.streamhive.player.ExternalPlayerLauncher
import com.mkbhdana.streamhive.player.PlayerScreen
import com.mkbhdana.streamhive.player.mpv.MpvPlayerScreen
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.player.proxy.StreamProxyServer
import com.mkbhdana.streamhive.player.proxy.StreamProxyService
import com.mkbhdana.streamhive.settings.SettingsScreen
import com.mkbhdana.streamhive.update.AppUpdateViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

/**
 * Top-level navigation items for the NavigationSuiteScaffold.
 */
private data class NavItem(
    val route: Any,
    val icon: ImageVector,
    val label: String
)

private val TOP_LEVEL_ITEMS = listOf(
    NavItem(HomeRoute, Icons.Default.Home, "Home"),
    NavItem(FoldersRoute, Icons.Default.VideoLibrary, "Library"),
    NavItem(SettingsRoute, Icons.Default.Settings, "Settings"),
    NavItem(SearchRoute, Icons.Default.Search, "Search")
)

@UnstableApi
@Composable
fun AppNavigation() {
    // Auth state: start as not authenticated, switch after auth success
    var isAuthenticated by rememberSaveable { mutableStateOf(false) }
    val updateViewModel: AppUpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(updateState.updateStatusMessage) {
        updateState.updateStatusMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            updateViewModel.clearUpdateStatusMessage()
        }
    }

    updateState.availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = {
                if (!updateState.isDownloadingUpdate) updateViewModel.dismissUpdatePrompt()
            },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Update Available") },
            text = {
                Text(
                    buildString {
                        append("StreamHive v${update.versionName} is available.")
                        if (update.targetAbi.isNotBlank()) {
                            append("\nAPK: ${update.targetAbi}")
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !updateState.isDownloadingUpdate,
                    onClick = updateViewModel::downloadAndInstallUpdate
                ) {
                    Text(
                        if (updateState.isDownloadingUpdate) {
                            "Downloading ${updateState.updateDownloadProgress}%"
                        } else {
                            "Download & Install"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !updateState.isDownloadingUpdate,
                    onClick = updateViewModel::dismissUpdatePrompt
                ) {
                    Text("Later")
                }
            }
        )
    }

    if (!isAuthenticated) {
        AuthScreen(
            onAuthSuccess = { isAuthenticated = true }
        )
    } else {
        MainScreen(
            onLogout = { isAuthenticated = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@UnstableApi
@Composable
private fun MainScreen(
    onLogout: () -> Unit
) {
    val topLevelBackStack = remember { TopLevelBackStack<Any>(HomeRoute) }
    val context = LocalContext.current

    // CatalogViewModel shared across all tabs — scoped to MainScreen
    val catalogViewModel: CatalogViewModel = hiltViewModel()
    val catalogState by catalogViewModel.uiState.collectAsState()

    var searchFocusRequest by rememberSaveable { mutableIntStateOf(0) }
    var isScopedSearchActive by rememberSaveable { mutableStateOf(false) }
    var scopedSearchFocusRequest by rememberSaveable { mutableIntStateOf(0) }
    val closeScopedSearch = {
        isScopedSearchActive = false
        catalogViewModel.clearScopedSearch()
    }

    // Determine if the current route is a top-level tab (show nav bar) or child (hide nav bar)
    val currentRoute = topLevelBackStack.currentRoute
    val isTopLevelRoute = currentRoute in TOP_LEVEL_ITEMS.map { it.route }

    // Back handler for app exit and tab switching
    var showExitDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val activity = (context as? android.app.Activity)

    BackHandler(enabled = isTopLevelRoute) {
        val currentKey = topLevelBackStack.topLevelKey
        if (isScopedSearchActive) {
            closeScopedSearch()
        } else if (currentKey == FoldersRoute) {
            if (catalogState.folderStack.isNotEmpty()) {
                catalogViewModel.navigateBack()
            } else if (catalogState.selectedDrive != null) {
                catalogViewModel.clearSelectedDrive()
            } else {
                topLevelBackStack.addTopLevel(HomeRoute)
            }
        } else if (currentKey != HomeRoute) {
            topLevelBackStack.addTopLevel(HomeRoute)
        } else {
            showExitDialog = true
        }
    }

    // Exit dialog
    if (showExitDialog) {
        Dialog(onDismissRequest = { showExitDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                    Text("Exit App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Are you sure you want to exit?",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { showExitDialog = false },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { activity?.finish() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Exit", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    // Logout dialog
    if (showLogoutDialog) {
        Dialog(onDismissRequest = { showLogoutDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                    Text("Logout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Are you sure you want to log out of StreamHive? You will need to sign in again.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { showLogoutDialog = false },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                showLogoutDialog = false
                                catalogViewModel.logout()
                                onLogout()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Logout", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    // Playback launcher helper
    val launchPlayback: (String, String, PlayerEngine, String?) -> Unit = { fileId, fileName, engine, decoderMode ->
        when (engine) {
            PlayerEngine.EXO_PLAYER -> {
                topLevelBackStack.add(PlayerRoute(fileId, fileName, decoder = decoderMode ?: ""))
            }
            PlayerEngine.MPV -> {
                topLevelBackStack.add(MpvPlayerRoute(fileId, fileName, decoder = decoderMode ?: ""))
            }
            PlayerEngine.EXTERNAL -> {
                StreamProxyService.start(context)
                val proxyUrl = StreamProxyServer.instanceUrl?.let { base -> "$base/stream/$fileId" }
                if (proxyUrl != null) {
                    ExternalPlayerLauncher.launch(context, proxyUrl, fileName)
                } else {
                    Toast.makeText(context, "Server not ready, please try again", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Home tab scroll state and app bar opacity. With no hero (nothing full-bleed behind
    // the bar), keep the header solid instead of transitioning transparent→solid on scroll.
    // While the home skeleton is loading, keep it transparent.
    val homeListState = rememberLazyListState()
    val noHero = catalogState.homeRecentlyAdded.isEmpty()
    val homeHasContent = catalogState.homeSections.isNotEmpty() || catalogState.homeRecentlyAdded.isNotEmpty()
    val homeSkeletonLoading = catalogState.isHomeRefreshing || catalogState.isHomeLoading ||
        (catalogState.isLoading && !homeHasContent)
    val appBarAlpha by remember(noHero, homeSkeletonLoading) {
        derivedStateOf {
            when {
                topLevelBackStack.topLevelKey != HomeRoute -> 1f
                homeSkeletonLoading -> 0f
                noHero -> 1f
                homeListState.firstVisibleItemIndex > 0 -> 1f
                else -> (homeListState.firstVisibleItemScrollOffset / 400f).coerceIn(0f, 1f)
            }
        }
    }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val appBarColor = surfaceColor.copy(alpha = appBarAlpha)

    // Refresh preferences on tab switch
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(topLevelBackStack.topLevelKey) {
        if (topLevelBackStack.topLevelKey == HomeRoute) {
            catalogViewModel.refreshPreferences()
        }
        if (topLevelBackStack.topLevelKey == SearchRoute) {
            catalogViewModel.prepareGlobalSearch()
        }
    }
    LaunchedEffect(Unit) {
        catalogViewModel.refreshPreferences()
    }
    DisposableEffect(lifecycleOwner, topLevelBackStack.topLevelKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && topLevelBackStack.topLevelKey == HomeRoute) {
                catalogViewModel.refreshPreferences()
                catalogViewModel.loadHomeContent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isImeVisible = WindowInsets.isImeVisible
    val showTopBar = !(isLandscape && isImeVisible && topLevelBackStack.topLevelKey == SearchRoute)

    // Main UI: NavigationSuiteScaffold wraps NavDisplay
    if (isTopLevelRoute) {
        Scaffold(
            topBar = {
                if (showTopBar) {
                    val titleText = when (topLevelBackStack.topLevelKey) {
                        SearchRoute -> "Search"
                        FoldersRoute -> if (isScopedSearchActive) "Search" else "Library"
                        SettingsRoute -> "Settings"
                        else -> "StreamHive"
                    }
                    val isHome = topLevelBackStack.topLevelKey == HomeRoute
                    
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = com.mkbhdana.streamhive.R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    titleText,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            if (isScopedSearchActive) {
                                IconButton(onClick = closeScopedSearch) {
                                    Icon(Icons.Default.Close, "Close search", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            } else if (isHome) {
                                val scope = rememberCoroutineScope()
                                val rotation = remember { Animatable(0f) }
                                IconButton(onClick = {
                                    if (!rotation.isRunning) {
                                        scope.launch {
                                            rotation.snapTo(0f)
                                            rotation.animateTo(
                                                targetValue = 360f,
                                                animationSpec = tween(500, easing = LinearEasing)
                                            )
                                        }
                                    }
                                    catalogViewModel.refreshHomeContent(fromSwipe = false)
                                }) {
                                    Icon(
                                        Icons.Default.Refresh, "Refresh",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.rotate(rotation.value)
                                    )
                                }
                            } else if (topLevelBackStack.topLevelKey == FoldersRoute && catalogState.selectedDrive != null) {
                                IconButton(onClick = {
                                    catalogViewModel.clearScopedSearch()
                                    isScopedSearchActive = true
                                    scopedSearchFocusRequest++
                                }) {
                                    Icon(Icons.Default.Search, "Search current folder", tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = appBarColor
                        )
                    )
                }
            }
        ) { topBarPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Tab content — rendered directly (not via NavDisplay) since these share ViewModel
                val scope = rememberCoroutineScope()
                when (topLevelBackStack.topLevelKey) {
                    HomeRoute -> {
                        PullToRefreshBox(
                            isRefreshing = catalogState.isHomeRefreshing,
                            onRefresh = { catalogViewModel.refreshHomeContent(fromSwipe = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            HomeTab(
                                state = catalogState,
                                preferredEngine = catalogViewModel.getPreferredEngine(),
                                onPlayFile = { fileId, fileName, engine ->
                                    launchPlayback(fileId, fileName, engine, null)
                                },
                                onPlayFileWithDecoder = launchPlayback,
                                onNavigateToSettings = { topLevelBackStack.add(SettingsRoute) },
                                onClearHistory = catalogViewModel::clearPlaybackHistory,
                                onNavigateToInfo = { driveFileId, mediaType ->
                                    topLevelBackStack.add(MediaInfoRoute(driveFileId, mediaType))
                                },
                                onRemoveFromContinue = catalogViewModel::removeFromHistory,
                                onPlayFromStart = { fileId, fileName, engine, decoderMode ->
                                    scope.launch {
                                        catalogViewModel.removeFromHistorySync(fileId)
                                        launchPlayback(fileId, fileName, engine, decoderMode)
                                    }
                                },
                                onNavigateToSeeAll = { folderId ->
                                    topLevelBackStack.add(TmdbSeeAllRoute(folderId))
                                },
                                homeListState = homeListState
                            )
                        }
                    }
                    FoldersRoute -> {
                        if (isScopedSearchActive) {
                            ScopedFolderSearchView(
                                uiState = catalogState,
                                viewModel = catalogViewModel,
                                focusRequestSignal = scopedSearchFocusRequest,
                                onPlayFile = { fileId, fileName, engine ->
                                    launchPlayback(fileId, fileName, engine, null)
                                },
                                onFolderOpen = { folder ->
                                    closeScopedSearch()
                                    catalogViewModel.openFolder(folder.id, folder.name)
                                },
                                modifier = Modifier.padding(top = topBarPadding.calculateTopPadding())
                            )
                        } else {
                            FoldersTab(
                                uiState = catalogState,
                                viewModel = catalogViewModel,
                                onPlayFile = { fileId, fileName, engine ->
                                    launchPlayback(fileId, fileName, engine, null)
                                },
                                modifier = Modifier.padding(top = topBarPadding.calculateTopPadding())
                            )
                        }
                    }
                    SearchRoute -> {
                        SearchTab(
                            state = catalogState,
                            viewModel = catalogViewModel,
                            focusRequestSignal = searchFocusRequest,
                            onPlayFile = { fileId, fileName, engine ->
                                launchPlayback(fileId, fileName, engine, null)
                            },
                            onFolderNavigate = { folder ->
                                catalogViewModel.openFolderFromSearch(folder)
                                closeScopedSearch()
                                topLevelBackStack.addTopLevel(FoldersRoute)
                            },
                            onNavigateToInfo = { driveFileId, mediaType ->
                                topLevelBackStack.add(MediaInfoRoute(driveFileId, mediaType))
                            },
                            modifier = Modifier
                                .padding(top = topBarPadding.calculateTopPadding())
                                .imePadding()
                        )
                    }
                    SettingsRoute -> {
                        SettingsScreen(
                            onBack = { topLevelBackStack.addTopLevel(HomeRoute) },
                            onNavigate = { route -> 
                                when (route) {
                                    "settings/player" -> topLevelBackStack.add(SettingsPlayerRoute)
                                    "settings/gestures" -> topLevelBackStack.add(SettingsGesturesRoute)
                                    "settings/subtitles" -> topLevelBackStack.add(SettingsSubtitlesRoute)
                                    "settings/tmdb" -> topLevelBackStack.add(SettingsTmdbRoute)
                                    "settings/storage" -> topLevelBackStack.add(SettingsStorageRoute)
                                }
                            },
                            onLogout = { showLogoutDialog = true },
                            viewModel = hiltViewModel()
                        )
                    }
                }

                // Floating Bottom Nav Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .padding(horizontal = 24.dp)
                        .width(300.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TOP_LEVEL_ITEMS.forEach { navItem ->
                        val selected = navItem.route == topLevelBackStack.topLevelKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        closeScopedSearch()
                                        if (navItem.route == SearchRoute) {
                                            catalogViewModel.prepareGlobalSearch()
                                            searchFocusRequest++
                                        }
                                        topLevelBackStack.addTopLevel(navItem.route)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = navItem.icon,
                                contentDescription = navItem.label,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = navItem.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Non-tab routes: full-screen, no nav bar
        NavDisplay(
            backStack = topLevelBackStack.backStack,
            onBack = { topLevelBackStack.removeLast() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                // These entries are only rendered when a child route is on top.
                // The top-level tab entries (Home/Folders/Search) are rendered
                // directly in the NavigationSuiteScaffold above.

                entry<HomeRoute> { /* Handled above in NavigationSuiteScaffold */ }
                entry<FoldersRoute> { /* Handled above */ }
                entry<SearchRoute> { /* Handled above */ }

                entry<SettingsRoute> { /* Handled above */ }

                entry<SettingsPlayerRoute> {
                    val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
                    com.mkbhdana.streamhive.settings.PlayerSettingsScreen(
                        onBack = { topLevelBackStack.removeLast() },
                        viewModel = settingsVm
                    )
                }

                entry<SettingsGesturesRoute> {
                    val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
                    com.mkbhdana.streamhive.settings.GesturesSettingsScreen(
                        onBack = { topLevelBackStack.removeLast() },
                        viewModel = settingsVm
                    )
                }

                entry<SettingsSubtitlesRoute> {
                    val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
                    com.mkbhdana.streamhive.settings.SubtitleSettingsScreen(
                        onBack = { topLevelBackStack.removeLast() },
                        viewModel = settingsVm
                    )
                }

                entry<SettingsTmdbRoute> {
                    val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
                    com.mkbhdana.streamhive.settings.TmdbSettingsScreen(
                        onBack = { topLevelBackStack.removeLast() },
                        viewModel = settingsVm
                    )
                }

                entry<SettingsStorageRoute> {
                    val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
                    com.mkbhdana.streamhive.settings.StorageSettingsScreen(
                        onBack = { topLevelBackStack.removeLast() },
                        viewModel = settingsVm
                    )
                }

                entry<PlayerRoute> { key ->
                    PlayerScreen(
                        onBack = { topLevelBackStack.removeLast() },
                        allowEngineFallback = key.allowFallback,
                        switchingMessage = if (key.handoff) "Switching to Exo" else null,
                        navKey = key,
                        onFallbackToMpv = { decoderMode ->
                            topLevelBackStack.replaceLast(
                                MpvPlayerRoute(key.fileId, key.fileName, allowFallback = false, handoff = true, decoder = decoderMode ?: "")
                            )
                        },
                        onSwitchToMpv = { decoderMode ->
                            topLevelBackStack.replaceLast(
                                MpvPlayerRoute(key.fileId, key.fileName, allowFallback = true, handoff = true, decoder = decoderMode ?: "")
                            )
                        }
                    )
                }

                entry<MpvPlayerRoute> { key ->
                    MpvPlayerScreen(
                        onBack = { topLevelBackStack.removeLast() },
                        allowEngineFallback = key.allowFallback,
                        switchingMessage = if (key.handoff) "Switching to MPV" else null,
                        navKey = key,
                        onFallbackToExo = { decoderMode ->
                            topLevelBackStack.replaceLast(
                                PlayerRoute(key.fileId, key.fileName, allowFallback = false, handoff = true, decoder = decoderMode ?: "")
                            )
                        },
                        onSwitchToExo = { decoderMode ->
                            topLevelBackStack.replaceLast(
                                PlayerRoute(key.fileId, key.fileName, allowFallback = true, handoff = true, decoder = decoderMode ?: "")
                            )
                        }
                    )
                }

                entry<MediaInfoRoute> { key ->
                    MediaInfoScreen(
                        onBack = { topLevelBackStack.removeLast() },
                        navKey = key,
                        onPlayFile = { fileId, fileName, engine ->
                            when (engine) {
                                PlayerEngine.EXO_PLAYER -> topLevelBackStack.add(PlayerRoute(fileId, fileName))
                                PlayerEngine.MPV -> topLevelBackStack.add(MpvPlayerRoute(fileId, fileName))
                                PlayerEngine.EXTERNAL -> {
                                    StreamProxyService.start(context)
                                    val proxyUrl = StreamProxyServer.instanceUrl?.let { base -> "$base/stream/$fileId" }
                                    if (proxyUrl != null) {
                                        ExternalPlayerLauncher.launch(context, proxyUrl, fileName)
                                    }
                                }
                            }
                        }
                    )
                }

                entry<TmdbSeeAllRoute> { key ->
                    val catalogState2 by catalogViewModel.uiState.collectAsState()
                    val section = catalogState2.homeSections.firstOrNull { it.folderId == key.folderId }
                    val files = section?.items.orEmpty().distinctBy { it.id }
                    val defaultMediaType = section?.mediaType ?: "auto"
                    val fileMediaTypes = files.associate { file -> file.id to defaultMediaType }

                    TmdbSeeAllScreen(
                        title = section?.folderName ?: "Catalog",
                        defaultMediaType = defaultMediaType,
                        files = files,
                        fileMediaTypes = fileMediaTypes,
                        tmdbMetadata = catalogState2.tmdbMetadata,
                        onBack = { topLevelBackStack.removeLast() },
                        onNavigateToInfo = { driveFileId, mediaType ->
                            topLevelBackStack.add(MediaInfoRoute(driveFileId, mediaType))
                        }
                    )
                }
            }
        )
    }
}
