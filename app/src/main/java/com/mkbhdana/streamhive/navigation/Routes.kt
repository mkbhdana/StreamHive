package com.mkbhdana.streamhive.navigation

/**
 * Type-safe route definitions for Navigation 3.
 *
 * Each route is a data class or data object that serves as both:
 *   1. A navigation key (identity on the back stack)
 *   2. A strongly-typed argument container
 */

// ──── Auth ────
data object AuthRoute

// ──── Main / Catalog tabs (top-level routes) ────
data object HomeRoute
data object FoldersRoute
data object SearchRoute

// ──── Player ────
data class PlayerRoute(
    val fileId: String,
    val fileName: String,
    val allowFallback: Boolean = true,
    val handoff: Boolean = false,
    val decoder: String = "",
    val instanceId: Long = System.nanoTime() // Unique per play to force fresh ViewModel
)

data class MpvPlayerRoute(
    val fileId: String,
    val fileName: String,
    val allowFallback: Boolean = true,
    val handoff: Boolean = false,
    val decoder: String = "",
    val instanceId: Long = System.nanoTime() // Unique per play to force fresh ViewModel
)

// ──── Settings ────
data object SettingsRoute
data object SettingsPlayerRoute
data object SettingsGesturesRoute
data object SettingsSubtitlesRoute
data object SettingsTmdbRoute
data object SettingsStorageRoute

// ──── Media Info ────
data class MediaInfoRoute(
    val driveFileId: String,
    val mediaType: String = "auto"
)

// ──── TMDB See All ────
data class TmdbSeeAllRoute(val folderId: String)

/**
 * Legacy string constants for backward compatibility with SettingsScreen's
 * onNavigate(String) callback. These are mapped to Nav3 routes in AppNavigation.
 */
object Routes {
    const val SETTINGS_PLAYER = "settings/player"
    const val SETTINGS_GESTURES = "settings/gestures"
    const val SETTINGS_SUBTITLES = "settings/subtitles"
    const val SETTINGS_TMDB = "settings/tmdb"
    const val SETTINGS_STORAGE = "settings/storage"
}
