package com.driveplay.app.data.tmdb

import com.google.gson.annotations.SerializedName

// ──── Search Responses ────

data class TmdbSearchMovieResponse(
    val page: Int = 1,
    val results: List<TmdbMovie> = emptyList(),
    @SerializedName("total_results") val totalResults: Int = 0
)

data class TmdbSearchTvResponse(
    val page: Int = 1,
    val results: List<TmdbTvShow> = emptyList(),
    @SerializedName("total_results") val totalResults: Int = 0
)

data class TmdbMultiSearchResponse(
    val page: Int = 1,
    val results: List<TmdbMultiResult> = emptyList()
)

// ──── Movie ────

data class TmdbMovie(
    val id: Int,
    val title: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Float? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList(),
    val popularity: Float? = null,
    @SerializedName("original_language") val originalLanguage: String? = null
) {
    val year: String?
        get() = releaseDate?.take(4)

    val fullPosterUrl: String?
        get() = posterPath?.let { "${IMAGE_BASE_URL}w500$it" }

    val fullBackdropUrl: String?
        get() = backdropPath?.let { "${IMAGE_BASE_URL}w780$it" }
}

// ──── TV Show ────

data class TmdbTvShow(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Float? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("genre_ids") val genreIds: List<Int> = emptyList(),
    val popularity: Float? = null,
    @SerializedName("original_language") val originalLanguage: String? = null
) {
    val year: String?
        get() = firstAirDate?.take(4)

    val fullPosterUrl: String?
        get() = posterPath?.let { "${IMAGE_BASE_URL}w500$it" }

    val fullBackdropUrl: String?
        get() = backdropPath?.let { "${IMAGE_BASE_URL}w780$it" }
}

// ──── Multi Search ────

data class TmdbMultiResult(
    val id: Int,
    @SerializedName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Float? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null
) {
    val displayTitle: String
        get() = title ?: name ?: ""

    val year: String?
        get() = (releaseDate ?: firstAirDate)?.take(4)

    val fullPosterUrl: String?
        get() = posterPath?.let { "${IMAGE_BASE_URL}w500$it" }

    val fullBackdropUrl: String?
        get() = backdropPath?.let { "${IMAGE_BASE_URL}w780$it" }
}

// ──── Constants ────

const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
const val TMDB_BASE_URL = "https://api.themoviedb.org/"
