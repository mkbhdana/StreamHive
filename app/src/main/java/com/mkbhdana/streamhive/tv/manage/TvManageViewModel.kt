package com.mkbhdana.streamhive.tv.manage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mkbhdana.streamhive.catalog.DriveRepository
import com.mkbhdana.streamhive.data.db.MediaFileDao
import com.mkbhdana.streamhive.data.db.PlaybackHistoryDao
import com.mkbhdana.streamhive.data.db.TmdbMetadataDao
import com.mkbhdana.streamhive.settings.AppPreferences
import com.mkbhdana.streamhive.tv.auth.TvNetwork
import dagger.hilt.android.lifecycle.HiltViewModel
import fi.iki.elonen.NanoHTTPD
import javax.inject.Inject

/** Starts the phone-management web server and exposes its QR URL. */
@HiltViewModel
class TvManageViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val driveRepository: DriveRepository,
    private val mediaFileDao: MediaFileDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val tmdbMetadataDao: TmdbMetadataDao
) : ViewModel() {

    var manageUrl by mutableStateOf<String?>(null)
        private set
    var serverError by mutableStateOf<String?>(null)
        private set

    private var server: TvManageServer? = null

    fun start() {
        if (server != null) return
        val ip = TvNetwork.localIpv4()
        if (ip == null) {
            serverError = "No network found. Connect your TV to the same Wi-Fi/Ethernet as your phone."
            return
        }
        val port = TvNetwork.freePort()
        val newServer = TvManageServer(prefs, driveRepository, mediaFileDao, playbackHistoryDao, tmdbMetadataDao, port)
        try {
            newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = newServer
            manageUrl = "http://$ip:$port/"
            serverError = null
        } catch (e: Exception) {
            serverError = "Could not start server: ${e.message}"
        }
    }

    fun stop() {
        server?.stop()
        server = null
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
