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

    var defaultDecoder: String
        get() = prefs.getString(KEY_DECODER, "hw+") ?: "hw+"
        set(value) = prefs.edit().putString(KEY_DECODER, value).apply()

    var defaultResizeMode: String
        get() = prefs.getString(KEY_RESIZE_MODE, "fit") ?: "fit"
        set(value) = prefs.edit().putString(KEY_RESIZE_MODE, value).apply()

    var keepServerRunning: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SERVER_RUNNING, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SERVER_RUNNING, value).apply()

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

    var gestureZoomEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_ZOOM, true)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_ZOOM, value).apply()

    /** Sensitivity multiplier: 0.5f (low) to 2.0f (high), default 1.0f */
    var gestureSensitivity: Float
        get() = prefs.getFloat(KEY_GESTURE_SENSITIVITY, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_GESTURE_SENSITIVITY, value.coerceIn(0.5f, 2.0f)).apply()

    // ──── Subtitle Settings ────

    var subtitleLanguage: String
        get() = prefs.getString(KEY_SUBTITLE_LANGUAGE, "eng") ?: "eng"
        set(value) = prefs.edit().putString(KEY_SUBTITLE_LANGUAGE, value).apply()

    var subtitleFontSize: Int
        get() = prefs.getInt(KEY_SUBTITLE_FONT_SIZE, 18)
        set(value) = prefs.edit().putInt(KEY_SUBTITLE_FONT_SIZE, value.coerceIn(10, 48)).apply()

    var subtitleColor: Long
        get() = prefs.getLong(KEY_SUBTITLE_COLOR, 0xFFFFFFFF)
        set(value) = prefs.edit().putLong(KEY_SUBTITLE_COLOR, value).apply()

    var subtitleBgOpacity: Float
        get() = prefs.getFloat(KEY_SUBTITLE_BG_OPACITY, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_SUBTITLE_BG_OPACITY, value.coerceIn(0f, 1f)).apply()

    var subtitlePosition: Int
        get() = prefs.getInt(KEY_SUBTITLE_POSITION, 90)
        set(value) = prefs.edit().putInt(KEY_SUBTITLE_POSITION, value.coerceIn(0, 100)).apply()

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

    var tmdbAnimeFolders: Set<String>
        get() = HashSet(prefs.getStringSet(KEY_TMDB_ANIME_FOLDERS, emptySet()) ?: emptySet())
        set(value) = prefs.edit().putStringSet(KEY_TMDB_ANIME_FOLDERS, HashSet(value)).apply()

    // ──── Helpers ────

    fun isMpvAvailable(): Boolean {
        return try {
            Class.forName("is.xyz.mpv.MPV")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
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
        private const val KEY_DECODER = "default_decoder"
        private const val KEY_RESIZE_MODE = "default_resize_mode"
        private const val KEY_KEEP_SERVER_RUNNING = "keep_server_running"

        // Gestures
        private const val KEY_GESTURE_VOLUME = "gesture_volume_enabled"
        private const val KEY_GESTURE_BRIGHTNESS = "gesture_brightness_enabled"
        private const val KEY_GESTURE_SEEK = "gesture_seek_enabled"
        private const val KEY_GESTURE_DOUBLE_TAP = "gesture_double_tap_enabled"
        private const val KEY_GESTURE_ZOOM = "gesture_zoom_enabled"
        private const val KEY_GESTURE_SENSITIVITY = "gesture_sensitivity"

        // Subtitles
        private const val KEY_SUBTITLE_LANGUAGE = "subtitle_language"
        private const val KEY_SUBTITLE_FONT_SIZE = "subtitle_font_size"
        private const val KEY_SUBTITLE_COLOR = "subtitle_color"
        private const val KEY_SUBTITLE_BG_OPACITY = "subtitle_bg_opacity"
        private const val KEY_SUBTITLE_POSITION = "subtitle_position"

        // TMDB
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_TMDB_MOVIE_FOLDERS = "tmdb_movie_folders"
        private const val KEY_TMDB_TV_FOLDERS = "tmdb_tv_folders"
        private const val KEY_TMDB_ANIME_FOLDERS = "tmdb_anime_folders"
    }
}
