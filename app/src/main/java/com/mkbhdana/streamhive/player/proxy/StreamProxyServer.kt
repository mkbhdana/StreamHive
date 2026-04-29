package com.mkbhdana.streamhive.player.proxy

import android.util.Log
import com.mkbhdana.streamhive.auth.AuthRepository
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.ServerSocket

/**
 * Local proxy server that streams Google Drive files via a clean localhost URL.
 *
 * Usage:
 *   val url = streamProxyServer.getStreamUrl(fileId)
 *   // url = "http://127.0.0.1:<port>/stream/<fileId>"
 *
 * The proxy injects the OAuth Bearer token on each request, so players
 * (ExoPlayer, MPV, external) never see the Drive auth credentials.
 *
 * Supports HTTP Range requests for seeking.
 */
class StreamProxyServer(
    private val authRepository: AuthRepository,
    private val okHttpClient: OkHttpClient
) : NanoHTTPD("0.0.0.0", selectPort()) {

    init {
        // Disable NanoHTTPD's internal logger to prevent logcat spam (e.g. ConnectionResetException when players seek/disconnect)
        java.util.logging.Logger.getLogger(NanoHTTPD::class.java.name).level = java.util.logging.Level.OFF
    }

    companion object {
        private const val TAG = "StreamProxyServer"
        private const val DRIVE_FILE_URL = "https://www.googleapis.com/drive/v3/files"

        /** The port selected at construction time (before start()) */
        @Volatile
        private var selectedPort: Int = 0

        /** Static reference so navigation can build proxy URLs without DI */
        @Volatile
        var instanceUrl: String? = null
            private set

        @Volatile
        var instancePort: Int = 0
            private set

        /**
         * Pick an available port and remember it.
         * Called once from the NanoHTTPD constructor arg.
         */
        private fun selectPort(): Int {
            val port = ServerSocket(0).use { it.localPort }
            selectedPort = port
            return port
        }
    }

    override fun start() {
        super.start()
        // After start(), listeningPort is valid. Use selectedPort as fallback.
        val port = if (listeningPort > 0) listeningPort else selectedPort
        instancePort = port
        instanceUrl = "http://127.0.0.1:$port"
        Log.d(TAG, "Server started on port $port, instanceUrl=$instanceUrl")
    }

    override fun start(timeout: Int, daemon: Boolean) {
        super.start(timeout, daemon)
        val port = if (listeningPort > 0) listeningPort else selectedPort
        instancePort = port
        instanceUrl = "http://127.0.0.1:$port"
        Log.d(TAG, "Server started on port $port, instanceUrl=$instanceUrl")
    }

    /**
     * Return a localhost URL that streams the given Drive file.
     * Uses 127.0.0.1 — works for both in-app and on-device external apps.
     */
    fun getStreamUrl(fileId: String): String {
        val port = if (instancePort > 0) instancePort else selectedPort
        return "http://127.0.0.1:$port/stream/$fileId"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: return newErrorResponse("No URI")

        // Route: /stream/{fileId}
        if (!uri.startsWith("/stream/")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }

        val fileId = uri.removePrefix("/stream/").trim()
        if (fileId.isBlank()) {
            return newErrorResponse("Missing file ID")
        }

        return try {
            streamDriveFile(fileId, session)
        } catch (e: Exception) {
            Log.e(TAG, "Stream error for $fileId", e)
            newErrorResponse("Stream error: ${e.message}")
        }
    }

    private fun streamDriveFile(fileId: String, session: IHTTPSession): Response {
        // Get fresh access token
        val token = runBlocking { authRepository.getValidAccessToken() }
            ?: return newErrorResponse("Auth failed — no valid token")

        val driveUrl = "$DRIVE_FILE_URL/$fileId?alt=media"

        val requestBuilder = Request.Builder()
            .url(driveUrl)
            .header("Authorization", "Bearer $token")

        // Pass through Range header for seeking support
        val rangeHeader = session.headers["range"]
        if (rangeHeader != null) {
            requestBuilder.header("Range", rangeHeader)
        }

        val driveResponse = okHttpClient.newCall(requestBuilder.build()).execute()

        if (!driveResponse.isSuccessful) {
            val code = driveResponse.code
            val body = driveResponse.body?.string() ?: ""
            driveResponse.close()
            Log.e(TAG, "Drive API returned $code: $body")
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Drive error: $code"
            )
        }

        val responseBody = driveResponse.body ?: run {
            driveResponse.close()
            return newErrorResponse("Empty response body")
        }

        val contentType = driveResponse.header("Content-Type") ?: "application/octet-stream"
        val contentLength = responseBody.contentLength()
        val inputStream: InputStream = responseBody.byteStream()

        // Determine status based on whether this is a range response
        val status = if (driveResponse.code == 206) {
            Response.Status.PARTIAL_CONTENT
        } else {
            Response.Status.OK
        }

        val response = if (contentLength > 0) {
            newFixedLengthResponse(status, contentType, inputStream, contentLength)
        } else {
            newChunkedResponse(status, contentType, inputStream)
        }

        // Forward Range-related headers from Drive
        driveResponse.header("Content-Range")?.let {
            response.addHeader("Content-Range", it)
        }
        driveResponse.header("Accept-Ranges")?.let {
            response.addHeader("Accept-Ranges", it)
        }

        // Allow seeking
        response.addHeader("Accept-Ranges", "bytes")

        return response
    }

    private fun newErrorResponse(message: String): Response {
        Log.e(TAG, message)
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            MIME_PLAINTEXT,
            message
        )
    }
}
