package com.mkbhdana.streamhive.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val driveId: String,
    val lastPosition: Long,    // in milliseconds
    val duration: Long,        // in milliseconds
    val lastPlayedAt: Long,    // System.currentTimeMillis()
    val posterPath: String? = null,
    val thumbnailUrl: String? = null,
    val lastPlayerEngine: String? = null,
    val lastDecoderMode: String? = null
) {
    val progressPercent: Float
        get() = if (duration > 0) (lastPosition.toFloat() / duration.toFloat()) else 0f

    val isCompleted: Boolean
        get() = progressPercent > 0.90f

    val isResumeEligible: Boolean
        get() = progressPercent > 0.10f && progressPercent < 0.90f
}
