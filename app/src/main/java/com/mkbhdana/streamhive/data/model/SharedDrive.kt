package com.mkbhdana.streamhive.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SharedDrive(
    val id: String,
    val name: String,
    val backgroundImageLink: String? = null,
    val colorRgb: String? = null
)

@Serializable
data class SharedDriveListResponse(
    val nextPageToken: String? = null,
    val drives: List<SharedDrive> = emptyList()
)
