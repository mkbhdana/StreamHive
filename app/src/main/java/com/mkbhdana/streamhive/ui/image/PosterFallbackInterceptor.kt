package com.mkbhdana.streamhive.ui.image

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult

/**
 * Falls back to TMDB art when a third-party poster fails to load.
 *
 * Third-party poster URLs built by [PosterSource] carry their TMDB equivalent in the URL
 * fragment. When the third-party request errors — the host 404s for an IMDb id it does not
 * have, is down, or times out — this retries the load with that TMDB URL, so a missing
 * third-party poster degrades to the old artwork instead of an empty card.
 *
 * Doing this in the image loader keeps every call site unchanged: composables still pass a
 * plain URL string to `AsyncImage`.
 */
class PosterFallbackInterceptor : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed(chain.request)
        if (result !is ErrorResult) return result

        val fallback = PosterSource.fallbackOf(chain.request.data) ?: return result
        return chain.proceed(
            chain.request.newBuilder()
                .data(fallback)
                .build()
        )
    }
}
