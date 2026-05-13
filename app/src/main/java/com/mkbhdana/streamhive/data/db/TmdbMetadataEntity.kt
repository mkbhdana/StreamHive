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
    val originalLanguage: String? = null,
    val mediaType: String = "movie", // "movie" or "tv"
    val cachedAt: Long = System.currentTimeMillis()
) {
    val hasUsableTitle: Boolean
        get() = title.isNotBlank()

    val hasUsableOverview: Boolean
        get() = !overview.isNullOrBlank()

    val isIncomplete: Boolean
        get() = !hasUsableTitle || !hasUsableOverview

    val titleLooksLocalized: Boolean
        get() = title.any { it.code > 127 }

    val needsDisplayRepair: Boolean
        get() = isIncomplete || titleLooksLocalized

    /** Consider metadata stale after 7 days */
    fun isStale(): Boolean {
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - cachedAt > sevenDaysMs
    }
}
