package com.driveplay.app.data.tmdb

import com.driveplay.app.data.db.TmdbMetadataDao
import com.driveplay.app.data.db.TmdbMetadataEntity
import com.driveplay.app.settings.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbRepository @Inject constructor(
    private val tmdbApiService: TmdbApiService,
    private val tmdbMetadataDao: TmdbMetadataDao,
    private val prefs: AppPreferences
) {
    /**
     * Clean a file/folder name to make it suitable for TMDB search.
     * Removes year patterns, quality tags, codec info etc.
     */
    private fun cleanNameForSearch(name: String): String {
        return name
            .replace(Regex("""\.(mp4|mkv|avi|mov|wmv|flv|webm|m4v|ts|mts)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[.*?]"""), "")
            .replace(Regex("""\(.*?\)"""), "")
            .replace(Regex("""(720p|1080p|2160p|4K|HDR|BluRay|BRRip|WEBRip|WEB-DL|DVDRip|x264|x265|HEVC|AAC|DTS|FLAC|REMUX)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[._-]+"""), " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    /**
     * Search TMDB for a file/folder name and cache the result.
     * @param name The file or folder name from Drive
     * @param driveFileId The drive file ID to link metadata to
     * @param mediaType "movie", "tv", or "auto"
     */
    suspend fun fetchAndCacheMetadata(
        driveFileId: String,
        name: String,
        mediaType: String = "auto"
    ): TmdbMetadataEntity? = withContext(Dispatchers.IO) {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return@withContext null

        // Check cache first
        val cached = tmdbMetadataDao.getByDriveFileId(driveFileId)
        if (cached != null && !cached.isStale()) return@withContext cached

        val query = cleanNameForSearch(name)
        if (query.isBlank()) return@withContext null

        try {
            val entity = when (mediaType) {
                "movie" -> searchMovie(apiKey, query, driveFileId)
                "tv" -> searchTvShow(apiKey, query, driveFileId)
                else -> searchMulti(apiKey, query, driveFileId)
            }

            if (entity != null) {
                tmdbMetadataDao.insert(entity)
            }
            entity
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun searchMovie(apiKey: String, query: String, driveFileId: String): TmdbMetadataEntity? {
        val response = tmdbApiService.searchMovies(apiKey, query)
        val movie = response.results.firstOrNull() ?: return null
        return TmdbMetadataEntity(
            driveFileId = driveFileId,
            tmdbId = movie.id,
            title = movie.title ?: "",
            overview = movie.overview,
            posterPath = movie.fullPosterUrl,
            backdropPath = movie.fullBackdropUrl,
            rating = movie.voteAverage,
            year = movie.year,
            mediaType = "movie"
        )
    }

    private suspend fun searchTvShow(apiKey: String, query: String, driveFileId: String): TmdbMetadataEntity? {
        val response = tmdbApiService.searchTvShows(apiKey, query)
        val show = response.results.firstOrNull() ?: return null
        return TmdbMetadataEntity(
            driveFileId = driveFileId,
            tmdbId = show.id,
            title = show.name ?: "",
            overview = show.overview,
            posterPath = show.fullPosterUrl,
            backdropPath = show.fullBackdropUrl,
            rating = show.voteAverage,
            year = show.year,
            mediaType = "tv"
        )
    }

    private suspend fun searchMulti(apiKey: String, query: String, driveFileId: String): TmdbMetadataEntity? {
        val response = tmdbApiService.searchMulti(apiKey, query)
        val result = response.results.firstOrNull {
            it.mediaType == "movie" || it.mediaType == "tv"
        } ?: return null
        return TmdbMetadataEntity(
            driveFileId = driveFileId,
            tmdbId = result.id,
            title = result.displayTitle,
            overview = result.overview,
            posterPath = result.fullPosterUrl,
            backdropPath = result.fullBackdropUrl,
            rating = result.voteAverage,
            year = result.year,
            mediaType = result.mediaType ?: "movie"
        )
    }

    suspend fun getMetadataForFile(driveFileId: String): TmdbMetadataEntity? {
        return tmdbMetadataDao.getByDriveFileId(driveFileId)
    }

    suspend fun getMetadataForFiles(driveFileIds: List<String>): List<TmdbMetadataEntity> {
        return tmdbMetadataDao.getByDriveFileIds(driveFileIds)
    }

    fun isConfigured(): Boolean = prefs.tmdbApiKey.isNotBlank()
}
