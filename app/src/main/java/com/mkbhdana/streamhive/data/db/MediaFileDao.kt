package com.mkbhdana.streamhive.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaFileDao {
    @Query("SELECT * FROM media_files WHERE driveId = :driveId AND parentId = :parentId ORDER BY isFolder DESC, name ASC")
    fun getFilesByFolder(driveId: String, parentId: String?): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE driveId = :driveId AND isFolder = 0 ORDER BY name ASC")
    fun getAllVideosByDrive(driveId: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchAllFiles(query: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE name LIKE '%' || :query || '%' AND driveId = :driveId ORDER BY name ASC")
    fun searchFilesInDrive(query: String, driveId: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE name LIKE '%' || :query || '%' AND isFolder = 0 ORDER BY name ASC")
    fun searchFiles(query: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE id = :fileId")
    suspend fun getFileById(fileId: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE driveId = :driveId AND parentId = :parentId ORDER BY isFolder DESC, name ASC")
    suspend fun getFilesByFolderSync(driveId: String, parentId: String?): List<MediaFileEntity>

    @Query("SELECT * FROM media_files WHERE isFolder = 1 ORDER BY name ASC")
    fun getAllFolders(): Flow<List<MediaFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<MediaFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: MediaFileEntity)

    @Query("DELETE FROM media_files WHERE driveId = :driveId")
    suspend fun deleteByDrive(driveId: String)

    @Query("DELETE FROM media_files WHERE driveId = :driveId AND parentId = :parentId")
    suspend fun deleteByFolder(driveId: String, parentId: String?)

    @Query("DELETE FROM media_files")
    suspend fun deleteAll()
}
