package com.driveplay.app.util

import com.driveplay.app.data.db.MediaFileEntity

/**
 * Best-effort regex matcher for mapping TV episode metadata (season/episode numbers)
 * to Drive file names. Patterns are tried in order from most specific to least.
 *
 * If no confident match is found, returns null — the UI should show a manual file picker.
 */
object EpisodeMatcher {

    private val patterns = listOf(
        // S01E03, s1e3, S01.E03
        Regex("""[Ss](\d{1,2})\s*[.\-]?\s*[Ee](\d{1,3})"""),
        // 1x03, 01x03
        Regex("""(\d{1,2})[xX](\d{1,3})"""),
        // Season 1 Episode 3, Season.1.Episode.3
        Regex("""[Ss]eason\s*[.\-]?\s*(\d{1,2})\s*[.\-]?\s*[Ee]pisode\s*[.\-]?\s*(\d{1,3})""", RegexOption.IGNORE_CASE),
        // E03 or e03 (season-agnostic, matched with context)
        Regex("""[^a-zA-Z][Ee](\d{1,3})[^a-zA-Z\d]"""),
        // Episode 3, Episode.3
        Regex("""[Ee]pisode\s*[.\-]?\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
    )

    /**
     * Find the best matching file for the given season and episode number.
     * Returns null if no confident match is found.
     */
    fun findMatch(
        seasonNum: Int,
        episodeNum: Int,
        files: List<MediaFileEntity>
    ): MediaFileEntity? {
        // Try patterns with both season and episode
        for (file in files) {
            if (file.isFolder) continue
            val name = file.name

            // Pattern 1: S01E03
            patterns[0].find(name)?.let { match ->
                val s = match.groupValues[1].toIntOrNull()
                val e = match.groupValues[2].toIntOrNull()
                if (s == seasonNum && e == episodeNum) return file
            }

            // Pattern 2: 1x03
            patterns[1].find(name)?.let { match ->
                val s = match.groupValues[1].toIntOrNull()
                val e = match.groupValues[2].toIntOrNull()
                if (s == seasonNum && e == episodeNum) return file
            }

            // Pattern 3: Season 1 Episode 3
            patterns[2].find(name)?.let { match ->
                val s = match.groupValues[1].toIntOrNull()
                val e = match.groupValues[2].toIntOrNull()
                if (s == seasonNum && e == episodeNum) return file
            }
        }

        // Fallback: episode-only patterns (less confident — only if single season or user is browsing season folder)
        for (file in files) {
            if (file.isFolder) continue
            val name = file.name

            // Pattern 4: E03
            patterns[3].find(" $name ")?.let { match ->
                val e = match.groupValues[1].toIntOrNull()
                if (e == episodeNum) return file
            }

            // Pattern 5: Episode 3
            patterns[4].find(name)?.let { match ->
                val e = match.groupValues[1].toIntOrNull()
                if (e == episodeNum) return file
            }
        }

        return null
    }

    /**
     * Extract season and episode numbers from a filename, if possible.
     * Returns Pair(season, episode) or null.
     */
    fun extractSeasonEpisode(fileName: String): Pair<Int, Int>? {
        // S01E03
        patterns[0].find(fileName)?.let { match ->
            val s = match.groupValues[1].toIntOrNull() ?: return null
            val e = match.groupValues[2].toIntOrNull() ?: return null
            return Pair(s, e)
        }
        // 1x03
        patterns[1].find(fileName)?.let { match ->
            val s = match.groupValues[1].toIntOrNull() ?: return null
            val e = match.groupValues[2].toIntOrNull() ?: return null
            return Pair(s, e)
        }
        // Season 1 Episode 3
        patterns[2].find(fileName)?.let { match ->
            val s = match.groupValues[1].toIntOrNull() ?: return null
            val e = match.groupValues[2].toIntOrNull() ?: return null
            return Pair(s, e)
        }
        return null
    }

    /**
     * Extract only the season number from a filename.
     * Returns the season number or null if not found.
     */
    fun extractSeason(fileName: String): Int? {
        return extractSeasonEpisode(fileName)?.first
    }
}
