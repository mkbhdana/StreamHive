package com.mkbhdana.streamhive.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE fileId = :fileId")
    suspend fun getByFileId(fileId: String): PlaybackHistoryEntity?

    @Query("SELECT * FROM playback_history WHERE lastPosition > duration * 0.10 AND lastPosition < duration * 0.90 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getContinuePlaying(limit: Int = 10): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getLastPlayed(limit: Int = 20): List<PlaybackHistoryEntity>

    @Query("SELECT * FROM playback_history")
    suspend fun getAll(): List<PlaybackHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackHistoryEntity)

    @Query("UPDATE playback_history SET lastPlayerEngine = :playerEngine, lastDecoderMode = :decoderMode WHERE fileId = :fileId")
    suspend fun updatePlaybackSelection(fileId: String, playerEngine: String, decoderMode: String)

    @Query("DELETE FROM playback_history WHERE fileId = :fileId")
    suspend fun delete(fileId: String)

    @Query("DELETE FROM playback_history")
    suspend fun deleteAll()

    @Query("""UPDATE playback_history SET 
        lastPosition = :lastPosition, 
        duration = :duration, 
        lastPlayedAt = :lastPlayedAt, 
        posterPath = :posterPath, 
        thumbnailUrl = :thumbnailUrl, 
        lastPlayerEngine = :lastPlayerEngine, 
        lastDecoderMode = :lastDecoderMode 
        WHERE fileId = :fileId""")
    suspend fun updatePosition(
        fileId: String,
        lastPosition: Long,
        duration: Long,
        lastPlayedAt: Long,
        posterPath: String?,
        thumbnailUrl: String?,
        lastPlayerEngine: String?,
        lastDecoderMode: String?
    )

    @Query("UPDATE playback_history SET savedPlayerSettings = :settingsJson WHERE fileId = :fileId")
    suspend fun updateFileSettings(fileId: String, settingsJson: String?)

    @Query("UPDATE playback_history SET savedPlayerSettings = NULL WHERE fileId = :fileId")
    suspend fun clearFileSettings(fileId: String)
}
