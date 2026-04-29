package com.mkbhdana.streamhive.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tmdb_metadata")
data class TmdbMetadataEntity(
    @PrimaryKey
    val driveFileId: String,
    val tmdbId: Int,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val rating: Float? = null,
    val year: String? = null,
    val mediaType: String = "movie", // "movie" or "tv"
    val cachedAt: Long = System.currentTimeMillis()
) {
    /** Consider metadata stale after 7 days */
    fun isStale(): Boolean {
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - cachedAt > sevenDaysMs
    }
}
