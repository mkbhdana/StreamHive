package com.mkbhdana.streamhive.tv.components

import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity

/**
 * Image-model helpers mirroring the mobile `HomeTab` logic so TV cards render
 * the same high-quality TMDB / Drive thumbnails. Coil loads these URL strings.
 */

private val tmdbImageSizeRegex = Regex("/t/p/(w\\d+|original)/")
private val driveThumbnailSizeRegex = Regex("=s\\d+(-[a-z]+)?")

fun tmdbImageUrl(path: String?, size: String): String? {
    if (path.isNullOrBlank()) return null
    val fullUrl = if (path.startsWith("/")) "https://image.tmdb.org/t/p/$size$path" else path
    return if (fullUrl.contains("image.tmdb.org/t/p/")) {
        fullUrl.replace(tmdbImageSizeRegex, "/t/p/$size/")
    } else {
        fullUrl
    }
}

fun driveThumbnailUrl(url: String?, size: Int = 720): String? {
    if (url.isNullOrBlank()) return null
    return url.replace(driveThumbnailSizeRegex, "=s$size")
}

/** Poster (2:3) image model for a catalog item. */
fun posterModel(metadata: TmdbMetadataEntity?, file: MediaFileEntity?): String? =
    tmdbImageUrl(metadata?.posterPath, "w500")
        ?: driveThumbnailUrl(file?.thumbnailLink, 500)

/** Wide backdrop (16:9) image model, falling back to poster then thumbnail. */
fun backdropModel(metadata: TmdbMetadataEntity?, file: MediaFileEntity?): String? =
    tmdbImageUrl(metadata?.backdropPath, "w1280")
        ?: tmdbImageUrl(metadata?.posterPath, "w780")
        ?: driveThumbnailUrl(file?.thumbnailLink, 1280)
