package com.mkbhdana.streamhive.tv

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.hilt.navigation.compose.hiltViewModel
import com.mkbhdana.streamhive.catalog.CatalogViewModel
import com.mkbhdana.streamhive.navigation.FoldersRoute
import com.mkbhdana.streamhive.navigation.HomeRoute
import com.mkbhdana.streamhive.navigation.MediaInfoRoute
import com.mkbhdana.streamhive.navigation.MpvPlayerRoute
import com.mkbhdana.streamhive.navigation.PlayerRoute
import com.mkbhdana.streamhive.navigation.SearchRoute
import com.mkbhdana.streamhive.navigation.SettingsRoute
import com.mkbhdana.streamhive.navigation.TopLevelBackStack
import com.mkbhdana.streamhive.player.ExternalPlayerLauncher
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.player.proxy.StreamProxyServer
import com.mkbhdana.streamhive.player.proxy.StreamProxyService
import com.mkbhdana.streamhive.tv.components.TvConfirmDialog
import com.mkbhdana.streamhive.tv.components.TvDestination
import com.mkbhdana.streamhive.tv.components.TvNavDrawer
import com.mkbhdana.streamhive.tv.components.TvUpdateDialog
import com.mkbhdana.streamhive.update.AppUpdateViewModel
import com.mkbhdana.streamhive.tv.detail.TvMediaInfoScreen
import com.mkbhdana.streamhive.tv.home.TvHomeScreen
import com.mkbhdana.streamhive.tv.library.TvLibraryScreen
import com.mkbhdana.streamhive.tv.player.TvExoPlayerScreen
import com.mkbhdana.streamhive.tv.player.TvMpvPlayerScreen
import com.mkbhdana.streamhive.tv.search.TvSearchScreen
import com.mkbhdana.streamhive.tv.settings.TvSettingsScreen
import com.mkbhdana.streamhive.tv.theme.TvBackgroundColor as TvBackground

/**
 * The authenticated TV experience: a NuvioTV-style drawer hosting Home / Search /
 * Library / Settings, plus full-screen Player and Detail routes. Navigation state
 * reuses the shared [TopLevelBackStack] and route types; content reuses the shared
 * [CatalogViewModel].
 */
@UnstableApi
@Composable
fun TvMainScreen(onLoggedOut: () -> Unit) {
    val context = LocalContext.current
    val backStack = remember { TopLevelBackStack<Any>(HomeRoute) }
    val catalogViewModel: CatalogViewModel = hiltViewModel()
    val updateViewModel: AppUpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.uiState.collectAsState()
    var showExitDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(updateState.updateStatusMessage) {
        updateState.updateStatusMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            updateViewModel.clearUpdateStatusMessage()
        }
    }

    val launchPlayback: (String, String, PlayerEngine, String?) -> Unit = { fileId, fileName, engine, decoder ->
        when (engine) {
            PlayerEngine.EXO_PLAYER -> backStack.add(PlayerRoute(fileId, fileName, decoder = decoder ?: ""))
            PlayerEngine.MPV -> backStack.add(MpvPlayerRoute(fileId, fileName, decoder = decoder ?: ""))
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
    val openInfo: (String, String) -> Unit = { id, type -> backStack.add(MediaInfoRoute(id, type)) }

    LaunchedEffect(Unit) { catalogViewModel.refreshPreferences() }
    LaunchedEffect(backStack.topLevelKey) {
        when (backStack.topLevelKey) {
            HomeRoute -> catalogViewModel.refreshPreferences()
            SearchRoute -> catalogViewModel.prepareGlobalSearch()
        }
    }

    val current = backStack.currentRoute
    val isChild = current is PlayerRoute || current is MpvPlayerRoute || current is MediaInfoRoute

    // Back handling for the drawer level (child screens own their own BackHandler).
    BackHandler(enabled = !isChild) {
        val state = catalogViewModel.uiState.value
        when {
            backStack.topLevelKey == FoldersRoute && state.folderStack.isNotEmpty() ->
                catalogViewModel.navigateBack()
            backStack.topLevelKey == FoldersRoute && state.selectedDrive != null ->
                catalogViewModel.clearSelectedDrive()
            backStack.topLevelKey != HomeRoute -> backStack.addTopLevel(HomeRoute)
            else -> showExitDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(TvBackground)) {
        if (!isChild) {
            TvNavDrawer(
                selected = destinationFor(backStack.topLevelKey),
                onSelect = { backStack.addTopLevel(routeFor(it)) }
            ) { contentModifier ->
                Box(contentModifier.fillMaxSize()) {
                    when (backStack.topLevelKey) {
                        SearchRoute -> TvSearchScreen(
                            viewModel = catalogViewModel,
                            onPlay = launchPlayback,
                            onOpenInfo = openInfo
                        )
                        FoldersRoute -> TvLibraryScreen(
                            viewModel = catalogViewModel,
                            onPlay = launchPlayback
                        )
                        SettingsRoute -> TvSettingsScreen(
                            onLogout = { showLogoutDialog = true }
                        )
                        else -> TvHomeScreen(
                            viewModel = catalogViewModel,
                            onPlay = launchPlayback,
                            onOpenInfo = openInfo
                        )
                    }
                }
            }
        } else {
            when (val key = current) {
                is PlayerRoute -> TvExoPlayerScreen(
                    navKey = key,
                    onBack = { backStack.removeLast() },
                    onSwitchEngine = { resize, speed ->
                        backStack.replaceLast(MpvPlayerRoute(key.fileId, key.fileName, handoff = true, decoder = key.decoder, resizeMode = resize, playbackSpeed = speed))
                    }
                )
                is MpvPlayerRoute -> TvMpvPlayerScreen(
                    navKey = key,
                    onBack = { backStack.removeLast() },
                    onSwitchEngine = { resize, speed ->
                        backStack.replaceLast(PlayerRoute(key.fileId, key.fileName, handoff = true, decoder = key.decoder, resizeMode = resize, playbackSpeed = speed))
                    }
                )
                is MediaInfoRoute -> TvMediaInfoScreen(
                    navKey = key,
                    onPlay = launchPlayback,
                    onBack = { backStack.removeLast() }
                )
            }
        }

        if (showExitDialog) {
            TvConfirmDialog(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "Exit App",
                message = "Are you sure you want to exit StreamHive?",
                confirmLabel = "Exit",
                onConfirm = {
                    showExitDialog = false
                    (context as? Activity)?.finish()
                },
                onDismiss = { showExitDialog = false }
            )
        }
        if (showLogoutDialog) {
            TvConfirmDialog(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "Logout",
                message = "Are you sure you want to log out of StreamHive? You will need to sign in again.",
                confirmLabel = "Logout",
                onConfirm = {
                    showLogoutDialog = false
                    catalogViewModel.logout()
                    onLoggedOut()
                },
                onDismiss = { showLogoutDialog = false }
            )
        }

        // Never surface the update prompt over the player.
        if (current !is PlayerRoute && current !is MpvPlayerRoute) {
            updateState.availableUpdate?.let { update ->
                TvUpdateDialog(
                    versionName = update.versionName,
                    targetAbi = update.targetAbi,
                    isDownloading = updateState.isDownloadingUpdate,
                    downloadProgress = updateState.updateDownloadProgress,
                    onDownload = updateViewModel::downloadAndInstallUpdate,
                    onDismiss = updateViewModel::dismissUpdatePrompt
                )
            }
        }
    }
}

private fun destinationFor(topLevelKey: Any): TvDestination = when (topLevelKey) {
    SearchRoute -> TvDestination.Search
    FoldersRoute -> TvDestination.Library
    SettingsRoute -> TvDestination.Settings
    else -> TvDestination.Home
}

private fun routeFor(destination: TvDestination): Any = when (destination) {
    TvDestination.Home -> HomeRoute
    TvDestination.Search -> SearchRoute
    TvDestination.Library -> FoldersRoute
    TvDestination.Settings -> SettingsRoute
}
