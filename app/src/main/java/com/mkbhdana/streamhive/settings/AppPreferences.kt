package com.mkbhdana.streamhive.settings

import android.content.Context
import android.content.SharedPreferences
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
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

    var tunneledPlaybackEnabled: Boolean
        get() = prefs.getBoolean(KEY_TUNNELED_PLAYBACK, false)
        set(value) = prefs.edit().putBoolean(KEY_TUNNELED_PLAYBACK, value).apply()

    var defaultResizeMode: String
        get() = prefs.getString(KEY_RESIZE_MODE, "fit") ?: "fit"
        set(value) = prefs.edit().putString(KEY_RESIZE_MODE, value).apply()

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
        get() = prefs.getInt(KEY_TAP_SEEK_DURATION, 10)
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
        get() = prefs.getFloat(KEY_GESTURE_SENSITIVITY, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_GESTURE_SENSITIVITY, value.coerceIn(0.5f, 2.0f)).apply()

    // ──── Subtitle Settings ────

    var exoSubtitleFontSize: Int
        get() = prefs.getInt(KEY_EXO_SUBTITLE_FONT_SIZE, prefs.getInt("subtitle_font_size", 18))
        set(value) = prefs.edit().putInt(KEY_EXO_SUBTITLE_FONT_SIZE, value.coerceIn(10, 48)).apply()

    var mpvSubtitleFontSize: Int
        get() = prefs.getInt(KEY_MPV_SUBTITLE_FONT_SIZE, prefs.getInt("subtitle_font_size", 18))
        set(value) = prefs.edit().putInt(KEY_MPV_SUBTITLE_FONT_SIZE, value.coerceIn(10, 48)).apply()

    var exoSubtitleColor: Long
        get() = prefs.getLong(KEY_EXO_SUBTITLE_COLOR, prefs.getLong("subtitle_color", 0xFFFFFFFF))
        set(value) = prefs.edit().putLong(KEY_EXO_SUBTITLE_COLOR, value).apply()

    var mpvSubtitleColor: Long
        get() = prefs.getLong(KEY_MPV_SUBTITLE_COLOR, prefs.getLong("subtitle_color", 0xFFFFFFFF))
        set(value) = prefs.edit().putLong(KEY_MPV_SUBTITLE_COLOR, value).apply()

    var exoSubtitleBgOpacity: Float
        get() = prefs.getFloat(KEY_EXO_SUBTITLE_BG_OPACITY, prefs.getFloat("subtitle_bg_opacity", 0.0f))
        set(value) = prefs.edit().putFloat(KEY_EXO_SUBTITLE_BG_OPACITY, value.coerceIn(0f, 1f)).apply()

    var mpvSubtitleBgOpacity: Float
        get() = prefs.getFloat(KEY_MPV_SUBTITLE_BG_OPACITY, prefs.getFloat("subtitle_bg_opacity", 0.0f))
        set(value) = prefs.edit().putFloat(KEY_MPV_SUBTITLE_BG_OPACITY, value.coerceIn(0f, 1f)).apply()

    var exoSubtitleEdgeType: String
        get() = prefs.getString(KEY_EXO_SUBTITLE_EDGE_TYPE, prefs.getString("subtitle_edge_type", "outline")) ?: "outline"
        set(value) = prefs.edit().putString(KEY_EXO_SUBTITLE_EDGE_TYPE, value).apply()

    var mpvSubtitleEdgeType: String
        get() = prefs.getString(KEY_MPV_SUBTITLE_EDGE_TYPE, prefs.getString("subtitle_edge_type", "outline")) ?: "outline"
        set(value) = prefs.edit().putString(KEY_MPV_SUBTITLE_EDGE_TYPE, value).apply()

    var exoSubtitleEdgeSize: Int
        get() = prefs.getInt(KEY_EXO_SUBTITLE_EDGE_SIZE, prefs.getInt("subtitle_edge_size", 0))
        set(value) = prefs.edit().putInt(KEY_EXO_SUBTITLE_EDGE_SIZE, value.coerceIn(0, 20)).apply()

    var mpvSubtitleEdgeSize: Int
        get() = prefs.getInt(KEY_MPV_SUBTITLE_EDGE_SIZE, prefs.getInt("subtitle_edge_size", 0))
        set(value) = prefs.edit().putInt(KEY_MPV_SUBTITLE_EDGE_SIZE, value.coerceIn(0, 20)).apply()

    var exoSubtitleOutlineColor: Long
        get() = prefs.getLong(KEY_EXO_SUBTITLE_OUTLINE_COLOR, prefs.getLong("subtitle_outline_color", 0xFF000000))
        set(value) = prefs.edit().putLong(KEY_EXO_SUBTITLE_OUTLINE_COLOR, value).apply()

    var mpvSubtitleOutlineColor: Long
        get() = prefs.getLong(KEY_MPV_SUBTITLE_OUTLINE_COLOR, prefs.getLong("subtitle_outline_color", 0xFF000000))
        set(value) = prefs.edit().putLong(KEY_MPV_SUBTITLE_OUTLINE_COLOR, value).apply()

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
        get() = prefs.getInt(KEY_EXO_SUBTITLE_POSITION, prefs.getInt("subtitle_position", 90))
        set(value) = prefs.edit().putInt(KEY_EXO_SUBTITLE_POSITION, value.coerceIn(0, 100)).apply()

    var mpvSubtitlePosition: Int
        get() = prefs.getInt(KEY_MPV_SUBTITLE_POSITION, prefs.getInt("subtitle_position", 90))
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
        get() = prefs.getFloat(KEY_SUBTITLE_SCALE, 1.0f)
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
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_AT, value).apply()

    var dismissedUpdateTag: String
        get() = prefs.getString(KEY_DISMISSED_UPDATE_TAG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DISMISSED_UPDATE_TAG, value).apply()

    var catalogSettingsLastChanged: Long
        get() = prefs.getLong(KEY_CATALOG_SETTINGS_LAST_CHANGED, 0L)
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
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is org.json.JSONArray -> {
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
        // Drive
        private const val KEY_SELECTED_DRIVE = "selected_drive_id"

        // Player
        private const val KEY_ENGINE = "player_engine"
        private const val KEY_EXO_DECODER = "exo_decoder"
        private const val KEY_MPV_DECODER = "mpv_decoder"
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
