package com.mkbhdana.streamhive.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaFileEntity::class, TmdbMetadataEntity::class, PlaybackHistoryEntity::class],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaFileDao(): MediaFileDao
    abstract fun tmdbMetadataDao(): TmdbMetadataDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "streamhive_database"
                )
                    .addMigrations(MIGRATION_6_7)
                    .addMigrations(MIGRATION_7_8)
                    .addMigrations(MIGRATION_8_9)
                    .addMigrations(MIGRATION_9_10)
                    .addMigrations(MIGRATION_10_11)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_history ADD COLUMN lastPlayerEngine TEXT")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN lastDecoderMode TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tmdb_metadata ADD COLUMN originalLanguage TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_history ADD COLUMN savedPlayerSettings TEXT")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tmdb_metadata ADD COLUMN imdbId TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS media_files_new (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        size INTEGER,
                        thumbnailLink TEXT,
                        modifiedTime TEXT,
                        createdTime TEXT,
                        parentId TEXT NOT NULL,
                        driveId TEXT NOT NULL,
                        fileExtension TEXT,
                        isFolder INTEGER NOT NULL,
                        videoWidth INTEGER,
                        videoHeight INTEGER,
                        videoDurationMs INTEGER,
                        lastSyncTime INTEGER NOT NULL,
                        PRIMARY KEY(id, driveId, parentId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO media_files_new (
                        id, name, mimeType, size, thumbnailLink, modifiedTime,
                        createdTime, parentId, driveId, fileExtension, isFolder,
                        videoWidth, videoHeight, videoDurationMs, lastSyncTime
                    )
                    SELECT
                        id, name, mimeType, size, thumbnailLink, modifiedTime,
                        createdTime, COALESCE(parentId, ''), driveId, fileExtension,
                        isFolder, videoWidth, videoHeight, videoDurationMs, lastSyncTime
                    FROM media_files
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE media_files")
                db.execSQL("ALTER TABLE media_files_new RENAME TO media_files")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_files_driveId_parentId ON media_files(driveId, parentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_files_id ON media_files(id)")
            }
        }
    }
}
