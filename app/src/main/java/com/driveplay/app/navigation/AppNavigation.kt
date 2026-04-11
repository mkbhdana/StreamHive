package com.driveplay.app.navigation

import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.driveplay.app.auth.AuthScreen
import com.driveplay.app.catalog.CatalogScreen
import com.driveplay.app.player.PlayerScreen
import com.driveplay.app.player.mpv.MpvPlayerScreen
import com.driveplay.app.player.mpv.PlayerEngine
import com.driveplay.app.settings.SettingsScreen

object Routes {
    const val AUTH = "auth"
    const val CATALOG = "catalog"
    const val PLAYER = "player/{fileId}/{fileName}"
    const val MPV_PLAYER = "mpv_player/{fileId}/{fileName}"
    const val SETTINGS = "settings"

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
                    }
                },
                onLogout = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
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

        // TV Catalog (placeholder - keep existing if available)
        composable(Routes.TV_CATALOG) {
            CatalogScreen(
                onPlayFile = { fileId, fileName, engine ->
                    val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8")
                    when (engine) {
                        PlayerEngine.EXO_PLAYER -> navController.navigate("player/$fileId/$encodedName")
                        PlayerEngine.MPV -> navController.navigate("mpv_player/$fileId/$encodedName")
                    }
                },
                onLogout = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
    }
}
