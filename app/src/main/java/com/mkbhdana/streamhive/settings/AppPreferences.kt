package com.mkbhdana.streamhive.settings

import android.content.Context
import android.content.SharedPreferences
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.ui.image.PosterSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central preferences for all app settings.
 * Replaces the old PlayerPreferences with comprehensive settings.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "streamhive_preferences", Context.MODE_PRIVATE
    )

    // ──── Type-safe numeric access ────
    //
    // A backup restore writes values back from JSON, and org.json narrows any integral
    // number that fits into an Int. That stored Long-typed preferences (timestamps, ARGB
    // colours) as Int, and SharedPreferences then throws ClassCastException on read —
    // crashing the app after a restore. Reads repair such a value in place rather than
    // throwing, so installs that already imported a bad backup recover on their own.

    private fun longPref(key: String, default: Long): Long = try {
        prefs.getLong(key, default)
    } catch (_: ClassCastException) {
        val repaired = (prefs.all[key] as? Number)?.toLong() ?: default
        prefs.edit().putLong(key, repaired).apply()
        repaired
    }

    private fun floatPref(key: String, default: Float): Float = try {
        prefs.getFloat(key, default)
    } catch (_: ClassCastException) {
        val repaired = (prefs.all[key] as? Number)?.toFloat() ?: default
        prefs.edit().putFloat(key, repaired).apply()
        repaired
    }

    private fun intPref(key: String, default: Int): Int = try {
        prefs.getInt(key, default)
    } catch (_: ClassCastException) {
        val repaired = (prefs.all[key] as? Number)?.toInt() ?: default
        prefs.edit().putInt(key, repaired).apply()
        repaired
    }

    // ──── Drive Settings ────

    var selectedDriveId: String
        get() = prefs.getString(KEY_SELECTED_DRIVE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_DRIVE, value).apply()

    // ──── Player Settings ────

    var preferredEngine: PlayerEngine
        get() {
            val value = prefs.getString(KEY_ENGINE, PlayerEngine.EXO_PLAYER.name)
            return try {
                PlayerEngine.valueOf(value ?: PlayerEngine.EXO_PLAYER.name)
            } catch (_: Exception) {
                PlayerEngine.EXO_PLAYER
            }
        }
        set(value) = prefs.edit().putString(KEY_ENGINE, value.name).apply()

    var exoDecoder: String
        get() = prefs.getString(KEY_EXO_DECODER, "hw+") ?: "hw+"
        set(value) = prefs.edit().putString(KEY_EXO_DECODER, value).apply()

    var mpvDecoder: String
        get() = prefs.getString(KEY_MPV_DECODER, "hw+") ?: "hw+"
        set(value) = prefs.edit().putString(KEY_MPV_DECODER, value).apply()

    var mapDv7ToHevc: Boolean
        get() = prefs.getBoolean(KEY_MAP_DV7_TO_HEVC, false)
        set(value) = prefs.edit().putBoolean(KEY_MAP_DV7_TO_HEVC, value).apply()

    // ──── MPV Engine Settings ────

    /** Built-in mpv profile: "default" (none), "fast", or "high-quality". */
    var mpvProfile: String
        get() = prefs.getString(KEY_MPV_PROFILE, MPV_PROFILE_DEFAULT) ?: MPV_PROFILE_DEFAULT
        set(value) = prefs.edit().putString(KEY_MPV_PROFILE, value).apply()

    /** Render with vo=gpu-next instead of vo=gpu. */
    var mpvGpuNext: Boolean
        get() = prefs.getBoolean(KEY_MPV_GPU_NEXT, MPV_GPU_NEXT_DEFAULT)
        set(value) = prefs.edit().putBoolean(KEY_MPV_GPU_NEXT, value).apply()

    /** Use the Vulkan gpu-context (androidvk) instead of OpenGL ES. */
    var mpvUseVulkan: Boolean
        get() = prefs.getBoolean(KEY_MPV_USE_VULKAN, MPV_USE_VULKAN_DEFAULT)
        set(value) = prefs.edit().putBoolean(KEY_MPV_USE_VULKAN, value).apply()

    /** Debanding mode: "none", "cpu" (gradfun filter), or "gpu" (deband). */
    var mpvDebanding: String
        get() = prefs.getString(KEY_MPV_DEBANDING, MPV_DEBANDING_DEFAULT) ?: MPV_DEBANDING_DEFAULT
        set(value) = prefs.edit().putString(KEY_MPV_DEBANDING, value).apply()

    /** Force yuv420p pixel format (may fix black screens on some codecs). */
    var mpvUseYuv420p: Boolean
        get() = prefs.getBoolean(KEY_MPV_YUV420P, MPV_YUV420P_DEFAULT)
        set(value) = prefs.edit().putBoolean(KEY_MPV_YUV420P, value).apply()

    /** User-authored mpv.conf contents ("" = none). Written to config-dir before init. */
    var mpvConfText: String
        get() = prefs.getString(KEY_MPV_CONF, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MPV_CONF, value).apply()

    /** User-authored input.conf contents ("" = none). */
    var mpvInputConfText: String
        get() = prefs.getString(KEY_MPV_INPUT_CONF, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MPV_INPUT_CONF, value).apply()

    /**
     * Push the artwork preferences into [PosterSource], which builds poster URLs from
     * plain (non-injected) helpers. Call after startup and after anything that can change
     * these values behind the settings UI, such as a backup restore.
     */
    fun applyPosterSourceSettings() {
        PosterSource.thirdPartyEnabled = thirdPartyPostersEnabled
        PosterSource.thirdPartyTemplate = betterPosterTemplate
    }

    /** Restore the MPV engine settings (not the conf files) to their defaults. */
    fun resetMpvEngineSettings() {
        prefs.edit()
            .remove(KEY_MPV_PROFILE)
            .remove(KEY_MPV_GPU_NEXT)
            .remove(KEY_MPV_USE_VULKAN)
            .remove(KEY_MPV_DEBANDING)
            .remove(KEY_MPV_YUV420P)
            .apply()
    }

    var tunneledPlaybackEnabled: Boolean
        get() = prefs.getBoolean(KEY_TUNNELED_PLAYBACK, false)
        set(value) = prefs.edit().putBoolean(KEY_TUNNELED_PLAYBACK, value).apply()

    var defaultResizeMode: String
        get() = prefs.getString(KEY_RESIZE_MODE, "fit") ?: "fit"
        set(value) = prefs.edit().putString(KEY_RESIZE_MODE, value).apply()

    /**
     * Use the third-party (btttr.cc) poster art instead of TMDB's, where an IMDb id is
     * known. Off by default — it depends on a third-party host staying available.
     */
    var thirdPartyPostersEnabled: Boolean
        get() = prefs.getBoolean(KEY_THIRD_PARTY_POSTERS, false)
        set(value) = prefs.edit().putBoolean(KEY_THIRD_PARTY_POSTERS, value).apply()

    /** Poster URL template containing `{imdb_id}`. See PosterSource for the accepted shape. */
    var betterPosterTemplate: String
        get() = prefs.getString(KEY_BETTER_POSTER_TEMPLATE, null)
            ?.takeIf { it.isNotBlank() }
            ?: PosterSource.DEFAULT_TEMPLATE
        set(value) = prefs.edit().putString(KEY_BETTER_POSTER_TEMPLATE, value).apply()

    var isGridView: Boolean
        get() = prefs.getBoolean(KEY_IS_GRID_VIEW, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_GRID_VIEW, value).apply()

    var keepServerRunning: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SERVER_RUNNING, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SERVER_RUNNING, value).apply()

    var sourcePriorityResolutions: List<String>
        get() = getSourcePriorityOrder(KEY_SOURCE_PRIORITY_RESOLUTIONS, SourcePriorityOptions.resolutions)
        set(value) = setSourcePriorityOrder(KEY_SOURCE_PRIORITY_RESOLUTIONS, value, SourcePriorityOptions.resolutions)

    var sourcePriorityVideoFormats: List<String>
        get() = getSourcePriorityOrder(KEY_SOURCE_PRIORITY_VIDEO_FORMATS, SourcePriorityOptions.videoFormats)
        set(value) = setSourcePriorityOrder(KEY_SOURCE_PRIORITY_VIDEO_FORMATS, value, SourcePriorityOptions.videoFormats)

    var sourcePriorityDecoders: List<String>
        get() = getSourcePriorityOrder(KEY_SOURCE_PRIORITY_DECODERS, SourcePriorityOptions.decoders)
        set(value) = setSourcePriorityOrder(KEY_SOURCE_PRIORITY_DECODERS, value, SourcePriorityOptions.decoders)

    var sourcePriorityContainers: List<String>
        get() = getSourcePriorityOrder(KEY_SOURCE_PRIORITY_CONTAINERS, SourcePriorityOptions.containers)
        set(value) = setSourcePriorityOrder(KEY_SOURCE_PRIORITY_CONTAINERS, value, SourcePriorityOptions.containers)

    val sourcePriorityConfig: SourcePriorityConfig
        get() = SourcePriorityConfig(
            resolutionOrder = sourcePriorityResolutions,
            videoFormatOrder = sourcePriorityVideoFormats,
            decoderOrder = sourcePriorityDecoders,
            containerOrder = sourcePriorityContainers
        )

    // ──── Gesture Settings ────

    var gestureVolumeEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_VOLUME, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_VOLUME, value).apply()

    var gestureBrightnessEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_BRIGHTNESS, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_BRIGHTNESS, value).apply()

    var gestureSeekEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_SEEK, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_SEEK, value).apply()

    var gestureDoubleTapEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_DOUBLE_TAP, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_DOUBLE_TAP, value).apply()

    var tapSeekDuration: Int
        get() = intPref(KEY_TAP_SEEK_DURATION, 10)
        set(value) = prefs.edit().putInt(KEY_TAP_SEEK_DURATION, value.coerceIn(10, 60)).apply()

    var gestureZoomEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_ZOOM, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_ZOOM, value).apply()

    var gestureSpeedPressEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_SPEED_PRESS, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_SPEED_PRESS, value).apply()

    var gestureLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_LOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_LOCK, value).apply()

    var hapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()

    /** Sensitivity multiplier: 0.5f (low) to 2.0f (high), default 1.0f */
    var gestureSensitivity: Float
        get() = floatPref(KEY_GESTURE_SENSITIVITY, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_GESTURE_SENSITIVITY, value.coerceIn(0.5f, 2.0f)).apply()

    // ──── Subtitle Settings ────

    var exoSubtitleFontSize: Int
        get() = intPref(KEY_EXO_SUBTITLE_FONT_SIZE, intPref("subtitle_font_size", 18))
        set(value) = prefs.edit().putInt(KEY_EXO_SUBTITLE_FONT_SIZE, value.coerceIn(10, 48)).apply()

    var mpvSubtitleFontSize: Int
        get() = intPref(KEY_MPV_SUBTITLE_FONT_SIZE, intPref("subtitle_font_size", 18))
        set(value) = prefs.edit().putInt(KEY_MPV_SUBTITLE_FONT_SIZE, value.coerceIn(10, 48)).apply()

    var exoSubtitleColor: Long
        get() = longPref(KEY_EXO_SUBTITLE_COLOR, longPref("subtitle_color", 0xFFFFFFFF))
        set(value) = prefs.edit().putLong(KEY_EXO_SUBTITLE_COLOR, value).apply()

    var mpvSubtitleColor: Long
        get() = longPref(KEY_MPV_SUBTITLE_COLOR, longPref("subtitle_color", 0xFFFFFFFF))
        set(value) = prefs.edit().putLong(KEY_MPV_SUBTITLE_COLOR, value).apply()

    var exoSubtitleBgOpacity: Float
        get() = floatPref(KEY_EXO_SUBTITLE_BG_OPACITY, floatPref("subtitle_bg_opacity", 0.0f))
        set(value) = prefs.edit().putFloat(KEY_EXO_SUBTITLE_BG_OPACITY, value.coerceIn(0f, 1f)).apply()

    var mpvSubtitleBgOpacity: Float
        get() = floatPref(KEY_MPV_SUBTITLE_BG_OPACITY, floatPref("subtitle_bg_opacity", 0.0f))
        set(value) = prefs.edit().putFloat(KEY_MPV_SUBTITLE_BG_OPACITY, value.coerceIn(0f, 1f)).apply()

    var exoSubtitleEdgeType: String
        get() = prefs.getString(KEY_EXO_SUBTITLE_EDGE_TYPE, prefs.getString("subtitle_edge_type", "outline")) ?: "outline"
        set(value) = prefs.edit().putString(KEY_EXO_SUBTITLE_EDGE_TYPE, value).apply()

    var mpvSubtitleEdgeType: String
        get() = prefs.getString(KEY_MPV_SUBTITLE_EDGE_TYPE, prefs.getString("subtitle_edge_type", "outline")) ?: "outline"
        set(value) = prefs.edit().putString(KEY_MPV_SUBTITLE_EDGE_TYPE, value).apply()

    var exoSubtitleEdgeSize: Int
        get() = intPref(KEY_EXO_SUBTITLE_EDGE_SIZE, intPref("subtitle_edge_size", 0))
        set(value) = prefs.edit().putInt(KEY_EXO_SUBTITLE_EDGE_SIZE, value.coerceIn(0, 20)).apply()

    var mpvSubtitleEdgeSize: Int
        get() = intPref(KEY_MPV_SUBTITLE_EDGE_SIZE, intPref("subtitle_edge_size", 0))
        set(value) = prefs.edit().putInt(KEY_MPV_SUBTITLE_EDGE_SIZE, value.coerceIn(0, 20)).apply()

    var exoSubtitleOutlineColor: Long
        get() = longPref(KEY_EXO_SUBTITLE_OUTLINE_COLOR, longPref("subtitle_outline_color", 0xFF000000))
        set(value) = prefs.edit().putLong(KEY_EXO_SUBTITLE_OUTLINE_COLOR, value).apply()

    var mpvSubtitleOutlineColor: Long
        get() = longPref(KEY_MPV_SUBTITLE_OUTLINE_COLOR, longPref("subtitle_outline_color", 0xFF000000))
        set(value) = prefs.edit().putLong(KEY_MPV_SUBTITLE_OUTLINE_COLOR, value).apply()

    /** Allow the volume gesture to push app-level gain above 100% (up to 200%). */
    var volumeBoostEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_BOOST, false)
        set(value) = prefs.edit().putBoolean(KEY_VOLUME_BOOST, value).apply()

    var preferredAudioLanguage: String
        get() = prefs.getString(KEY_PREF_AUDIO_LANG, "original") ?: "original"
        set(value) = prefs.edit().putString(KEY_PREF_AUDIO_LANG, value).apply()

    var preferredSubtitleLanguage: String
        get() = prefs.getString(KEY_PREF_SUBTITLE_LANG, "none") ?: "none"
        set(value) = prefs.edit().putString(KEY_PREF_SUBTITLE_LANG, value).apply()

    var subtitleExcludeLanguages: Set<String>
        get() = prefs.getStringSet(KEY_SUBTITLE_EXCLUDE_LANGS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_SUBTITLE_EXCLUDE_LANGS, value).apply()

    var exoSubtitlePosition: Int
        get() = intPref(KEY_EXO_SUBTITLE_POSITION, intPref("subtitle_position", 90))
        set(value) = prefs.edit().putInt(KEY_EXO_SUBTITLE_POSITION, value.coerceIn(0, 100)).apply()

    var mpvSubtitlePosition: Int
        get() = intPref(KEY_MPV_SUBTITLE_POSITION, intPref("subtitle_position", 90))
        set(value) = prefs.edit().putInt(KEY_MPV_SUBTITLE_POSITION, value.coerceIn(0, 100)).apply()

    var libassSubtitlesEnabled: Boolean
        get() = prefs.getBoolean(KEY_LIBASS_SUBTITLES_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LIBASS_SUBTITLES_ENABLED, value).apply()

    var exoOverrideAssSubtitleStyles: Boolean
        get() = prefs.getBoolean(KEY_EXO_OVERRIDE_ASS_SUBTITLE_STYLES, prefs.getBoolean("override_ass_subtitle_styles", false))
        set(value) = prefs.edit().putBoolean(KEY_EXO_OVERRIDE_ASS_SUBTITLE_STYLES, value).apply()

    var mpvOverrideAssSubtitleStyles: Boolean
        get() = prefs.getBoolean(KEY_MPV_OVERRIDE_ASS_SUBTITLE_STYLES, prefs.getBoolean("override_ass_subtitle_styles", false))
        set(value) = prefs.edit().putBoolean(KEY_MPV_OVERRIDE_ASS_SUBTITLE_STYLES, value).apply()

    /** Global subtitle scale multiplier (0.5 to 3.0, default 1.0). Applied as MPV sub-scale. */
    var subtitleScale: Float
        get() = floatPref(KEY_SUBTITLE_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SUBTITLE_SCALE, value.coerceIn(0.5f, 3.0f)).apply()

    /** Subtitle font name (default 'sans-serif'). Applied as MPV sub-font. */
    var subtitleFont: String
        get() = prefs.getString(KEY_SUBTITLE_FONT, "sans-serif") ?: "sans-serif"
        set(value) = prefs.edit().putString(KEY_SUBTITLE_FONT, value).apply()

    /** Subtitle bold. Applied as MPV sub-bold. */
    var subtitleBold: Boolean
        get() = prefs.getBoolean(KEY_SUBTITLE_BOLD, false)
        set(value) = prefs.edit().putBoolean(KEY_SUBTITLE_BOLD, value).apply()

    /** Subtitle italic. Applied as MPV sub-italic. */
    var subtitleItalic: Boolean
        get() = prefs.getBoolean(KEY_SUBTITLE_ITALIC, false)
        set(value) = prefs.edit().putBoolean(KEY_SUBTITLE_ITALIC, value).apply()

    /** Subtitle text alignment: left, center, right. Applied as MPV sub-justify. */
    var subtitleAlignment: String
        get() = prefs.getString(KEY_SUBTITLE_ALIGNMENT, "center") ?: "center"
        set(value) = prefs.edit().putString(KEY_SUBTITLE_ALIGNMENT, value).apply()

    // ──── TMDB Settings ────

    var tmdbApiKey: String
        get() = prefs.getString(KEY_TMDB_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TMDB_API_KEY, value).apply()

    /** Comma-separated folder IDs mapped as movies */
    var tmdbMovieFolders: Set<String>
        get() = HashSet(prefs.getStringSet(KEY_TMDB_MOVIE_FOLDERS, emptySet()) ?: emptySet())
        set(value) = prefs.edit().putStringSet(KEY_TMDB_MOVIE_FOLDERS, HashSet(value)).apply()

    var tmdbTvFolders: Set<String>
        get() = HashSet(prefs.getStringSet(KEY_TMDB_TV_FOLDERS, emptySet()) ?: emptySet())
        set(value) = prefs.edit().putStringSet(KEY_TMDB_TV_FOLDERS, HashSet(value)).apply()

    var tmdbRecentFolders: Set<String>
        get() = HashSet(prefs.getStringSet(KEY_TMDB_RECENT_FOLDERS, emptySet()) ?: emptySet())
        set(value) = prefs.edit().putStringSet(KEY_TMDB_RECENT_FOLDERS, HashSet(value)).apply()

    /** Ordered list of all TMDB folder IDs for display sequence. Stored as comma-separated string. */
    var tmdbFolderOrder: List<String>
        get() {
            val csv = prefs.getString(KEY_TMDB_FOLDER_ORDER, "") ?: ""
            return if (csv.isBlank()) emptyList() else csv.split(",")
        }
        set(value) = prefs.edit().putString(KEY_TMDB_FOLDER_ORDER, value.joinToString(",")).apply()

    var lastUpdateCheckAt: Long
        get() = longPref(KEY_LAST_UPDATE_CHECK_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_AT, value).apply()

    var dismissedUpdateTag: String
        get() = prefs.getString(KEY_DISMISSED_UPDATE_TAG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DISMISSED_UPDATE_TAG, value).apply()

    var catalogSettingsLastChanged: Long
        get() = longPref(KEY_CATALOG_SETTINGS_LAST_CHANGED, 0L)
        set(value) = prefs.edit().putLong(KEY_CATALOG_SETTINGS_LAST_CHANGED, value).apply()

    // ──── Helpers ────

    fun isMpvAvailable(): Boolean {
        return try {
            Class.forName("is.xyz.mpv.MPV")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private fun getSourcePriorityOrder(
        key: String,
        options: List<SourcePriorityOption>
    ): List<String> {
        val stored = prefs.getString(key, "") ?: ""
        if (stored.isBlank()) return emptyList()
        return SourcePriorityOptions.sanitizeOrder(stored.split("|"), options)
    }

    private fun setSourcePriorityOrder(
        key: String,
        value: List<String>,
        options: List<SourcePriorityOption>
    ) {
        prefs.edit()
            .putString(key, SourcePriorityOptions.sanitizeOrder(value, options).joinToString("|"))
            .apply()
    }

    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun exportToJson(): String {
        val allPrefs = prefs.all
        val jsonObject = org.json.JSONObject()
        for ((key, value) in allPrefs) {
            if (value is Set<*>) {
                val jsonArray = org.json.JSONArray()
                value.forEach { jsonArray.put(it) }
                jsonObject.put(key, jsonArray)
            } else {
                jsonObject.put(key, value)
            }
        }
        return jsonObject.toString(2)
    }

    fun importFromJson(jsonString: String): Boolean {
        return try {
            val jsonObject = org.json.JSONObject(jsonString)
            val editor = prefs.edit()
            
            // Optional: clear existing first if we want a pure import, 
            // but usually we just overwrite keys present in json.
            
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObject.get(key)
                // JSON carries no type width: org.json narrows any integral number that
                // fits into an Int, and widens every fractional one to Double. Writing a
                // value back under the wrong type makes the matching getter throw
                // ClassCastException later, so the app's own type for a key wins.
                when {
                    key in LONG_KEYS && value is Number -> editor.putLong(key, value.toLong())
                    key in FLOAT_KEYS && value is Number -> editor.putFloat(key, value.toFloat())
                    value is Boolean -> editor.putBoolean(key, value)
                    value is Int -> editor.putInt(key, value)
                    value is Long -> editor.putLong(key, value)
                    value is Float -> editor.putFloat(key, value)
                    // Only Float preferences are ever fractional in this app.
                    value is Double -> editor.putFloat(key, value.toFloat())
                    value is String -> editor.putString(key, value)
                    value is org.json.JSONArray -> {
                        val set = HashSet<String>()
                        for (i in 0 until value.length()) {
                            set.add(value.getString(i))
                        }
                        editor.putStringSet(key, set)
                    }
                }
            }
            editor.apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    companion object {
        /**
         * Preferences read with `getLong` / `getFloat`. A restore must write them back
         * under these types no matter how org.json parsed the number — see importFromJson.
         * Legacy pre-split keys are listed too, since they are still read as fallbacks.
         */
        private val LONG_KEYS = setOf(
            "exo_subtitle_color", "mpv_subtitle_color", "subtitle_color",
            "exo_subtitle_outline_color", "mpv_subtitle_outline_color", "subtitle_outline_color",
            "last_update_check_at", "catalog_settings_last_changed"
        )

        private val FLOAT_KEYS = setOf(
            "gesture_sensitivity", "subtitle_scale",
            "exo_subtitle_bg_opacity", "mpv_subtitle_bg_opacity", "subtitle_bg_opacity"
        )

        // Drive
        private const val KEY_SELECTED_DRIVE = "selected_drive_id"

        // Player
        private const val KEY_ENGINE = "player_engine"
        private const val KEY_EXO_DECODER = "exo_decoder"
        private const val KEY_MPV_DECODER = "mpv_decoder"
        private const val KEY_MPV_PROFILE = "mpv_profile"
        private const val KEY_MPV_GPU_NEXT = "mpv_gpu_next"
        private const val KEY_MPV_USE_VULKAN = "mpv_use_vulkan"
        private const val KEY_MPV_DEBANDING = "mpv_debanding"
        private const val KEY_MPV_YUV420P = "mpv_yuv420p"
        private const val KEY_MPV_CONF = "mpv_conf_text"
        private const val KEY_MPV_INPUT_CONF = "mpv_input_conf_text"
        private const val KEY_VOLUME_BOOST = "volume_boost_enabled"
        private const val KEY_THIRD_PARTY_POSTERS = "third_party_posters_enabled"
        private const val KEY_BETTER_POSTER_TEMPLATE = "better_poster_url_template"

        const val MPV_PROFILE_DEFAULT = "fast"
        const val MPV_GPU_NEXT_DEFAULT = false
        const val MPV_USE_VULKAN_DEFAULT = false
        const val MPV_DEBANDING_DEFAULT = "none"
        const val MPV_YUV420P_DEFAULT = false
        private const val KEY_MAP_DV7_TO_HEVC = "map_dv7_to_hevc"
        private const val KEY_TUNNELED_PLAYBACK = "tunneled_playback"
        private const val KEY_RESIZE_MODE = "default_resize_mode"
        private const val KEY_KEEP_SERVER_RUNNING = "keep_server_running"
        private const val KEY_SOURCE_PRIORITY_RESOLUTIONS = "source_priority_resolutions"
        private const val KEY_SOURCE_PRIORITY_VIDEO_FORMATS = "source_priority_video_formats"
        private const val KEY_SOURCE_PRIORITY_DECODERS = "source_priority_decoders"
        private const val KEY_SOURCE_PRIORITY_CONTAINERS = "source_priority_containers"

        // Gestures
        private const val KEY_GESTURE_VOLUME = "gesture_volume_enabled"
        private const val KEY_GESTURE_BRIGHTNESS = "gesture_brightness_enabled"
        private const val KEY_GESTURE_SEEK = "gesture_seek_enabled"
        private const val KEY_GESTURE_DOUBLE_TAP = "gesture_double_tap_enabled"
        private const val KEY_TAP_SEEK_DURATION = "tap_seek_duration"
        private const val KEY_GESTURE_ZOOM = "gesture_zoom_enabled"
        private const val KEY_GESTURE_SPEED_PRESS = "gesture_speed_press_enabled"
        private const val KEY_GESTURE_LOCK = "gesture_lock_enabled"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
        private const val KEY_GESTURE_SENSITIVITY = "gesture_sensitivity"

        // Subtitles
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_CATALOG_SETTINGS_LAST_CHANGED = "catalog_settings_last_changed"
        private const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        private const val KEY_EXO_SUBTITLE_FONT_SIZE = "exo_subtitle_font_size"
        private const val KEY_MPV_SUBTITLE_FONT_SIZE = "mpv_subtitle_font_size"
        private const val KEY_EXO_SUBTITLE_COLOR = "exo_subtitle_color"
        private const val KEY_MPV_SUBTITLE_COLOR = "mpv_subtitle_color"
        private const val KEY_EXO_SUBTITLE_BG_OPACITY = "exo_subtitle_bg_opacity"
        private const val KEY_MPV_SUBTITLE_BG_OPACITY = "mpv_subtitle_bg_opacity"
        private const val KEY_EXO_SUBTITLE_POSITION = "exo_subtitle_position"
        private const val KEY_MPV_SUBTITLE_POSITION = "mpv_subtitle_position"
        private const val KEY_EXO_SUBTITLE_EDGE_TYPE = "exo_subtitle_edge_type"
        private const val KEY_MPV_SUBTITLE_EDGE_TYPE = "mpv_subtitle_edge_type"
        private const val KEY_EXO_SUBTITLE_EDGE_SIZE = "exo_subtitle_edge_size"
        private const val KEY_MPV_SUBTITLE_EDGE_SIZE = "mpv_subtitle_edge_size"
        private const val KEY_EXO_SUBTITLE_OUTLINE_COLOR = "exo_subtitle_outline_color"
        private const val KEY_MPV_SUBTITLE_OUTLINE_COLOR = "mpv_subtitle_outline_color"
        private const val KEY_PREF_AUDIO_LANG = "pref_audio_lang"
        private const val KEY_PREF_SUBTITLE_LANG = "pref_subtitle_lang"
        private const val KEY_SUBTITLE_EXCLUDE_LANGS = "subtitle_exclude_langs"
        private const val KEY_LIBASS_SUBTITLES_ENABLED = "libass_subtitles_enabled"
        private const val KEY_EXO_OVERRIDE_ASS_SUBTITLE_STYLES = "exo_override_ass_subtitle_styles"
        private const val KEY_MPV_OVERRIDE_ASS_SUBTITLE_STYLES = "mpv_override_ass_subtitle_styles"
        private const val KEY_SUBTITLE_SCALE = "subtitle_scale"
        private const val KEY_SUBTITLE_FONT = "subtitle_font"
        private const val KEY_SUBTITLE_BOLD = "subtitle_bold"
        private const val KEY_SUBTITLE_ITALIC = "subtitle_italic"
        private const val KEY_SUBTITLE_ALIGNMENT = "subtitle_alignment"

        // TMDB
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_TMDB_MOVIE_FOLDERS = "tmdb_movie_folders"
        private const val KEY_TMDB_TV_FOLDERS = "tmdb_tv_folders"
        private const val KEY_TMDB_RECENT_FOLDERS = "tmdb_recent_folders"
        private const val KEY_TMDB_FOLDER_ORDER = "tmdb_folder_order"
        private const val KEY_IS_GRID_VIEW = "is_grid_view"
        private const val KEY_LAST_UPDATE_CHECK_AT = "last_update_check_at"
        private const val KEY_DISMISSED_UPDATE_TAG = "dismissed_update_tag"
    }
}
