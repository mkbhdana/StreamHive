package com.driveplay.app.di

import android.content.Context
import com.driveplay.app.auth.AuthRepository
import com.driveplay.app.data.db.AppDatabase
import com.driveplay.app.data.db.MediaFileDao
import com.driveplay.app.data.db.PlaybackHistoryDao
import com.driveplay.app.data.db.TmdbMetadataDao
import com.driveplay.app.data.tmdb.TmdbApiService
import com.driveplay.app.data.tmdb.TMDB_BASE_URL
import com.driveplay.app.player.proxy.StreamProxyServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideMediaFileDao(database: AppDatabase): MediaFileDao {
        return database.mediaFileDao()
    }

    @Provides
    @Singleton
    fun provideTmdbMetadataDao(database: AppDatabase): TmdbMetadataDao {
        return database.tmdbMetadataDao()
    }

    @Provides
    @Singleton
    fun providePlaybackHistoryDao(database: AppDatabase): PlaybackHistoryDao {
        return database.playbackHistoryDao()
    }

    @Provides
    @Singleton
    fun provideTmdbApiService(okHttpClient: OkHttpClient): TmdbApiService {
        return Retrofit.Builder()
            .baseUrl(TMDB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideStreamProxyServer(
        authRepository: AuthRepository,
        okHttpClient: OkHttpClient
    ): StreamProxyServer {
        return StreamProxyServer(authRepository, okHttpClient).also { server ->
            try {
                server.start()
                android.util.Log.d("StreamProxyServer", "Started on port ${server.listeningPort}")
            } catch (e: Exception) {
                android.util.Log.e("StreamProxyServer", "Failed to start proxy", e)
            }
        }
    }
}
