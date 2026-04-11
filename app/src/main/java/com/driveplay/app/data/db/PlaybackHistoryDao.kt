package com.driveplay.app.data.db

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

    @Query("SELECT * FROM playback_history WHERE lastPosition > 0 AND lastPosition < duration * 0.92 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun getContinuePlaying(limit: Int = 10): Flow<List<PlaybackHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE fileId = :fileId")
    suspend fun delete(fileId: String)

    @Query("DELETE FROM playback_history")
    suspend fun deleteAll()
}
