package com.mkbhdana.streamhive.ui.image

import android.net.Uri
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity

/**
 * Builds the image URLs used by catalog cards, for both TMDB art and the optional
 * third-party (btttr.cc) poster art.
 *
 * The third-party posters are keyed by IMDb id and are 500x750 — the same 2:3 shape as
 * TMDB's w500 posters, so no layout changes are needed. Not every title resolves to an
 * IMDb id, and the host can 404 or go down, so every third-party URL carries its TMDB
 * equivalent as a **URL fragment** (`#fb=…`). Fragments are never transmitted, so the
 * third party never sees it; [PosterFallbackInterceptor] reads it back off the request
 * and retries with the TMDB URL if the poster fails to load.
 */
object PosterSource {

    /** Set from app startup and whenever the user toggles the setting. */
    @Volatile
    var thirdPartyEnabled: Boolean = false

    /** User-editable URL template; `{imdb_id}` is replaced with the title's IMDb id. */
    @Volatile
    var thirdPartyTemplate: String = DEFAULT_TEMPLATE

    const val FALLBACK_FRAGMENT = "#fb="

    const val ID_PLACEHOLDER = "{imdb_id}"

    const val DEFAULT_TEMPLATE =
        "https://btttr.cc/poster-a/imdb/poster-default/{imdb_id}.jpg?tag=none"

    /**
     * Matches a poster URL and splits it into the parts that matter.
     *
     * Only two things may vary, so a typo anywhere else is rejected rather than silently
     * accepted:
     *  - the quality suffix straight after `https://btttr.cc/poster` — `-a`, `-qa`, `-gq`,
     *    `-nq`, or nothing at all;
     *  - the query, which is optional and preserved verbatim (`?tag=…&rs=…`, a subset of
     *    those, other parameters, or none).
     *
     * `https://btttr.cc/poster`, `/imdb/poster-default/` and `.jpg` are fixed. The id is
     * either the `{imdb_id}` placeholder or a literal IMDb id, so a URL pasted straight
     * from a browser becomes a usable template.
     *
     * The placeholder is quoted with Regex.escape rather than written inline: Android's
     * ICU regex engine rejects a bare `}` (desktop JVMs tolerate it), and quoting also
     * keeps the pattern correct if ID_PLACEHOLDER ever changes.
     */
    private val TEMPLATE_REGEX = Regex(
        """^(https://btttr\.cc/poster[A-Za-z0-9-]*/imdb/poster-default/)(${Regex.escape(ID_PLACEHOLDER)}|tt\d{6,})(\.jpg)(\?\S*)?$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Validate a user-entered URL and convert it to a template. A literal IMDb id is
     * rewritten to [ID_PLACEHOLDER], so pasting a working poster URL just works.
     * Returns null when the text isn't a usable poster URL.
     */
    fun normalizeTemplate(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val match = TEMPLATE_REGEX.matchEntire(trimmed) ?: return null
        val (prefix, _, extension, query) = match.destructured
        return prefix + ID_PLACEHOLDER + extension + query
    }

    /** True when the text is a usable poster URL (or a template). */
    fun isValidTemplate(input: String): Boolean = normalizeTemplate(input) != null

    private fun buildThirdPartyUrl(imdbId: String): String? {
        val template = thirdPartyTemplate.takeIf { it.contains(ID_PLACEHOLDER) } ?: return null
        return template.replace(ID_PLACEHOLDER, imdbId)
    }

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

    /**
     * Poster (2:3) image model for a catalog item: third-party art when enabled and an
     * IMDb id is known, otherwise the TMDB poster, otherwise the Drive thumbnail.
     */
    fun posterModel(
        metadata: TmdbMetadataEntity?,
        file: MediaFileEntity?,
        size: String = "w500"
    ): String? {
        val tmdbUrl = tmdbImageUrl(metadata?.posterPath, size)
        val imdbId = metadata?.imdbId
        if (thirdPartyEnabled && !imdbId.isNullOrBlank()) {
            buildThirdPartyUrl(imdbId)?.let { return withFallback(it, tmdbUrl) }
        }
        return tmdbUrl ?: driveThumbnailUrl(file?.thumbnailLink, 500)
    }

    /**
     * Wide backdrop (16:9). Always TMDB — the third-party endpoint only serves posters.
     */
    fun backdropModel(
        metadata: TmdbMetadataEntity?,
        file: MediaFileEntity?
    ): String? =
        tmdbImageUrl(metadata?.backdropPath, "w1280")
            ?: tmdbImageUrl(metadata?.posterPath, "w780")
            ?: driveThumbnailUrl(file?.thumbnailLink, 1280)

    /** Appends the fallback URL as a fragment, which is never sent to the server. */
    private fun withFallback(url: String, fallback: String?): String =
        if (fallback.isNullOrBlank()) url else url + FALLBACK_FRAGMENT + Uri.encode(fallback)

    /** The TMDB URL stashed on a third-party poster URL, if any. */
    fun fallbackOf(data: Any?): String? {
        val url = data as? String ?: return null
        val index = url.indexOf(FALLBACK_FRAGMENT)
        if (index < 0) return null
        return Uri.decode(url.substring(index + FALLBACK_FRAGMENT.length)).takeIf { it.isNotBlank() }
    }
}
