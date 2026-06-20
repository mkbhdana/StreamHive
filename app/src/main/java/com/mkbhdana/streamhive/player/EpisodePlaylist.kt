package com.mkbhdana.streamhive.player

import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.settings.SourcePriorityConfig
import com.mkbhdana.streamhive.settings.SourcePriorityFilter
import com.mkbhdana.streamhive.util.EpisodeMatcher

/**
 * Shared helpers for the in-player episode list, kept identical between the
 * ExoPlayer and MPV view models.
 */
object EpisodePlaylist {

    /**
     * Apply source priority (per episode, so single-source episodes survive) and
     * sort by season/episode so "next episode" is deterministic.
     */
    fun build(files: List<MediaFileEntity>, config: SourcePriorityConfig): List<MediaFileEntity> {
        val filtered = if (config.hasAnyPriority) {
            SourcePriorityFilter.filter(
                files,
                config,
                groupBy = EpisodeMatcher::sourceGroupKey
            ).files
        } else {
            files
        }
        return filtered.sortedWith(compareBy({ EpisodeMatcher.orderKey(it) }, { it.name.lowercase() }))
    }

    /** The episode that follows the current item, or null if it is the last / a movie. */
    fun next(
        episodes: List<MediaFileEntity>,
        currentFileId: String,
        currentFileName: String
    ): MediaFileEntity? {
        val index = episodes.indexOfFirst { it.id == currentFileId }
        if (index >= 0) return episodes.getOrNull(index + 1)

        // The current file may have been filtered out (e.g. a lower-quality source the
        // user launched directly). Fall back to season/episode ordering.
        val currentKey = EpisodeMatcher.extractSeasonEpisode(currentFileName) ?: return null
        val currentOrder = currentKey.first * 1000 + currentKey.second
        return episodes.firstOrNull { EpisodeMatcher.orderKey(it) > currentOrder }
    }
}
