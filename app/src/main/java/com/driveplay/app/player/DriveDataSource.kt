package com.driveplay.app.player

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DataSource

/**
 * Custom DataSource that injects Google Drive API Bearer token
 * into every HTTP request made by ExoPlayer, enabling authenticated
 * video streaming from Google Drive.
 */
@UnstableApi
class DriveDataSource(
    private val tokenProvider: suspend () -> String?
) : BaseDataSource(/* isNetwork = */ true) {

    private var httpDataSource: DefaultHttpDataSource? = null
    private var currentToken: String? = null

    override fun open(dataSpec: DataSpec): Long {
        // Get token synchronously - it should be pre-fetched
        val token = currentToken ?: throw IllegalStateException("Token not set. Call setToken() before open().")

        val factory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                mapOf(
                    "Authorization" to "Bearer $token",
                    "Accept" to "*/*"
                )
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        httpDataSource = factory.createDataSource()
        return httpDataSource!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return httpDataSource?.read(buffer, offset, length) ?: -1
    }

    override fun getUri(): Uri? = httpDataSource?.uri

    override fun close() {
        httpDataSource?.close()
        httpDataSource = null
    }

    fun setToken(token: String) {
        currentToken = token
    }

    @UnstableApi
    class Factory(
        private val tokenProvider: suspend () -> String?
    ) : DataSource.Factory {

        private var currentToken: String? = null

        fun updateToken(token: String) {
            currentToken = token
        }

        override fun createDataSource(): DriveDataSource {
            val dataSource = DriveDataSource(tokenProvider)
            currentToken?.let { dataSource.setToken(it) }
            return dataSource
        }
    }
}
