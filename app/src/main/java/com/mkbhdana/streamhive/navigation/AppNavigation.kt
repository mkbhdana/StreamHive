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
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    NavItem(FoldersRoute, Icons.Default.Folder, "Folders"),
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

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class, ExperimentalMaterial3Api::class)
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

    // Determine if the current route is a top-level tab (show nav bar) or child (hide nav bar)
    val currentRoute = topLevelBackStack.currentRoute
    val isTopLevelRoute = currentRoute in TOP_LEVEL_ITEMS.map { it.route }

    // Back handler for app exit and tab switching
    var showExitDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val activity = (context as? android.app.Activity)

    BackHandler(enabled = isTopLevelRoute) {
        val currentKey = topLevelBackStack.topLevelKey
        if (currentKey == FoldersRoute && catalogState.folderStack.isNotEmpty()) {
            catalogViewModel.navigateBack()
        } else if (currentKey != HomeRoute) {
            topLevelBackStack.addTopLevel(HomeRoute)
        } else {
            showExitDialog = true
        }
    }

    // Exit dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = MaterialTheme.colorScheme.primary) },
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

    // Logout dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out of StreamHive? You will need to sign in again.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    catalogViewModel.logout()
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

    // Home tab scroll state and app bar opacity
    val homeListState = rememberLazyListState()
    val appBarAlpha by remember {
        derivedStateOf {
            if (topLevelBackStack.topLevelKey != HomeRoute) 1f
            else when {
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

    // Main UI: NavigationSuiteScaffold wraps NavDisplay
    if (isTopLevelRoute) {
        Scaffold(
            topBar = {
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
                                "StreamHive",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        if (topLevelBackStack.topLevelKey == HomeRoute) {
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
                        }
                        IconButton(onClick = { topLevelBackStack.add(SettingsRoute) }) {
                            Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "Logout", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = appBarColor
                    )
                )
            }
        ) { topBarPadding ->
            NavigationSuiteScaffold(
                navigationSuiteItems = {
                    TOP_LEVEL_ITEMS.forEach { navItem ->
                        item(
                            selected = navItem.route == topLevelBackStack.topLevelKey,
                            onClick = {
                                if (navItem.route == SearchRoute) searchFocusRequest++
                                topLevelBackStack.addTopLevel(navItem.route)
                            },
                            icon = { Icon(navItem.icon, navItem.label) },
                            label = { Text(navItem.label) }
                        )
                    }
                }
            ) {
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
                        FoldersTab(
                            uiState = catalogState,
                            viewModel = catalogViewModel,
                            onPlayFile = { fileId, fileName, engine ->
                                launchPlayback(fileId, fileName, engine, null)
                            },
                            modifier = Modifier.padding(top = topBarPadding.calculateTopPadding())
                        )
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
                                catalogViewModel.openSearchFolder(folder.id, folder.name, folder.driveId)
                            },
                            onNavigateToInfo = { driveFileId, mediaType ->
                                topLevelBackStack.add(MediaInfoRoute(driveFileId, mediaType))
                            },
                            modifier = Modifier.padding(top = topBarPadding.calculateTopPadding())
                        )
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
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { -it / 3 },
                    animationSpec = tween(300)
                )
            },
            popTransitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = tween(300)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300)
                )
            },
            predictivePopTransitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = tween(300)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300)
                )
            },
            entryProvider = entryProvider {
                // These entries are only rendered when a child route is on top.
                // The top-level tab entries (Home/Folders/Search) are rendered
                // directly in the NavigationSuiteScaffold above.

                entry<HomeRoute> { /* Handled above in NavigationSuiteScaffold */ }
                entry<FoldersRoute> { /* Handled above */ }
                entry<SearchRoute> { /* Handled above */ }

                entry<SettingsRoute> {
                    val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
                    SettingsScreen(
                        onBack = { topLevelBackStack.removeLast() },
                        onNavigate = { route ->
                            when (route) {
                                "settings/player" -> topLevelBackStack.add(SettingsPlayerRoute)
                                "settings/gestures" -> topLevelBackStack.add(SettingsGesturesRoute)
                                "settings/subtitles" -> topLevelBackStack.add(SettingsSubtitlesRoute)
                                "settings/tmdb" -> topLevelBackStack.add(SettingsTmdbRoute)
                                "settings/storage" -> topLevelBackStack.add(SettingsStorageRoute)
                            }
                        },
                        viewModel = settingsVm
                    )
                }

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
