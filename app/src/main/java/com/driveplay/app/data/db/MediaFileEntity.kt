package com.driveplay.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_files")
data class MediaFileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long? = null,
    val thumbnailLink: String? = null,
    val modifiedTime: String? = null,
    val parentId: String? = null,
    val driveId: String,
    val fileExtension: String? = null,
    val isFolder: Boolean = false,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val videoDurationMs: Long? = null,
    val lastSyncTime: Long = System.currentTimeMillis()
)
