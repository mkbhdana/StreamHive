package com.mkbhdana.streamhive.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "media_files",
    primaryKeys = ["id", "driveId", "parentId"],
    indices = [
        Index(value = ["driveId", "parentId"]),
        Index(value = ["id"])
    ]
)
data class MediaFileEntity(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long? = null,
    val thumbnailLink: String? = null,
    val modifiedTime: String? = null,
    val createdTime: String? = null,
    val parentId: String = "",
    val driveId: String,
    val fileExtension: String? = null,
    val isFolder: Boolean = false,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val videoDurationMs: Long? = null,
    val lastSyncTime: Long = System.currentTimeMillis()
)
