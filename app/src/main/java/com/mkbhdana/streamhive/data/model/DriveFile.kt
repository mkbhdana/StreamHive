package com.mkbhdana.streamhive.data.model

import com.mkbhdana.streamhive.util.Constants
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
    val driveId: String? = null,
    val shortcutDetails: ShortcutDetails? = null
) {
    val isShortcut: Boolean
        get() = mimeType == Constants.MIME_TYPE_SHORTCUT

    val effectiveId: String
        get() = if (isShortcut) shortcutDetails?.targetId?.takeIf { it.isNotBlank() } ?: id else id

    val effectiveMimeType: String
        get() = if (isShortcut) shortcutDetails?.targetMimeType?.takeIf { it.isNotBlank() } ?: mimeType else mimeType

    val isFolder: Boolean
        get() = effectiveMimeType == Constants.MIME_TYPE_FOLDER

    val isVideo: Boolean
        get() {
            if (effectiveMimeType.startsWith("video/")) return true
            if (effectiveMimeType == Constants.MIME_TYPE_MATROSKA) return true
            val extension = fileExtension
                ?: name.substringAfterLast('.', missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() }
            return extension
                ?.lowercase()
                ?.let { it in Constants.SUPPORTED_VIDEO_EXTENSIONS } == true
        }

    val isDisplayable: Boolean
        get() = isFolder || isVideo

    val displaySize: String
        get() = com.mkbhdana.streamhive.util.FileUtils.formatFileSize(size)
}

@Serializable
data class ShortcutDetails(
    val targetId: String? = null,
    val targetMimeType: String? = null
)

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
