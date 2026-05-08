package com.mkbhdana.streamhive.data.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB API v3 service interface.
 * Base URL: https://api.themoviedb.org/
 */
interface TmdbApiService {

    @GET("3/search/movie")
    suspend fun searchMovies(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US",
        @Query("primary_release_year") year: String? = null
    ): TmdbSearchMovieResponse

    @GET("3/search/tv")
    suspend fun searchTvShows(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US",
        @Query("first_air_date_year") year: String? = null
    ): TmdbSearchTvResponse

    @GET("3/search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en-US"
    ): TmdbMultiSearchResponse

    @GET("3/movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): TmdbMovie

    @GET("3/tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): TmdbTvShow

    @GET("3/tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): TmdbSeasonResponse

    @GET("3/find/{external_id}")
    suspend fun findByExternalId(
        @Path("external_id") externalId: String,
        @Query("api_key") apiKey: String,
        @Query("external_source") externalSource: String = "imdb_id",
        @Query("language") language: String = "en-US"
    ): TmdbFindResponse
}
