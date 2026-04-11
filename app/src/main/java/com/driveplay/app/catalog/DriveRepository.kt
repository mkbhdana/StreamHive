package com.driveplay.app.catalog

import com.driveplay.app.auth.AuthRepository
import com.driveplay.app.data.db.MediaFileDao
import com.driveplay.app.data.db.MediaFileEntity
import com.driveplay.app.data.model.DriveFile
import com.driveplay.app.data.model.DriveFileListResponse
import com.driveplay.app.data.model.SharedDrive
import com.driveplay.app.data.model.SharedDriveListResponse
import com.driveplay.app.util.Constants
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

                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body == null) {
                    return@withContext Result.failure(
                        Exception("Failed to list drives: ${response.code} - $body")
                    )
                }

                val driveListResponse = gson.fromJson(body, SharedDriveListResponse::class.java)
                allDrives.addAll(driveListResponse.drives)
                pageToken = driveListResponse.nextPageToken
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

            val parentQuery = if (folderId != null) {
                "'$folderId' in parents"
            } else {
                "'$driveId' in parents"
            }

            val query = "$parentQuery and ${Constants.VIDEO_MIME_TYPES_QUERY} and trashed = false"

            do {
                val urlBuilder = StringBuilder("${Constants.DRIVE_FILES_URL}?")
                urlBuilder.append("corpora=drive")
                urlBuilder.append("&driveId=$driveId")
                urlBuilder.append("&includeItemsFromAllDrives=true")
                urlBuilder.append("&supportsAllDrives=true")
                urlBuilder.append("&q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                urlBuilder.append("&fields=${java.net.URLEncoder.encode(Constants.DRIVE_LIST_FIELDS, "UTF-8")}")
                urlBuilder.append("&pageSize=100")
                urlBuilder.append("&orderBy=folder,name")
                if (pageToken != null) {
                    urlBuilder.append("&pageToken=$pageToken")
                }

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body == null) {
                    return@withContext Result.failure(
                        Exception("Failed to list files: ${response.code} - $body")
                    )
                }

                val fileListResponse = gson.fromJson(body, DriveFileListResponse::class.java)
                allFiles.addAll(fileListResponse.files)
                pageToken = fileListResponse.nextPageToken
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
                    parentId = folderId ?: driveId,
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
            Result.failure(e)
        }
    }

    fun getCachedFiles(driveId: String, parentId: String?): Flow<List<MediaFileEntity>> {
        return mediaFileDao.getFilesByFolder(driveId, parentId ?: driveId)
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

    fun getAllFolders(): Flow<List<MediaFileEntity>> {
        return mediaFileDao.getAllFolders()
    }

    fun getStreamUrl(fileId: String): String {
        return "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
    }

    suspend fun getValidToken(): String? {
        return authRepository.getValidAccessToken()
    }
}
