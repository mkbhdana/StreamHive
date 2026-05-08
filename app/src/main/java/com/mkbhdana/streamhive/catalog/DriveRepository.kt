package com.mkbhdana.streamhive.catalog

import com.mkbhdana.streamhive.auth.AuthRepository
import com.mkbhdana.streamhive.data.db.MediaFileDao
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.model.DriveFile
import com.mkbhdana.streamhive.data.model.DriveFileListResponse
import com.mkbhdana.streamhive.data.model.SharedDrive
import com.mkbhdana.streamhive.data.model.SharedDriveListResponse
import com.mkbhdana.streamhive.util.Constants
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authRepository: AuthRepository,
    private val mediaFileDao: MediaFileDao
) {
    private val gson = Gson()

    suspend fun listSharedDrives(): Result<List<SharedDrive>> = withContext(Dispatchers.IO) {
        try {
            val token = authRepository.getValidAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val allDrives = mutableListOf<SharedDrive>()
            val authState = authRepository.authState.value
            if (authState is com.mkbhdana.streamhive.data.model.AuthState.Authenticated && 
                authState.credentials is com.mkbhdana.streamhive.data.model.AuthCredentials.OAuth2Credentials) {
                allDrives.add(SharedDrive("system_root", "Root"))
            }
            var pageToken: String? = null

            do {
                val urlBuilder = StringBuilder("${Constants.DRIVE_DRIVES_URL}?pageSize=100")
                if (pageToken != null) {
                    urlBuilder.append("&pageToken=$pageToken")
                }

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        return@withContext Result.failure(
                            Exception("Failed to list drives: ${response.code} - $errorBody")
                        )
                    }

                    val body = response.body ?: return@withContext Result.failure(
                        Exception("Failed to list drives: empty body")
                    )

                    val driveListResponse = gson.fromJson(body.charStream(), SharedDriveListResponse::class.java)
                    allDrives.addAll(driveListResponse.drives)
                    pageToken = driveListResponse.nextPageToken
                }
            } while (pageToken != null)

            Result.success(allDrives)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFilesInDrive(
        driveId: String,
        folderId: String? = null,
        forceRefresh: Boolean = false
    ): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        try {
            val token = authRepository.getValidAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val allFiles = mutableListOf<DriveFile>()
            var pageToken: String? = null

            // Intercept system_root for virtual folders
            if (driveId == "system_root" && (folderId == null || folderId == "system_root")) {
                val rootFolders = listOf(
                    DriveFile(id = "my_drive", name = "My Drive", mimeType = "application/vnd.google-apps.folder"),
                    DriveFile(id = "shared_with_me", name = "Shared with me", mimeType = "application/vnd.google-apps.folder"),
                    DriveFile(id = "starred", name = "Starred", mimeType = "application/vnd.google-apps.folder"),
                    DriveFile(id = "recent", name = "Recent", mimeType = "application/vnd.google-apps.folder"),
                    DriveFile(id = "trashed", name = "Trashed", mimeType = "application/vnd.google-apps.folder")
                )
                
                val entities = rootFolders.map { file ->
                    MediaFileEntity(
                        id = file.id, name = file.name, mimeType = file.mimeType, size = file.size,
                        thumbnailLink = file.thumbnailLink, modifiedTime = file.modifiedTime, createdTime = file.createdTime,
                        parentId = "system_root", driveId = "system_root",
                        fileExtension = file.fileExtension, isFolder = file.isFolder
                    )
                }
                mediaFileDao.deleteByFolder("system_root", "system_root")
                mediaFileDao.insertFiles(entities)
                return@withContext Result.success(rootFolders)
            }

            val queryBuilder = StringBuilder()
            var corpora = "drive"
            var orderBy = "folder,name"

            if (driveId == "system_root") {
                corpora = "allDrives"
                when (folderId) {
                    "my_drive" -> { corpora = "user"; queryBuilder.append("'root' in parents and trashed = false") }
                    "shared_with_me" -> queryBuilder.append("sharedWithMe = true and trashed = false")
                    "starred" -> queryBuilder.append("starred = true and trashed = false")
                    "recent" -> { queryBuilder.append("trashed = false"); orderBy = "viewedByMeTime desc" }
                    "trashed" -> queryBuilder.append("trashed = true")
                    else -> queryBuilder.append("'$folderId' in parents and trashed = false")
                }
            } else {
                val parentQuery = if (folderId != null) "'$folderId' in parents" else "'$driveId' in parents"
                queryBuilder.append("$parentQuery and trashed = false")
            }
            
            // Only append the video mime type query if it's not the recent/trashed root (as those return files naturally, but we still want folders to be visible)
            // Wait, we always want folders to be visible, so we append the video mime types query to everything
            queryBuilder.append(" and ${Constants.VIDEO_MIME_TYPES_QUERY}")
            
            val query = queryBuilder.toString()

            do {
                val urlBuilder = StringBuilder("${Constants.DRIVE_FILES_URL}?")
                urlBuilder.append("corpora=$corpora")
                if (corpora == "drive") {
                    urlBuilder.append("&driveId=$driveId")
                }
                urlBuilder.append("&includeItemsFromAllDrives=true")
                urlBuilder.append("&supportsAllDrives=true")
                urlBuilder.append("&q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                urlBuilder.append("&fields=${java.net.URLEncoder.encode(Constants.DRIVE_LIST_FIELDS, "UTF-8")}")
                urlBuilder.append("&pageSize=100")
                urlBuilder.append("&orderBy=${java.net.URLEncoder.encode(orderBy, "UTF-8")}")
                if (pageToken != null) {
                    urlBuilder.append("&pageToken=$pageToken")
                }

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        return@withContext Result.failure(
                            Exception("Failed to list files: ${response.code} - $errorBody")
                        )
                    }

                    val body = response.body ?: return@withContext Result.failure(
                        Exception("Failed to list files: empty body")
                    )

                    val fileListResponse = gson.fromJson(body.charStream(), DriveFileListResponse::class.java)
                    allFiles.addAll(fileListResponse.files)
                    pageToken = fileListResponse.nextPageToken
                }
            } while (pageToken != null)

            // Cache to database
            val entities = allFiles.map { file ->
                MediaFileEntity(
                    id = file.id,
                    name = file.name,
                    mimeType = file.mimeType,
                    size = file.size,
                    thumbnailLink = file.thumbnailLink,
                    modifiedTime = file.modifiedTime,
                    createdTime = file.createdTime,
                    parentId = file.parents?.firstOrNull() ?: folderId ?: driveId,
                    driveId = driveId,
                    fileExtension = file.fileExtension,
                    isFolder = file.isFolder,
                    videoWidth = file.videoMediaMetadata?.width,
                    videoHeight = file.videoMediaMetadata?.height,
                    videoDurationMs = file.videoMediaMetadata?.durationMillis
                )
            }
            mediaFileDao.deleteByFolder(driveId, folderId ?: driveId)
            mediaFileDao.insertFiles(entities)

            Result.success(allFiles)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    fun getCachedFiles(driveId: String, parentId: String?): Flow<List<MediaFileEntity>> {
        return mediaFileDao.getFilesByFolder(driveId, parentId ?: driveId)
    }

    /**
     * Fetch only .txt files from a folder via Drive API.
     * Used to detect metadata hint files (e.g. "tt1234567.txt").
     * Lightweight call — no caching, no pagination (expect very few txt files).
     */
    suspend fun listTextFilesInFolder(
        driveId: String,
        folderId: String
    ): List<DriveFile> = withContext(Dispatchers.IO) {
        try {
            val token = authRepository.getValidAccessToken() ?: return@withContext emptyList()

            val query = "'$folderId' in parents and mimeType = 'text/plain' and trashed = false"
            val url = StringBuilder("${Constants.DRIVE_FILES_URL}?")
                .append("corpora=drive")
                .append("&driveId=$driveId")
                .append("&includeItemsFromAllDrives=true")
                .append("&supportsAllDrives=true")
                .append("&q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                .append("&fields=${java.net.URLEncoder.encode("files(id,name,mimeType)", "UTF-8")}")
                .append("&pageSize=10")
                .toString()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body ?: return@withContext emptyList()

                val fileListResponse = gson.fromJson(body.charStream(), DriveFileListResponse::class.java)
                fileListResponse.files
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()
        }
    }

    /**
     * Get the modifiedTime of a specific folder from the Drive API.
     */
    suspend fun getFolderModifiedTime(fileId: String, driveId: String): String? = withContext(Dispatchers.IO) {
        try {
            val token = authRepository.getValidAccessToken() ?: return@withContext null
            val url = StringBuilder("${Constants.DRIVE_FILES_URL}/$fileId?")
                .append("supportsAllDrives=true")
                .append("&fields=modifiedTime")
                .toString()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null

                // Use Gson to parse the modifiedTime as a stream
                val jsonObject = com.google.gson.JsonParser.parseReader(body.charStream()).asJsonObject
                if (jsonObject.has("modifiedTime")) {
                    jsonObject.get("modifiedTime").asString
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    suspend fun getFileById(fileId: String): MediaFileEntity? {
        return mediaFileDao.getFileById(fileId)
    }

    suspend fun getFileByIdViaApi(fileId: String): Result<DriveFile> = withContext(Dispatchers.IO) {
        try {
            val token = authRepository.getValidAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val url = StringBuilder("${Constants.DRIVE_FILES_URL}/$fileId?")
                .append("supportsAllDrives=true")
                .append("&fields=${java.net.URLEncoder.encode(Constants.DRIVE_FILE_FIELDS.removePrefix("files(").removeSuffix(")"), "UTF-8")}")
                .toString()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to fetch file: ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
                val file = gson.fromJson(body.charStream(), DriveFile::class.java)
                Result.success(file)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }


    fun searchFiles(query: String): Flow<List<MediaFileEntity>> {
        return mediaFileDao.searchFiles(query)
    }

    fun searchAllFiles(query: String): Flow<List<MediaFileEntity>> {
        return mediaFileDao.searchAllFiles(query)
    }

    fun searchFilesInDrive(query: String, driveId: String): Flow<List<MediaFileEntity>> {
        return mediaFileDao.searchFilesInDrive(query, driveId)
    }

    /**
     * Search files via Drive API directly (not cache).
     * Returns fresh results from Google Drive.
     */
    suspend fun searchFilesViaApi(query: String, driveId: String? = null): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        try {
            val token = authRepository.getValidAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val allFiles = mutableListOf<DriveFile>()
            var pageToken: String? = null

            // Build search query
            val searchQuery = StringBuilder()
            searchQuery.append("(name contains '${query.replace("'", "\\'")}')")
            searchQuery.append(" and ${Constants.VIDEO_MIME_TYPES_QUERY}")
            searchQuery.append(" and trashed = false")

            val corpora = if (driveId == null) "allDrives" else "drive"
            val orderBy = "folder,name"

            do {
                val urlBuilder = StringBuilder("${Constants.DRIVE_FILES_URL}?")
                urlBuilder.append("corpora=$corpora")
                if (corpora == "drive" && driveId != null) {
                    urlBuilder.append("&driveId=$driveId")
                }
                urlBuilder.append("&includeItemsFromAllDrives=true")
                urlBuilder.append("&supportsAllDrives=true")
                urlBuilder.append("&q=${java.net.URLEncoder.encode(searchQuery.toString(), "UTF-8")}")
                urlBuilder.append("&fields=${java.net.URLEncoder.encode(Constants.DRIVE_LIST_FIELDS, "UTF-8")}")
                urlBuilder.append("&pageSize=100")
                urlBuilder.append("&orderBy=${java.net.URLEncoder.encode(orderBy, "UTF-8")}")
                if (pageToken != null) {
                    urlBuilder.append("&pageToken=$pageToken")
                }

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        return@withContext Result.failure(
                            Exception("Search failed: ${response.code} - $errorBody")
                        )
                    }

                    val body = response.body ?: return@withContext Result.failure(
                        Exception("Search failed: empty body")
                    )

                    val fileListResponse = gson.fromJson(body.charStream(), DriveFileListResponse::class.java)
                    allFiles.addAll(fileListResponse.files)
                    pageToken = fileListResponse.nextPageToken
                }
            } while (pageToken != null)

            Result.success(allFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAllFolders(): Flow<List<MediaFileEntity>> {
        return mediaFileDao.getAllFolders()
    }

    fun getStreamUrl(fileId: String): String {
        return "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
    }

    suspend fun getValidToken(): String? {
        return authRepository.getValidAccessToken()
    }

    // ──── Additional Drive Sections (all read-only) ────

    suspend fun listMyDriveFiles(folderId: String? = null): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        try {
            val token = authRepository.getValidAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val parentQuery = if (folderId != null) "'$folderId' in parents" else "'root' in parents"
            val query = "$parentQuery and trashed = false"

            val allFiles = mutableListOf<DriveFile>()
            var pageToken: String? = null

            do {
                val urlBuilder = StringBuilder("${Constants.DRIVE_FILES_URL}?")
                urlBuilder.append("corpora=user")
                urlBuilder.append("&q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                urlBuilder.append("&fields=${java.net.URLEncoder.encode(Constants.DRIVE_LIST_FIELDS, "UTF-8")}")
                urlBuilder.append("&pageSize=100")
                urlBuilder.append("&orderBy=folder,name")
                if (pageToken != null) urlBuilder.append("&pageToken=$pageToken")

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .header("Authorization", "Bearer $token")
                    .get().build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Failed: ${response.code}"))
                    }

                    val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
                    val fileListResponse = gson.fromJson(body.charStream(), DriveFileListResponse::class.java)
                    allFiles.addAll(fileListResponse.files)
                    pageToken = fileListResponse.nextPageToken
                }
            } while (pageToken != null)

            Result.success(allFiles)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun listSharedWithMe(): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        executeSimpleQuery("sharedWithMe = true and trashed = false")
    }

    suspend fun listStarredFiles(): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        executeSimpleQuery("starred = true and trashed = false")
    }

    suspend fun listRecentFiles(): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        try {
            val token = authRepository.getValidAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            val urlBuilder = StringBuilder("${Constants.DRIVE_FILES_URL}?")
            urlBuilder.append("corpora=allDrives")
            urlBuilder.append("&includeItemsFromAllDrives=true")
            urlBuilder.append("&supportsAllDrives=true")
            urlBuilder.append("&q=${java.net.URLEncoder.encode("trashed = false", "UTF-8")}")
            urlBuilder.append("&fields=${java.net.URLEncoder.encode(Constants.DRIVE_LIST_FIELDS, "UTF-8")}")
            urlBuilder.append("&pageSize=50")
            urlBuilder.append("&orderBy=${java.net.URLEncoder.encode("viewedByMeTime desc", "UTF-8")}")

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("Authorization", "Bearer $token")
                .get().build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed: ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty body"))
                val fileListResponse = gson.fromJson(body.charStream(), DriveFileListResponse::class.java)
                Result.success(fileListResponse.files)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun listTrashedFiles(): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        executeSimpleQuery("trashed = true")
    }

    private suspend fun executeSimpleQuery(query: String): Result<List<DriveFile>> {
        val token = authRepository.getValidAccessToken()
            ?: return Result.failure(Exception("Not authenticated"))

        val allFiles = mutableListOf<DriveFile>()
        var pageToken: String? = null

        do {
            val urlBuilder = StringBuilder("${Constants.DRIVE_FILES_URL}?")
            urlBuilder.append("corpora=allDrives")
            urlBuilder.append("&includeItemsFromAllDrives=true")
            urlBuilder.append("&supportsAllDrives=true")
            urlBuilder.append("&q=${java.net.URLEncoder.encode(query, "UTF-8")}")
            urlBuilder.append("&fields=${java.net.URLEncoder.encode(Constants.DRIVE_LIST_FIELDS, "UTF-8")}")
            urlBuilder.append("&pageSize=100")
            urlBuilder.append("&orderBy=folder,name")
            if (pageToken != null) urlBuilder.append("&pageToken=$pageToken")

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("Authorization", "Bearer $token")
                .get().build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Failed: ${response.code}"))
                }

                val body = response.body ?: return Result.failure(Exception("Empty body"))
                val fileListResponse = gson.fromJson(body.charStream(), DriveFileListResponse::class.java)
                allFiles.addAll(fileListResponse.files)
                pageToken = fileListResponse.nextPageToken
            }
        } while (pageToken != null)

        return Result.success(allFiles)
    }
}
