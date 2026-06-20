package com.mkbhdana.streamhive.tv.manage

import com.mkbhdana.streamhive.catalog.DriveRepository
import com.mkbhdana.streamhive.data.db.MediaFileDao
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.PlaybackHistoryDao
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataDao
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity
import com.mkbhdana.streamhive.settings.AppPreferences
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings backup import/export, identical in format to the mobile app's
 * (settings + playback history + folder names + TMDB metadata) — but with the
 * gesture settings stripped out, since the TV build has no gestures. Kept as a
 * string-based copy of the mobile logic so the mobile screens stay untouched.
 */
object ManageBackup {

    private val GESTURE_KEYS = listOf(
        "gesture_volume_enabled", "gesture_brightness_enabled", "gesture_seek_enabled",
        "gesture_double_tap_enabled", "tap_seek_duration", "gesture_zoom_enabled",
        "gesture_speed_press_enabled", "gesture_lock_enabled", "haptic_feedback_enabled",
        "gesture_sensitivity"
    )

    suspend fun exportJson(
        prefs: AppPreferences,
        playbackHistoryDao: PlaybackHistoryDao,
        mediaFileDao: MediaFileDao,
        tmdbMetadataDao: TmdbMetadataDao,
        includeApiKey: Boolean = true,
        includeMetadata: Boolean = true
    ): String {
        val root = JSONObject(prefs.exportToJson())
        root.put("streamhive_settings_version", 1)
        if (!includeApiKey) root.remove("tmdb_api_key")
        GESTURE_KEYS.forEach { root.remove(it) }

        val history = playbackHistoryDao.getAll()
        if (history.isNotEmpty()) {
            val arr = JSONArray()
            history.forEach { h ->
                arr.put(
                    JSONObject()
                        .put("fileId", h.fileId).put("fileName", h.fileName).put("driveId", h.driveId)
                        .put("lastPosition", h.lastPosition).put("duration", h.duration).put("lastPlayedAt", h.lastPlayedAt)
                        .put("posterPath", h.posterPath ?: "").put("thumbnailUrl", h.thumbnailUrl ?: "")
                        .put("lastPlayerEngine", h.lastPlayerEngine ?: "").put("lastDecoderMode", h.lastDecoderMode ?: "")
                )
            }
            root.put("playback_history", arr)
        }

        val allMappedFolders = prefs.tmdbMovieFolders + prefs.tmdbTvFolders
        if (allMappedFolders.isNotEmpty()) {
            val arr = JSONArray()
            val allFolders = mediaFileDao.getAllFolders().first()
            allMappedFolders.forEach { id ->
                val folder = allFolders.find { it.id == id }
                arr.put(
                    JSONObject()
                        .put("id", id)
                        .put("name", folder?.name ?: id)
                        .put("mimeType", folder?.mimeType ?: "application/vnd.google-apps.folder")
                        .put("parentId", folder?.parentId ?: "")
                        .put("driveId", folder?.driveId ?: "")
                        .put("modifiedTime", folder?.modifiedTime ?: "")
                        .put("createdTime", folder?.createdTime ?: "")
                )
            }
            root.put("tmdb_folder_names", arr)
        }

        if (includeMetadata) {
            val allMeta = tmdbMetadataDao.getByMediaType("movie") + tmdbMetadataDao.getByMediaType("tv")
            if (allMeta.isNotEmpty()) {
                val arr = JSONArray()
                allMeta.forEach { m ->
                    arr.put(
                        JSONObject()
                            .put("driveFileId", m.driveFileId).put("tmdbId", m.tmdbId).put("title", m.title)
                            .put("overview", m.overview ?: "").put("posterPath", m.posterPath ?: "")
                            .put("backdropPath", m.backdropPath ?: "").put("rating", m.rating?.toDouble() ?: 0.0)
                            .put("year", m.year ?: "").put("originalLanguage", m.originalLanguage ?: "")
                            .put("mediaType", m.mediaType).put("cachedAt", m.cachedAt)
                    )
                }
                root.put("tmdb_metadata", arr)
            }
        }
        return root.toString(2)
    }

    suspend fun importJson(
        prefs: AppPreferences,
        playbackHistoryDao: PlaybackHistoryDao,
        mediaFileDao: MediaFileDao,
        tmdbMetadataDao: TmdbMetadataDao,
        driveRepository: DriveRepository,
        json: String
    ): Boolean {
        return try {
            val obj = JSONObject(json)

            val hasKnownSettingsKey = listOf(
                "tmdb_api_key", "tmdb_movie_folders", "tmdb_tv_folders", "player_engine", "selected_drive_id"
            ).any { obj.has(it) }
            if (!obj.has("streamhive_settings_version") && !hasKnownSettingsKey) return false

            if (obj.has("tmdb_folder_names")) {
                val arr = obj.getJSONArray("tmdb_folder_names")
                val imported = mutableListOf<MediaFileEntity>()
                val fallbackDriveId = obj.optString("selected_drive_id").takeIf { it.isNotBlank() }
                    ?: prefs.selectedDriveId.takeIf { it.isNotBlank() }
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val folderId = o.getString("id")
                    val apiFolder = if (o.optString("driveId").isBlank()) {
                        driveRepository.getFileByIdViaApi(folderId).getOrNull()
                    } else null
                    val driveId = o.optString("driveId").takeIf { it.isNotBlank() }
                        ?: apiFolder?.driveId?.takeIf { it.isNotBlank() }
                        ?: fallbackDriveId
                        ?: ""
                    imported.add(
                        MediaFileEntity(
                            id = folderId,
                            name = o.optString("name").ifBlank { apiFolder?.name ?: folderId },
                            mimeType = o.optString("mimeType").ifBlank { apiFolder?.mimeType ?: "application/vnd.google-apps.folder" },
                            size = 0,
                            isFolder = true,
                            parentId = o.optString("parentId").ifBlank { apiFolder?.parents?.firstOrNull() ?: driveId },
                            driveId = driveId,
                            modifiedTime = o.optString("modifiedTime").ifBlank { apiFolder?.modifiedTime ?: "" },
                            createdTime = o.optString("createdTime").ifBlank { apiFolder?.createdTime ?: "" }
                        )
                    )
                }
                if (imported.isNotEmpty()) mediaFileDao.insertFiles(imported)
                obj.remove("tmdb_folder_names")
            }

            if (obj.has("playback_history")) {
                val arr = obj.getJSONArray("playback_history")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    playbackHistoryDao.upsert(
                        PlaybackHistoryEntity(
                            fileId = o.getString("fileId"),
                            fileName = o.getString("fileName"),
                            driveId = o.getString("driveId"),
                            lastPosition = o.getLong("lastPosition"),
                            duration = o.getLong("duration"),
                            lastPlayedAt = o.getLong("lastPlayedAt"),
                            posterPath = o.optString("posterPath").ifBlank { null },
                            thumbnailUrl = o.optString("thumbnailUrl").ifBlank { null },
                            lastPlayerEngine = o.optString("lastPlayerEngine").ifBlank { null },
                            lastDecoderMode = o.optString("lastDecoderMode").ifBlank { null }
                        )
                    )
                }
                obj.remove("playback_history")
            }

            if (obj.has("tmdb_metadata")) {
                val arr = obj.getJSONArray("tmdb_metadata")
                val entities = mutableListOf<TmdbMetadataEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    entities.add(
                        TmdbMetadataEntity(
                            driveFileId = o.getString("driveFileId"),
                            tmdbId = o.getInt("tmdbId"),
                            title = o.getString("title"),
                            overview = o.optString("overview").ifBlank { null },
                            posterPath = o.optString("posterPath").ifBlank { null },
                            backdropPath = o.optString("backdropPath").ifBlank { null },
                            rating = o.optDouble("rating", 0.0).toFloat().takeIf { it > 0f },
                            year = o.optString("year").ifBlank { null },
                            originalLanguage = o.optString("originalLanguage").ifBlank { null },
                            mediaType = o.optString("mediaType", "movie"),
                            cachedAt = o.optLong("cachedAt", System.currentTimeMillis())
                        )
                    )
                }
                if (entities.isNotEmpty()) tmdbMetadataDao.insertAll(entities)
                obj.remove("tmdb_metadata")
            }

            obj.remove("streamhive_settings_version")
            GESTURE_KEYS.forEach { obj.remove(it) }

            val success = prefs.importFromJson(obj.toString())
            if (success) prefs.catalogSettingsLastChanged = System.currentTimeMillis()
            success
        } catch (e: Exception) {
            false
        }
    }
}
