package com.mkbhdana.streamhive.tv.components

import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity
import com.mkbhdana.streamhive.ui.image.PosterSource

/**
 * Image-model helpers for TV cards. These delegate to [PosterSource] so TV and mobile
 * always resolve artwork the same way (including third-party posters). Coil loads the
 * returned URL strings.
 */

fun tmdbImageUrl(path: String?, size: String): String? = PosterSource.tmdbImageUrl(path, size)

fun driveThumbnailUrl(url: String?, size: Int = 720): String? =
    PosterSource.driveThumbnailUrl(url, size)

/** Poster (2:3) image model for a catalog item. */
fun posterModel(metadata: TmdbMetadataEntity?, file: MediaFileEntity?): String? =
    PosterSource.posterModel(metadata, file)

/** Wide backdrop (16:9) image model, falling back to poster then thumbnail. */
fun backdropModel(metadata: TmdbMetadataEntity?, file: MediaFileEntity?): String? =
    PosterSource.backdropModel(metadata, file)
