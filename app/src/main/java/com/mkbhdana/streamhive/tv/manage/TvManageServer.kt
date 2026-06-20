package com.mkbhdana.streamhive.tv.manage

import android.util.Log
import com.mkbhdana.streamhive.catalog.DriveRepository
import com.mkbhdana.streamhive.data.db.MediaFileDao
import com.mkbhdana.streamhive.data.db.PlaybackHistoryDao
import com.mkbhdana.streamhive.data.db.TmdbMetadataDao
import com.mkbhdana.streamhive.settings.AppPreferences
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local web server (scanned via QR) for managing the catalog from a phone:
 * TMDB API key, catalog folders (browse drives + add/remove), and a full
 * settings backup import/export (matching mobile, minus gestures — see
 * [ManageBackup]). Changes flow into the app immediately via the settings
 * timestamp the catalog observes.
 */
class TvManageServer(
    private val prefs: AppPreferences,
    private val driveRepository: DriveRepository,
    private val mediaFileDao: MediaFileDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val tmdbMetadataDao: TmdbMetadataDao,
    port: Int
) : NanoHTTPD("0.0.0.0", port) {

    init {
        java.util.logging.Logger.getLogger(NanoHTTPD::class.java.name).level = java.util.logging.Level.OFF
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/" ->
                    newFixedLengthResponse(Response.Status.OK, "text/html", ManageWebAssets.PAGE)
                session.method == Method.GET && session.uri == "/state" -> json(stateJson())
                session.method == Method.GET && session.uri == "/export" -> exportResponse()
                session.method == Method.POST && session.uri == "/tmdb-key" -> handleKey(session)
                session.method == Method.POST && session.uri == "/folder/remove" -> handleRemove(session)
                session.method == Method.POST && session.uri == "/folder/order" -> handleOrder(session)
                session.method == Method.POST && session.uri == "/folder/add" -> handleAdd(session)
                session.method == Method.POST && session.uri == "/browse" -> handleBrowse(session)
                session.method == Method.POST && session.uri == "/import" -> handleImport(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "serve error", e)
            json(JSONObject().put("ok", false).put("message", e.message ?: "Server error"))
        }
    }

    private fun stateJson(): JSONObject {
        val names = runBlocking { runCatching { mediaFileDao.getAllFolders().first() }.getOrDefault(emptyList()) }
            .associate { it.id to it.name }
        val ordered = (prefs.tmdbFolderOrder + prefs.tmdbMovieFolders + prefs.tmdbTvFolders).distinct()
        val folders = JSONArray()
        ordered.forEach { id ->
            val type = if (id in prefs.tmdbMovieFolders) "movie" else if (id in prefs.tmdbTvFolders) "tv" else return@forEach
            folders.put(JSONObject().put("id", id).put("name", names[id] ?: id).put("type", type))
        }
        val drives = JSONArray()
        runBlocking { driveRepository.listSharedDrives().getOrNull() }.orEmpty().forEach {
            drives.put(JSONObject().put("id", it.id).put("name", it.name))
        }
        return JSONObject()
            .put("tmdbKey", prefs.tmdbApiKey)
            .put("folders", folders)
            .put("drives", drives)
    }

    private fun handleKey(session: IHTTPSession): Response {
        prefs.tmdbApiKey = readJson(session).optString("key").trim()
        touch()
        return json(JSONObject().put("ok", true))
    }

    private fun handleRemove(session: IHTTPSession): Response {
        val id = readJson(session).optString("id")
        prefs.tmdbMovieFolders = prefs.tmdbMovieFolders - id
        prefs.tmdbTvFolders = prefs.tmdbTvFolders - id
        prefs.tmdbFolderOrder = prefs.tmdbFolderOrder - id
        touch()
        return json(JSONObject().put("ok", true))
    }

    private fun handleOrder(session: IHTTPSession): Response {
        val arr = readJson(session).optJSONArray("ids")
        if (arr != null) {
            prefs.tmdbFolderOrder = (0 until arr.length()).map { arr.getString(it) }
            touch()
        }
        return json(JSONObject().put("ok", true))
    }

    private fun handleAdd(session: IHTTPSession): Response {
        val body = readJson(session)
        val id = body.optString("folderId")
        val type = body.optString("type").ifBlank { "movie" }
        if (id.isBlank()) return json(JSONObject().put("ok", false).put("message", "Missing folder"))
        prefs.tmdbMovieFolders = prefs.tmdbMovieFolders - id
        prefs.tmdbTvFolders = prefs.tmdbTvFolders - id
        if (type == "tv") prefs.tmdbTvFolders = prefs.tmdbTvFolders + id
        else prefs.tmdbMovieFolders = prefs.tmdbMovieFolders + id
        val order = prefs.tmdbFolderOrder.toMutableList()
        if (id !in order) order.add(id)
        prefs.tmdbFolderOrder = order
        touch()
        return json(JSONObject().put("ok", true))
    }

    private fun handleBrowse(session: IHTTPSession): Response {
        val body = readJson(session)
        val driveId = body.optString("driveId")
        val folderId = body.optString("folderId").ifBlank { driveId }
        val folders = JSONArray()
        runBlocking {
            runCatching { driveRepository.listFilesInDrive(driveId, folderId) }
            val cached = runCatching { driveRepository.getCachedFiles(driveId, folderId).first() }.getOrDefault(emptyList())
            cached.filter { it.isFolder }.forEach { folders.put(JSONObject().put("id", it.id).put("name", it.name)) }
        }
        return json(JSONObject().put("driveId", driveId).put("folderId", folderId).put("folders", folders))
    }

    private fun exportResponse(): Response {
        val data = runBlocking {
            ManageBackup.exportJson(prefs, playbackHistoryDao, mediaFileDao, tmdbMetadataDao)
        }
        val res = newFixedLengthResponse(Response.Status.OK, "application/json", data)
        res.addHeader("Content-Disposition", "attachment; filename=\"streamhive-backup.json\"")
        return res
    }

    private fun handleImport(session: IHTTPSession): Response {
        val payload = readJson(session).optString("json")
        val ok = runBlocking {
            ManageBackup.importJson(prefs, playbackHistoryDao, mediaFileDao, tmdbMetadataDao, driveRepository, payload)
        }
        return json(JSONObject().put("ok", ok))
    }

    private fun touch() {
        prefs.catalogSettingsLastChanged = System.currentTimeMillis()
    }

    private fun readJson(session: IHTTPSession): JSONObject {
        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            JSONObject((files["postData"] ?: "{}").ifBlank { "{}" })
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun json(obj: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", obj.toString())

    companion object {
        private const val TAG = "TvManageServer"
    }
}
