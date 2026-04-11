package com.driveplay.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TmdbMetadataDao {

    @Query("SELECT * FROM tmdb_metadata WHERE driveFileId = :driveFileId")
    suspend fun getByDriveFileId(driveFileId: String): TmdbMetadataEntity?

    @Query("SELECT * FROM tmdb_metadata WHERE driveFileId IN (:driveFileIds)")
    suspend fun getByDriveFileIds(driveFileIds: List<String>): List<TmdbMetadataEntity>

    @Query("SELECT * FROM tmdb_metadata WHERE mediaType = :mediaType ORDER BY title ASC")
    suspend fun getByMediaType(mediaType: String): List<TmdbMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TmdbMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TmdbMetadataEntity>)

    @Query("DELETE FROM tmdb_metadata WHERE driveFileId = :driveFileId")
    suspend fun deleteByDriveFileId(driveFileId: String)

    @Query("DELETE FROM tmdb_metadata")
    suspend fun deleteAll()
}
