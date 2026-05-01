package com.mkbhdana.streamhive.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long? = null,
    val thumbnailLink: String? = null,
    val modifiedTime: String? = null,
    val createdTime: String? = null,
    val parents: List<String>? = null,
    val fileExtension: String? = null,
    val videoMediaMetadata: VideoMediaMetadata? = null,
    val driveId: String? = null
) {
    val isFolder: Boolean
        get() = mimeType == "application/vnd.google-apps.folder"

    val displaySize: String
        get() = com.mkbhdana.streamhive.util.FileUtils.formatFileSize(size)
}

@Serializable
data class VideoMediaMetadata(
    val width: Int? = null,
    val height: Int? = null,
    val durationMillis: Long? = null
)

@Serializable
data class DriveFileListResponse(
    val nextPageToken: String? = null,
    val files: List<DriveFile> = emptyList()
)
