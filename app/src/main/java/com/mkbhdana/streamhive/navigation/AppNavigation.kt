package com.mkbhdana.streamhive.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mkbhdana.streamhive.auth.AuthScreen
import com.mkbhdana.streamhive.catalog.CatalogScreen
import com.mkbhdana.streamhive.catalog.TmdbSeeAllScreen
import com.mkbhdana.streamhive.catalog.TvCatalogScreen
import com.mkbhdana.streamhive.catalog.info.MediaInfoScreen
import com.mkbhdana.streamhive.player.ExternalPlayerLauncher
import com.mkbhdana.streamhive.player.PlayerScreen
import com.mkbhdana.streamhive.player.proxy.StreamProxyServer
import com.mkbhdana.streamhive.player.proxy.StreamProxyService
import com.mkbhdana.streamhive.player.mpv.MpvPlayerScreen
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.settings.SettingsScreen
import com.mkbhdana.streamhive.update.AppUpdateViewModel
import android.widget.Toast
import androidx.compose.ui.unit.dp

object Routes {
    const val AUTH = "auth"
    const val CATALOG = "catalog"
    const val PLAYER = "player/{fileId}/{fileName}?allowFallback={allowFallback}&handoff={handoff}"
    const val MPV_PLAYER = "mpv_player/{fileId}/{fileName}?allowFallback={allowFallback}&handoff={handoff}"
    const val SETTINGS = "settings"
    const val MEDIA_INFO = "media_info/{driveFileId}?mediaType={mediaType}"
    const val TMDB_SEE_ALL = "tmdb_see_all/{category}"

    // TV routes
    const val TV_CATALOG = "tv_catalog"
}

@UnstableApi
@Composable
fun AppNavigation(isTv: Boolean = false) {
    val navController = rememberNavController()
    val updateViewModel: AppUpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    updateState.availableUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { updateViewModel.dismissUpdatePrompt() },
            shape = RoundedCornerShape(16.dp),
            title = { Text("Update Available") },
            text = { Text("StreamHive v${update.versionName} is available. Download the latest APK to update the app.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        uriHandler.openUri(update.downloadUrl)
                        updateViewModel.dismissUpdatePrompt(suppressThisVersion = true)
                    }
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateViewModel.dismissUpdatePrompt() }) {
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
                    navController.navigate(
                        if (isTv) Routes.TV_CATALOG else Routes.CATALOG
                    ) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CATALOG) {
            val ctx = LocalContext.current
            CatalogScreen(
                onPlayFile = { fileId, fileName, engine ->
                    when (engine) {
                        PlayerEngine.EXO_PLAYER -> {
                            navController.navigateToPlayback(playerRoute(fileId, fileName))
                        }
                        PlayerEngine.MPV -> {
                            navController.navigateToPlayback(mpvPlayerRoute(fileId, fileName))
                        }
                        PlayerEngine.EXTERNAL -> {
                            // Start foreground service to keep proxy alive while external player runs
                            StreamProxyService.start(ctx)
                            val proxyUrl = StreamProxyServer.instanceUrl?.let { base -> "$base/stream/$fileId" }
                            if (proxyUrl != null) {
                                ExternalPlayerLauncher.launch(ctx, proxyUrl, fileName)
                            } else {
                                Toast.makeText(ctx, "Server not ready, please try again", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
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
                onNavigateToSeeAll = { category ->
                    navController.navigate("tmdb_see_all/$category")
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("fileId") { type = NavType.StringType },
                navArgument("fileName") { type = NavType.StringType },
                navArgument("allowFallback") {
                    type = NavType.BoolType
                    defaultValue = true
                },
                navArgument("handoff") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            enterTransition = { fadeIn(animationSpec = tween(140)) },
            exitTransition = { fadeOut(animationSpec = tween(140)) },
            popEnterTransition = { fadeIn(animationSpec = tween(140)) },
            popExitTransition = { fadeOut(animationSpec = tween(140)) }
        ) { backStackEntry ->
            val ctx = LocalContext.current
            val fileId = backStackEntry.arguments?.getString("fileId").orEmpty()
            val fileName = decodeRouteValue(backStackEntry.arguments?.getString("fileName").orEmpty())
            val allowFallback = backStackEntry.arguments?.getBoolean("allowFallback") ?: true
            val handoff = backStackEntry.arguments?.getBoolean("handoff") ?: false

            PlayerScreen(
                onBack = { navController.popBackStack() },
                allowEngineFallback = allowFallback,
                switchingMessage = if (handoff) "Switching to Exo" else null,
                onFallbackToMpv = {
                    navController.navigateToPlayback(
                        mpvPlayerRoute(fileId, fileName, allowFallback = false, handoff = true),
                        replaceCurrent = true
                    )
                },
                onSwitchToMpv = {
                    navController.navigateToPlayback(
                        mpvPlayerRoute(fileId, fileName, allowFallback = true, handoff = true),
                        replaceCurrent = true
                    )
                }
            )
        }

        composable(
            route = Routes.MPV_PLAYER,
            arguments = listOf(
                navArgument("fileId") { type = NavType.StringType },
                navArgument("fileName") { type = NavType.StringType },
                navArgument("allowFallback") {
                    type = NavType.BoolType
                    defaultValue = true
                },
                navArgument("handoff") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            enterTransition = { fadeIn(animationSpec = tween(140)) },
            exitTransition = { fadeOut(animationSpec = tween(140)) },
            popEnterTransition = { fadeIn(animationSpec = tween(140)) },
            popExitTransition = { fadeOut(animationSpec = tween(140)) }
        ) { backStackEntry ->
            val ctx = LocalContext.current
            val fileId = backStackEntry.arguments?.getString("fileId").orEmpty()
            val fileName = decodeRouteValue(backStackEntry.arguments?.getString("fileName").orEmpty())
            val allowFallback = backStackEntry.arguments?.getBoolean("allowFallback") ?: true
            val handoff = backStackEntry.arguments?.getBoolean("handoff") ?: false

            MpvPlayerScreen(
                onBack = { navController.popBackStack() },
                allowEngineFallback = allowFallback,
                switchingMessage = if (handoff) "Switching to MPV" else null,
                onFallbackToExo = {
                    navController.navigateToPlayback(
                        playerRoute(fileId, fileName, allowFallback = false, handoff = true),
                        replaceCurrent = true
                    )
                },
                onSwitchToExo = {
                    navController.navigateToPlayback(
                        playerRoute(fileId, fileName, allowFallback = true, handoff = true),
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
                            // Start foreground service to keep proxy alive while external player runs
                            StreamProxyService.start(ctx)
                            val proxyUrl = StreamProxyServer.instanceUrl?.let { base -> "$base/stream/$fileId" }
                            if (proxyUrl != null) ExternalPlayerLauncher.launch(ctx, proxyUrl, fileName)
                        }
                    }
                }
            )
        }

        // TV Catalog — uses the TV-optimized D-pad-friendly screen
        composable(Routes.TV_CATALOG) {
            val ctx = LocalContext.current
            TvCatalogScreen(
                onPlayFile = { fileId, fileName ->
                    // TV always uses ExoPlayer with the TV player UI
                    navController.navigateToPlayback(playerRoute(fileId, fileName))
                },
                onLogout = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // TMDB See All — vertical grid for a category
        composable(
            route = Routes.TMDB_SEE_ALL,
            arguments = listOf(
                navArgument("category") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category") ?: ""
            // Get catalog ViewModel from the parent catalog route
            val catalogEntry = remember(backStackEntry) {
                navController.getBackStackEntry(Routes.CATALOG)
            }
            val catalogVm: com.mkbhdana.streamhive.catalog.CatalogViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(catalogEntry)
            val catalogState by catalogVm.uiState.collectAsState()

            val files = when (category) {
                "movies" -> catalogState.homeSections
                    .filter { it.typeLabel == "Movie" }
                    .flatMap { it.items }
                    .distinctBy { it.id }
                "tv" -> catalogState.homeSections
                    .filter { it.typeLabel == "Series" }
                    .flatMap { it.items }
                    .distinctBy { it.id }
                "anime" -> catalogState.homeSections
                    .filter { it.typeLabel == "Anime" }
                    .flatMap { it.items }
                    .distinctBy { it.id }
                else -> emptyList()
            }

            TmdbSeeAllScreen(
                category = category,
                files = files,
                tmdbMetadata = catalogState.tmdbMetadata,
                onBack = { navController.popBackStack() },
                onNavigateToInfo = { driveFileId, mediaType ->
                    navController.navigate("media_info/$driveFileId?mediaType=$mediaType")
                }
            )
        }
    }
}

private fun playerRoute(
    fileId: String,
    fileName: String,
    allowFallback: Boolean = true,
    handoff: Boolean = false
): String {
    return "player/$fileId/${encodeRouteValue(fileName)}?allowFallback=$allowFallback&handoff=$handoff"
}

private fun mpvPlayerRoute(
    fileId: String,
    fileName: String,
    allowFallback: Boolean = true,
    handoff: Boolean = false
): String {
    return "mpv_player/$fileId/${encodeRouteValue(fileName)}?allowFallback=$allowFallback&handoff=$handoff"
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
