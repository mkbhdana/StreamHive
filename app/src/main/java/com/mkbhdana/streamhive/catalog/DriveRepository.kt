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
                allDrives.add(SharedDrive("my_drive", "My Drive"))
                allDrives.add(SharedDrive("shared_with_me", "Shared with me"))
                allDrives.add(SharedDrive("starred", "Starred"))
                allDrives.add(SharedDrive("trashed", "Trashed"))
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

            val queryBuilder = StringBuilder()
            var corpora = "allDrives"
            var requestDriveId: String? = null
            var orderBy = "folder,name"

            val virtualDriveIds = setOf("my_drive", "shared_with_me", "starred","trashed")

            if (driveId in virtualDriveIds) {
                if (folderId == null) {
                    when (driveId) {
                        "my_drive" -> {
                            corpora = "user"
                            queryBuilder.append("'root' in parents and trashed = false")
                        }
                        "shared_with_me" -> queryBuilder.append("sharedWithMe = true and trashed = false")
                        "starred" -> queryBuilder.append("starred = true and trashed = false")
                        "trashed" -> queryBuilder.append("'root' in parents and trashed=true")
                    }
                } else {
                    queryBuilder.append("'$folderId' in parents and trashed = false")
                }
            } else {
                if (folderId == null) {
                    corpora = "drive"
                    requestDriveId = driveId
                }
                val parentQuery = if (folderId != null) "'$folderId' in parents" else "'$driveId' in parents"
                queryBuilder.append("$parentQuery and trashed = false")
            }

            queryBuilder.append(" and ${Constants.VIDEO_MIME_TYPES_QUERY}")
            
            val query = queryBuilder.toString()

            do {
                val urlBuilder = StringBuilder("${Constants.DRIVE_FILES_URL}?")
                urlBuilder.append("corpora=$corpora")
                requestDriveId?.let {
                    urlBuilder.append("&driveId=${java.net.URLEncoder.encode(it, "UTF-8")}")
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

            val displayFiles = allFiles
                .mapNotNull { it.toDisplayableDriveFile() }
                .distinctBy { it.id }
            val cacheParentId = folderId ?: driveId

            // Cache under the logical folder being displayed. Virtual roots like
            // Starred and Trash do not match the API's physical parent ids.
            val entities = displayFiles.map { file ->
                MediaFileEntity(
                    id = file.id,
                    name = file.name,
                    mimeType = file.mimeType,
                    size = file.size,
                    thumbnailLink = file.thumbnailLink,
                    modifiedTime = file.modifiedTime,
                    createdTime = file.createdTime,
                    parentId = cacheParentId,
                    driveId = driveId,
                    fileExtension = file.fileExtension,
                    isFolder = file.isFolder,
                    videoWidth = file.videoMediaMetadata?.width,
                    videoHeight = file.videoMediaMetadata?.height,
                    videoDurationMs = file.videoMediaMetadata?.durationMillis
                )
            }
            mediaFileDao.deleteByFolder(driveId, cacheParentId)
            mediaFileDao.insertFiles(entities)

            Result.success(displayFiles)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    fun getCachedFiles(driveId: String, parentId: String?): Flow<List<MediaFileEntity>> {
        return mediaFileDao.getFilesByFolder(driveId, parentId ?: driveId)
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
    suspend fun searchFilesViaApi(
        query: String,
        driveId: String? = null,
        folderId: String? = null
    ): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        try {
            val token = authRepository.getValidAccessToken()
                ?: return@withContext Result.failure(Exception("Not authenticated"))

            if (folderId != null) {
                return@withContext Result.success(
                    searchFilesInFolderTree(token, query, folderId, driveId)
                )
            }

            val allFiles = mutableListOf<DriveFile>()
            var pageToken: String? = null

            // Build search query
            val searchQuery = StringBuilder()
            val escapedQuery = query.replace("'", "\\'")
            searchQuery.append("(name contains '$escapedQuery')")
            searchQuery.append(" and ${Constants.VIDEO_MIME_TYPES_QUERY}")

            var corpora = "allDrives"
            var requestDriveId: String? = null
            var includeItemsFromAllDrives = true
            when (driveId) {
                null -> searchQuery.append(" and trashed = false")
                "my_drive" -> {
                    corpora = "user"
                    includeItemsFromAllDrives = false
                    searchQuery.append(" and 'me' in owners and trashed = false")
                }
                "shared_with_me" -> searchQuery.append(" and sharedWithMe = true and trashed = false")
                "starred" -> searchQuery.append(" and starred = true and trashed = false")
                "trashed" -> {
                    corpora = "user"
                    includeItemsFromAllDrives = false
                    searchQuery.append(" and trashed = true")
                }
                else -> {
                    corpora = "drive"
                    requestDriveId = driveId
                    searchQuery.append(" and trashed = false")
                }
            }
            val orderBy = "folder,name"

            do {
                val urlBuilder = StringBuilder("${Constants.DRIVE_FILES_URL}?")
                urlBuilder.append("corpora=$corpora")
                requestDriveId?.let {
                    urlBuilder.append("&driveId=${java.net.URLEncoder.encode(it, "UTF-8")}")
                }
                if (includeItemsFromAllDrives) {
                    urlBuilder.append("&includeItemsFromAllDrives=true")
                }
                urlBuilder.append("&supportsAllDrives=true")
                urlBuilder.append("&q=${java.net.URLEncoder.encode(searchQuery.toString(), "UTF-8")}")
                urlBuilder.append("&fields=${java.net.URLEncoder.encode(Constants.DRIVE_LIST_FIELDS, "UTF-8")}")
                urlBuilder.append("&pageSize=100")
                urlBuilder.append("&orderBy=${java.net.URLEncoder.encode(orderBy, "UTF-8")}")
                if (pageToken != null) {
                    urlBuilder.append("&pageToken=$pageToken")
                }

                val requestUrl = urlBuilder.toString()
                android.util.Log.d("SearchDebug", "searchFilesViaApi Request URL: $requestUrl")
                val request = Request.Builder()
                    .url(requestUrl)
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
                    android.util.Log.d("SearchDebug", "searchFilesViaApi Returned ${fileListResponse.files.size} files. Files: ${gson.toJson(fileListResponse.files)}")
                    allFiles.addAll(fileListResponse.files)
                    pageToken = fileListResponse.nextPageToken
                }
            } while (pageToken != null)

            val filteredFiles = allFiles.mapNotNull { it.toDisplayableDriveFile() }
                .distinctBy { it.id }

            Result.success(filteredFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun searchFilesInFolderTree(
        token: String,
        query: String,
        rootFolderId: String,
        driveId: String?
    ): List<DriveFile> {
        val results = mutableListOf<DriveFile>()
        val pendingFolders = ArrayDeque<String>()
        pendingFolders.add(rootFolderId)

        while (pendingFolders.isNotEmpty()) {
            val currentFolderId = pendingFolders.removeFirst()
            val children = listSearchFolderChildren(token, query, currentFolderId, driveId)
            children.forEach { file ->
                if (file.isFolder) {
                    pendingFolders.add(file.id)
                }
                if (file.name.contains(query, ignoreCase = true)) {
                    results.add(file)
                }
            }
        }

        return results.distinctBy { it.id }
    }

    private fun listSearchFolderChildren(token: String, query: String, folderId: String, driveId: String?): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        
        var corpora = "allDrives"
        var requestDriveId: String? = null
        var includeItemsFromAllDrives = true
        when (driveId) {
            null -> {}
            "my_drive" -> {
                corpora = "user"
                includeItemsFromAllDrives = false
            }
            "shared_with_me" -> corpora = "user"
            "starred" -> corpora = "user"
            "trashed" -> {
                corpora = "user"
                includeItemsFromAllDrives = false
            }
            else -> {
                corpora = "drive"
                requestDriveId = driveId
            }
        }
        
        val apiQuery = "'${folderId.escapeDriveQueryValue()}' in parents and trashed = false and ${Constants.VIDEO_MIME_TYPES_QUERY}"

        do {
            val urlBuilder = StringBuilder("${Constants.DRIVE_FILES_URL}?")
            urlBuilder.append("corpora=$corpora")
            requestDriveId?.let {
                urlBuilder.append("&driveId=${java.net.URLEncoder.encode(it, "UTF-8")}")
            }
            if (includeItemsFromAllDrives) {
                urlBuilder.append("&includeItemsFromAllDrives=true")
            }
            urlBuilder.append("&supportsAllDrives=true")
            urlBuilder.append("&q=${java.net.URLEncoder.encode(apiQuery, "UTF-8")}")
            urlBuilder.append("&fields=${java.net.URLEncoder.encode(Constants.DRIVE_LIST_FIELDS, "UTF-8")}")
            urlBuilder.append("&pageSize=100")
            urlBuilder.append("&orderBy=${java.net.URLEncoder.encode("folder,name", "UTF-8")}")
            if (pageToken != null) {
                urlBuilder.append("&pageToken=$pageToken")
            }

            val requestUrl = urlBuilder.toString()
            android.util.Log.d("SearchDebug", "listSearchFolderChildren Request URL: $requestUrl")
            val request = Request.Builder()
                .url(requestUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    throw IllegalStateException("Search failed: ${response.code} - $errorBody")
                }

                val body = response.body ?: throw IllegalStateException("Search failed: empty body")
                val fileListResponse = gson.fromJson(body.charStream(), DriveFileListResponse::class.java)
                files.addAll(fileListResponse.files.mapNotNull { it.toDisplayableDriveFile() })
                pageToken = fileListResponse.nextPageToken
            }
        } while (pageToken != null)

        return files.distinctBy { it.id }
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
                android.util.Log.d("SearchDebug", "executeSimpleQuery Returned ${fileListResponse.files.size} files. Files: ${gson.toJson(fileListResponse.files)}")
                allFiles.addAll(fileListResponse.files)
                pageToken = fileListResponse.nextPageToken
            }
        } while (pageToken != null)

        return Result.success(allFiles.mapNotNull { it.toDisplayableDriveFile() }.distinctBy { it.id })
    }

    private fun DriveFile.toDisplayableDriveFile(): DriveFile? {
        if (!isDisplayable) return null
        if (!isShortcut) return this

        val targetId = shortcutDetails?.targetId?.takeIf { it.isNotBlank() } ?: return null
        val targetMimeType = shortcutDetails?.targetMimeType?.takeIf { it.isNotBlank() } ?: return null
        return copy(id = targetId, mimeType = targetMimeType)
    }

    private fun String.escapeDriveQueryValue(): String {
        return replace("\\", "\\\\").replace("'", "\\'")
    }
}
