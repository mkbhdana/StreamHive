package com.driveplay.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.driveplay.app.auth.AuthScreen
import com.driveplay.app.catalog.CatalogScreen
import com.driveplay.app.catalog.TmdbSeeAllScreen
import com.driveplay.app.catalog.TvCatalogScreen
import com.driveplay.app.catalog.info.MediaInfoScreen
import com.driveplay.app.player.ExternalPlayerLauncher
import com.driveplay.app.player.PlayerScreen
import com.driveplay.app.player.proxy.StreamProxyServer
import com.driveplay.app.player.mpv.MpvPlayerScreen
import com.driveplay.app.player.mpv.PlayerEngine
import com.driveplay.app.settings.SettingsScreen
import android.widget.Toast

object Routes {
    const val AUTH = "auth"
    const val CATALOG = "catalog"
    const val PLAYER = "player/{fileId}/{fileName}"
    const val MPV_PLAYER = "mpv_player/{fileId}/{fileName}"
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

    NavHost(
        navController = navController,
        startDestination = Routes.AUTH
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
                    val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8")
                    when (engine) {
                        PlayerEngine.EXO_PLAYER -> {
                            navController.navigate("player/$fileId/$encodedName")
                        }
                        PlayerEngine.MPV -> {
                            navController.navigate("mpv_player/$fileId/$encodedName")
                        }
                        PlayerEngine.EXTERNAL -> {
                            // Launch external player via proxy
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
                navArgument("fileName") { type = NavType.StringType }
            )
        ) {
            PlayerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.MPV_PLAYER,
            arguments = listOf(
                navArgument("fileId") { type = NavType.StringType },
                navArgument("fileName") { type = NavType.StringType }
            )
        ) {
            MpvPlayerScreen(
                onBack = { navController.popBackStack() }
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
                    val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8")
                    when (engine) {
                        PlayerEngine.EXO_PLAYER -> navController.navigate("player/$fileId/$encodedName")
                        PlayerEngine.MPV -> navController.navigate("mpv_player/$fileId/$encodedName")
                        PlayerEngine.EXTERNAL -> {
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
                    val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8")
                    // TV always uses ExoPlayer with the TV player UI
                    navController.navigate("player/$fileId/$encodedName")
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
            val catalogVm: com.driveplay.app.catalog.CatalogViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(catalogEntry)
            val catalogState by catalogVm.uiState.collectAsState()

            val files = when (category) {
                "movies" -> catalogState.homeMovies
                "tv" -> catalogState.homeTvShows
                "anime" -> catalogState.homeAnime
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
