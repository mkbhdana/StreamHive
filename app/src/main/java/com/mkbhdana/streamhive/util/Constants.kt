package com.mkbhdana.streamhive.util

object Constants {
    const val GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
    const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3/"
    const val DRIVE_FILES_URL = "${DRIVE_API_BASE}files"
    const val DRIVE_DRIVES_URL = "${DRIVE_API_BASE}drives"
    const val DEFAULT_SCOPE = "https://www.googleapis.com/auth/drive.readonly"

    const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
    const val MIME_TYPE_SHORTCUT = "application/vnd.google-apps.shortcut"
    const val MIME_TYPE_MATROSKA = "application/x-matroska"

    const val VIDEO_MIME_TYPES_QUERY = "(mimeType contains 'video/' or mimeType = '$MIME_TYPE_FOLDER' or mimeType = '$MIME_TYPE_MATROSKA' or mimeType = '$MIME_TYPE_SHORTCUT')"
    const val DRIVE_FILE_FIELDS = "files(id,name,mimeType,size,thumbnailLink,modifiedTime,createdTime,parents,videoMediaMetadata,fileExtension,driveId,shortcutDetails(targetId,targetMimeType))"
    const val DRIVE_LIST_FIELDS = "nextPageToken,$DRIVE_FILE_FIELDS"

    val SUPPORTED_VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
        "m4v", "3gp", "ts", "mts", "m2ts", "vob", "mpg", "mpeg"
    )
}
