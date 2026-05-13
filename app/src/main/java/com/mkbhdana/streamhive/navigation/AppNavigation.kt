package com.mkbhdana.streamhive.navigation

import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mkbhdana.streamhive.auth.AuthScreen
import com.mkbhdana.streamhive.catalog.CatalogScreen
import com.mkbhdana.streamhive.catalog.TmdbSeeAllScreen
import com.mkbhdana.streamhive.catalog.info.MediaInfoScreen
import com.mkbhdana.streamhive.player.ExternalPlayerLauncher
import com.mkbhdana.streamhive.player.PlayerScreen
import com.mkbhdana.streamhive.player.mpv.MpvPlayerScreen
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.player.proxy.StreamProxyServer
import com.mkbhdana.streamhive.player.proxy.StreamProxyService
import com.mkbhdana.streamhive.settings.SettingsScreen
import com.mkbhdana.streamhive.update.AppUpdateViewModel

object Routes {
    const val AUTH = "auth"
    const val CATALOG = "catalog"
    const val PLAYER = "player/{fileId}/{fileName}?allowFallback={allowFallback}&handoff={handoff}&decoder={decoder}"
    const val MPV_PLAYER = "mpv_player/{fileId}/{fileName}?allowFallback={allowFallback}&handoff={handoff}&decoder={decoder}"
    const val SETTINGS = "settings"
    const val SETTINGS_PLAYER = "settings/player"
    const val SETTINGS_GESTURES = "settings/gestures"
    const val SETTINGS_SUBTITLES = "settings/subtitles"
    const val SETTINGS_TMDB = "settings/tmdb"
    const val SETTINGS_STORAGE = "settings/storage"
    const val MEDIA_INFO = "media_info/{driveFileId}?mediaType={mediaType}"
    const val TMDB_SEE_ALL = "tmdb_see_all/{folderId}"
}

@UnstableApi
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
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

    NavHost(
        navController = navController,
        startDestination = Routes.AUTH,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(animationSpec = tween(300)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn(animationSpec = tween(300)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(animationSpec = tween(300)) }
    ) {
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Routes.CATALOG) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CATALOG) {
            val ctx = LocalContext.current
            val launchPlayback: (String, String, PlayerEngine, String?) -> Unit = { fileId, fileName, engine, decoderMode ->
                when (engine) {
                    PlayerEngine.EXO_PLAYER -> {
                        navController.navigateToPlayback(playerRoute(fileId, fileName, decoderMode = decoderMode))
                    }
                    PlayerEngine.MPV -> {
                        navController.navigateToPlayback(mpvPlayerRoute(fileId, fileName, decoderMode = decoderMode))
                    }
                    PlayerEngine.EXTERNAL -> {
                        StreamProxyService.start(ctx)
                        val proxyUrl = StreamProxyServer.instanceUrl?.let { base -> "$base/stream/$fileId" }
                        if (proxyUrl != null) {
                            ExternalPlayerLauncher.launch(ctx, proxyUrl, fileName)
                        } else {
                            Toast.makeText(ctx, "Server not ready, please try again", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            CatalogScreen(
                onPlayFile = { fileId, fileName, engine ->
                    launchPlayback(fileId, fileName, engine, null)
                },
                onPlayFileWithDecoder = launchPlayback,
                onLogout = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToInfo = { driveFileId, mediaType ->
                    navController.navigate("media_info/$driveFileId?mediaType=$mediaType")
                },
                onNavigateToSeeAll = { folderId ->
                    navController.navigate("tmdb_see_all/${encodeRouteValue(folderId)}")
                }
            )
        }

        composable(Routes.SETTINGS) {
            val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) },
                viewModel = settingsVm
            )
        }

        composable(Routes.SETTINGS_PLAYER) {
            val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
            com.mkbhdana.streamhive.settings.PlayerSettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = settingsVm
            )
        }

        composable(Routes.SETTINGS_GESTURES) {
            val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
            com.mkbhdana.streamhive.settings.GesturesSettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = settingsVm
            )
        }

        composable(Routes.SETTINGS_SUBTITLES) {
            val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
            com.mkbhdana.streamhive.settings.SubtitleSettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = settingsVm
            )
        }

        composable(Routes.SETTINGS_TMDB) {
            val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
            com.mkbhdana.streamhive.settings.TmdbSettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = settingsVm
            )
        }

        composable(Routes.SETTINGS_STORAGE) {
            val settingsVm: com.mkbhdana.streamhive.settings.SettingsViewModel = hiltViewModel()
            com.mkbhdana.streamhive.settings.StorageSettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = settingsVm
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = playbackArguments(),
            enterTransition = { fadeIn(animationSpec = tween(140)) },
            exitTransition = { fadeOut(animationSpec = tween(140)) },
            popEnterTransition = { fadeIn(animationSpec = tween(140)) },
            popExitTransition = { fadeOut(animationSpec = tween(140)) }
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getString("fileId").orEmpty()
            val fileName = decodeRouteValue(backStackEntry.arguments?.getString("fileName").orEmpty())
            val allowFallback = backStackEntry.arguments?.getBoolean("allowFallback") ?: true
            val handoff = backStackEntry.arguments?.getBoolean("handoff") ?: false

            PlayerScreen(
                onBack = { navController.popBackStack() },
                allowEngineFallback = allowFallback,
                switchingMessage = if (handoff) "Switching to Exo" else null,
                onFallbackToMpv = { decoderMode ->
                    navController.navigateToPlayback(
                        mpvPlayerRoute(fileId, fileName, allowFallback = false, handoff = true, decoderMode = decoderMode),
                        replaceCurrent = true
                    )
                },
                onSwitchToMpv = { decoderMode ->
                    navController.navigateToPlayback(
                        mpvPlayerRoute(fileId, fileName, allowFallback = true, handoff = true, decoderMode = decoderMode),
                        replaceCurrent = true
                    )
                }
            )
        }

        composable(
            route = Routes.MPV_PLAYER,
            arguments = playbackArguments(),
            enterTransition = { fadeIn(animationSpec = tween(140)) },
            exitTransition = { fadeOut(animationSpec = tween(140)) },
            popEnterTransition = { fadeIn(animationSpec = tween(140)) },
            popExitTransition = { fadeOut(animationSpec = tween(140)) }
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getString("fileId").orEmpty()
            val fileName = decodeRouteValue(backStackEntry.arguments?.getString("fileName").orEmpty())
            val allowFallback = backStackEntry.arguments?.getBoolean("allowFallback") ?: true
            val handoff = backStackEntry.arguments?.getBoolean("handoff") ?: false

            MpvPlayerScreen(
                onBack = { navController.popBackStack() },
                allowEngineFallback = allowFallback,
                switchingMessage = if (handoff) "Switching to MPV" else null,
                onFallbackToExo = { decoderMode ->
                    navController.navigateToPlayback(
                        playerRoute(fileId, fileName, allowFallback = false, handoff = true, decoderMode = decoderMode),
                        replaceCurrent = true
                    )
                },
                onSwitchToExo = { decoderMode ->
                    navController.navigateToPlayback(
                        playerRoute(fileId, fileName, allowFallback = true, handoff = true, decoderMode = decoderMode),
                        replaceCurrent = true
                    )
                }
            )
        }

        composable(
            route = Routes.MEDIA_INFO,
            arguments = listOf(
                navArgument("driveFileId") { type = NavType.StringType },
                navArgument("mediaType") {
                    type = NavType.StringType
                    defaultValue = "auto"
                    nullable = true
                }
            )
        ) {
            val ctx = LocalContext.current
            MediaInfoScreen(
                onBack = { navController.popBackStack() },
                onPlayFile = { fileId, fileName, engine ->
                    when (engine) {
                        PlayerEngine.EXO_PLAYER -> navController.navigateToPlayback(playerRoute(fileId, fileName))
                        PlayerEngine.MPV -> navController.navigateToPlayback(mpvPlayerRoute(fileId, fileName))
                        PlayerEngine.EXTERNAL -> {
                            StreamProxyService.start(ctx)
                            val proxyUrl = StreamProxyServer.instanceUrl?.let { base -> "$base/stream/$fileId" }
                            if (proxyUrl != null) {
                                ExternalPlayerLauncher.launch(ctx, proxyUrl, fileName)
                            }
                        }
                    }
                }
            )
        }

        composable(
            route = Routes.TMDB_SEE_ALL,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderId = decodeRouteValue(backStackEntry.arguments?.getString("folderId") ?: "")
            val catalogEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.CATALOG)
            }
            val catalogVm: com.mkbhdana.streamhive.catalog.CatalogViewModel = hiltViewModel(catalogEntry)
            val catalogState by catalogVm.uiState.collectAsState()

            val section = catalogState.homeSections.firstOrNull { it.folderId == folderId }
            val files = section?.items.orEmpty().distinctBy { it.id }
            val defaultMediaType = section?.mediaType ?: "auto"
            val fileMediaTypes = files.associate { file -> file.id to defaultMediaType }

            TmdbSeeAllScreen(
                title = section?.folderName ?: "Catalog",
                defaultMediaType = defaultMediaType,
                files = files,
                fileMediaTypes = fileMediaTypes,
                tmdbMetadata = catalogState.tmdbMetadata,
                onBack = { navController.popBackStack() },
                onNavigateToInfo = { driveFileId, mediaType ->
                    navController.navigate("media_info/$driveFileId?mediaType=$mediaType")
                }
            )
        }
    }
}

private fun playbackArguments() = listOf(
    navArgument("fileId") { type = NavType.StringType },
    navArgument("fileName") { type = NavType.StringType },
    navArgument("allowFallback") {
        type = NavType.BoolType
        defaultValue = true
    },
    navArgument("handoff") {
        type = NavType.BoolType
        defaultValue = false
    },
    navArgument("decoder") {
        type = NavType.StringType
        defaultValue = ""
    }
)

private fun playerRoute(
    fileId: String,
    fileName: String,
    allowFallback: Boolean = true,
    handoff: Boolean = false,
    decoderMode: String? = null
): String {
    val decoderQuery = decoderMode?.takeIf { it.isNotBlank() }?.let { "&decoder=${encodeRouteValue(it)}" }.orEmpty()
    return "player/$fileId/${encodeRouteValue(fileName)}?allowFallback=$allowFallback&handoff=$handoff$decoderQuery"
}

private fun mpvPlayerRoute(
    fileId: String,
    fileName: String,
    allowFallback: Boolean = true,
    handoff: Boolean = false,
    decoderMode: String? = null
): String {
    val decoderQuery = decoderMode?.takeIf { it.isNotBlank() }?.let { "&decoder=${encodeRouteValue(it)}" }.orEmpty()
    return "mpv_player/$fileId/${encodeRouteValue(fileName)}?allowFallback=$allowFallback&handoff=$handoff$decoderQuery"
}

private fun encodeRouteValue(value: String): String {
    return java.net.URLEncoder.encode(value, "UTF-8")
}

private fun decodeRouteValue(value: String): String {
    return runCatching {
        java.net.URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)
}

private fun NavHostController.navigateToPlayback(route: String, replaceCurrent: Boolean = false) {
    val currentDestinationId = currentDestination?.id
    navigate(route) {
        if (replaceCurrent && currentDestinationId != null) {
            popUpTo(currentDestinationId) { inclusive = true }
        }
        launchSingleTop = true
    }
}
