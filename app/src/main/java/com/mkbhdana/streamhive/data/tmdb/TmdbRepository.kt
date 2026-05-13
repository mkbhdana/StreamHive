package com.mkbhdana.streamhive.data.tmdb

import com.mkbhdana.streamhive.data.db.TmdbMetadataDao
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity
import com.mkbhdana.streamhive.settings.AppPreferences
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
            // Remove file extensions
            .replace(Regex("""\.(?:mp4|mkv|avi|mov|wmv|flv|webm|m4v|ts|mts|srt|sub|ass|ssa|idx)$""", RegexOption.IGNORE_CASE), "")
            // Remove content in brackets [...]
            .replace(Regex("""\[.*?]"""), " ")
            // Remove content in parentheses (...)
            .replace(Regex("""\(.*?\)"""), " ")
            // Remove quality, codec, and release tags
            .replace(Regex("""(?:720p|1080p|2160p|4K|UHD|HDR|HDR10|DV|DoVi|Dolby\.?Vision|BluRay|Blu-Ray|BRRip|BDRip|WEBRip|WEB-DL|WEB|DVDRip|HDTV|PDTV|x264|x265|h\.?264|h\.?265|HEVC|AVC|AAC|DTS|FLAC|AC3|EAC3|Atmos|TrueHD|REMUX|PROPER|REPACK|EXTENDED|UNRATED|DC|Directors\.?Cut|10bit|8bit|SDR|AMZN|NF|DSNP|HMAX|ATVP|PCOK)""", RegexOption.IGNORE_CASE), " ")
            // Replace separators with spaces
            .replace(Regex("""[._-]+"""), " ")
            // Collapse multiple spaces
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
        val requestStartedAt = System.currentTimeMillis()
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return@withContext null

        // Check cache first
        val cached = tmdbMetadataDao.getByDriveFileId(driveFileId)
        if (cached != null && !cached.isStale() && !cached.originalLanguage.isNullOrBlank() && !cached.isIncomplete) {
            return@withContext cached
        }

        val query = cleanNameForSearch(name)
        if (query.isBlank()) return@withContext null

        try {
            val entity = when (mediaType) {
                "movie" -> searchMovie(apiKey, query, driveFileId)
                    ?: searchMulti(apiKey, query, driveFileId) // fallback to multi if typed search fails
                "tv" -> searchTvShow(apiKey, query, driveFileId)
                    ?: searchMulti(apiKey, query, driveFileId) // fallback to multi if typed search fails
                else -> searchMulti(apiKey, query, driveFileId)
            }

            if (entity != null) {
                val latest = tmdbMetadataDao.getByDriveFileId(driveFileId)
                if (latest != null && latest.cachedAt >= requestStartedAt) {
                    return@withContext latest
                }
                tmdbMetadataDao.insert(entity)
            }
            entity
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Pick the best matching result from a list of candidates.
     * Prefers exact title match (case-insensitive), then containment, then first result.
     */
    private fun <T> bestMatch(
        results: List<T>,
        query: String,
        titleExtractor: (T) -> String?
    ): T? {
        if (results.isEmpty()) return null
        if (results.size == 1) return results.first()

        val queryLower = query.lowercase().trim()

        // 1. Exact match (case-insensitive)
        results.firstOrNull { titleExtractor(it)?.lowercase()?.trim() == queryLower }
            ?.let { return it }

        // 2. Title contains the query
        results.firstOrNull { titleExtractor(it)?.lowercase()?.contains(queryLower) == true }
            ?.let { return it }

        // 3. Query contains the title (for shorter TMDB titles)
        results.firstOrNull {
            val title = titleExtractor(it)?.lowercase()?.trim()
            title != null && title.length > 2 && queryLower.contains(title)
        }?.let { return it }

        // 4. Fallback to first result
        return results.first()
    }

    private suspend fun searchMovie(apiKey: String, query: String, driveFileId: String): TmdbMetadataEntity? {
        val response = tmdbApiService.searchMovies(apiKey, query)
        val movie = bestMatch(response.results, query) { it.displayTitle } ?: return null
        return movie.toMetadataEntity(driveFileId)
    }

    private suspend fun searchTvShow(apiKey: String, query: String, driveFileId: String): TmdbMetadataEntity? {
        val response = tmdbApiService.searchTvShows(apiKey, query)
        val show = bestMatch(response.results, query) { it.displayTitle } ?: return null
        return show.toMetadataEntity(driveFileId)
    }

    private suspend fun searchMulti(apiKey: String, query: String, driveFileId: String): TmdbMetadataEntity? {
        val response = tmdbApiService.searchMulti(apiKey, query)
        val filtered = response.results.filter {
            it.mediaType == "movie" || it.mediaType == "tv"
        }
        val result = bestMatch(filtered, query) { it.displayTitle } ?: return null
        return TmdbMetadataEntity(
            driveFileId = driveFileId,
            tmdbId = result.id,
            title = result.displayTitle,
            overview = result.displayOverview,
            posterPath = result.fullPosterUrl,
            backdropPath = result.fullBackdropUrl,
            rating = result.voteAverage,
            year = result.year,
            originalLanguage = result.originalLanguage,
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

    suspend fun repairMetadataIfIncomplete(
        metadata: TmdbMetadataEntity,
        mediaTypeHint: String = "auto"
    ): TmdbMetadataEntity? = withContext(Dispatchers.IO) {
        val requestStartedAt = System.currentTimeMillis()
        if (!metadata.needsDisplayRepair) return@withContext metadata

        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return@withContext metadata

        val preferredType = mediaTypeHint
            .takeIf { it == "movie" || it == "tv" }
            ?: metadata.mediaType.takeIf { it == "movie" || it == "tv" }
            ?: "auto"

        val repaired = lookupByTmdbId(
            apiKey = apiKey,
            tmdbId = metadata.tmdbId,
            driveFileId = metadata.driveFileId,
            mediaTypeHint = preferredType
        ) ?: return@withContext metadata

        val shouldReplace = repaired.title.isNotBlank() && (
            metadata.title.isBlank() ||
                metadata.titleLooksLocalized ||
                metadata.overview.isNullOrBlank() ||
                repaired.overview != metadata.overview
            )

        if (shouldReplace) {
            val latest = tmdbMetadataDao.getByDriveFileId(metadata.driveFileId)
            if (latest != null && latest.cachedAt >= requestStartedAt) {
                return@withContext latest
            }
            tmdbMetadataDao.deleteByDriveFileId(metadata.driveFileId)
            tmdbMetadataDao.insert(repaired)
            repaired
        } else {
            metadata
        }
    }

    suspend fun getFullMovieDetails(tmdbId: Int): TmdbMovie? {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return null
        return try {
            getMovieDetailsWithTextFallback(apiKey, tmdbId)
        } catch (e: Exception) { null }
    }

    suspend fun getFullTvDetails(tmdbId: Int): TmdbTvShow? {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return null
        return try {
            getTvDetailsWithTextFallback(apiKey, tmdbId)
        } catch (e: Exception) { null }
    }

    suspend fun getTvSeasons(tmdbId: Int, numberOfSeasons: Int): List<TmdbSeasonResponse> {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return emptyList()
        return (1..numberOfSeasons).mapNotNull { seasonNum ->
            try {
                tmdbApiService.getTvSeasonDetails(tmdbId, seasonNum, apiKey)
            } catch (e: Exception) { null }
        }
    }

    /**
     * Fix metadata by manually providing a TMDB or IMDB ID.
     * - Numeric input → treated as TMDB ID and uses /movie/{id} or /tv/{id}
     * - "tt..." input → treated as IMDB ID and uses /find/{external_id}
     */
    suspend fun fixMetadataById(
        driveFileId: String,
        idInput: String,
        mediaTypeHint: String = "auto"
    ): TmdbMetadataEntity? = withContext(Dispatchers.IO) {
        val apiKey = prefs.tmdbApiKey
        if (apiKey.isBlank()) return@withContext null

        try {
            val entity = if (idInput.startsWith("tt", ignoreCase = true)) {
                // IMDB ID
                lookupByImdbId(apiKey, idInput, driveFileId, mediaTypeHint)
            } else {
                // TMDB ID
                val tmdbId = idInput.toIntOrNull() ?: return@withContext null
                lookupByTmdbId(apiKey, tmdbId, driveFileId, mediaTypeHint)
            }

            if (entity != null) {
                // Delete old entry and insert new one
                tmdbMetadataDao.deleteByDriveFileId(driveFileId)
                tmdbMetadataDao.insert(entity)
            }
            entity
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun lookupByTmdbId(
        apiKey: String,
        tmdbId: Int,
        driveFileId: String,
        mediaTypeHint: String
    ): TmdbMetadataEntity? {
        if (mediaTypeHint == "movie" || mediaTypeHint == "auto") {
            try {
                return getMovieDetailsWithTextFallback(apiKey, tmdbId).toMetadataEntity(driveFileId)
            } catch (_: Exception) {}
        }

        if (mediaTypeHint == "tv" || mediaTypeHint == "auto") {
            try {
                return getTvDetailsWithTextFallback(apiKey, tmdbId).toMetadataEntity(driveFileId)
            } catch (_: Exception) {}
        }

        return null
    }

    private suspend fun lookupByImdbId(
        apiKey: String,
        imdbId: String,
        driveFileId: String,
        mediaTypeHint: String = "auto"
    ): TmdbMetadataEntity? {
        val findResponse = tmdbApiService.findByExternalId(imdbId, apiKey)

        if (mediaTypeHint != "tv") {
            findResponse.movieResults.firstOrNull()?.let { movie ->
                return movie.toMetadataEntity(driveFileId)
            }
        }

        if (mediaTypeHint != "movie") {
            findResponse.tvResults.firstOrNull()?.let { show ->
                return show.toMetadataEntity(driveFileId)
            }
        }

        return null
    }

    private suspend fun getMovieDetailsWithTextFallback(apiKey: String, tmdbId: Int): TmdbMovie {
        val primary = tmdbApiService.getMovieDetails(tmdbId, apiKey)
        val fallbackLanguage = primary.originalLanguage
            ?.takeIf { it.isNotBlank() && it != "en" }
            ?: return primary
        if (primary.displayTitle.isNotBlank() && !primary.displayOverview.isNullOrBlank()) return primary

        val fallback = runCatching {
            tmdbApiService.getMovieDetails(tmdbId, apiKey, language = fallbackLanguage)
        }.getOrNull() ?: return primary

        return primary.copy(
            title = primary.title.takeUnlessBlank()
                ?: fallback.title.takeUnlessBlank()
                ?: primary.originalTitle.takeUnlessBlank()
                ?: fallback.originalTitle.takeUnlessBlank(),
            originalTitle = primary.originalTitle.takeUnlessBlank()
                ?: fallback.originalTitle.takeUnlessBlank(),
            overview = primary.overview.takeUnlessBlank()
                ?: fallback.overview.takeUnlessBlank()
        )
    }

    private suspend fun getTvDetailsWithTextFallback(apiKey: String, tmdbId: Int): TmdbTvShow {
        val primary = tmdbApiService.getTvDetails(tmdbId, apiKey)
        val fallbackLanguage = primary.originalLanguage
            ?.takeIf { it.isNotBlank() && it != "en" }
            ?: return primary
        if (primary.displayTitle.isNotBlank() && !primary.displayOverview.isNullOrBlank()) return primary

        val fallback = runCatching {
            tmdbApiService.getTvDetails(tmdbId, apiKey, language = fallbackLanguage)
        }.getOrNull() ?: return primary

        return primary.copy(
            name = primary.name.takeUnlessBlank()
                ?: fallback.name.takeUnlessBlank()
                ?: primary.originalName.takeUnlessBlank()
                ?: fallback.originalName.takeUnlessBlank(),
            originalName = primary.originalName.takeUnlessBlank()
                ?: fallback.originalName.takeUnlessBlank(),
            overview = primary.overview.takeUnlessBlank()
                ?: fallback.overview.takeUnlessBlank()
        )
    }

    private fun TmdbMovie.toMetadataEntity(driveFileId: String): TmdbMetadataEntity {
        return TmdbMetadataEntity(
            driveFileId = driveFileId,
            tmdbId = id,
            title = displayTitle,
            overview = displayOverview,
            posterPath = fullPosterUrl,
            backdropPath = fullBackdropUrl,
            rating = voteAverage,
            year = year,
            originalLanguage = originalLanguage,
            mediaType = "movie"
        )
    }

    private fun TmdbTvShow.toMetadataEntity(driveFileId: String): TmdbMetadataEntity {
        return TmdbMetadataEntity(
            driveFileId = driveFileId,
            tmdbId = id,
            title = displayTitle,
            overview = displayOverview,
            posterPath = fullPosterUrl,
            backdropPath = fullBackdropUrl,
            rating = voteAverage,
            year = year,
            originalLanguage = originalLanguage,
            mediaType = "tv"
        )
    }

    private fun String?.takeUnlessBlank(): String? {
        return this?.takeIf { it.isNotBlank() }
    }
}
