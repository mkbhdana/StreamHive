package com.mkbhdana.streamhive.tv.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mkbhdana.streamhive.auth.AuthRepository
import com.mkbhdana.streamhive.data.model.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Drives the TV second-screen login: starts the local [TvAuthServer], exposes
 * the QR URL, and surfaces the shared [AuthRepository] auth state so the UI
 * advances automatically once the phone completes sign-in.
 */
@HiltViewModel
class TvAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    var loginUrl by mutableStateOf<String?>(null)
        private set
    var serverError by mutableStateOf<String?>(null)
        private set

    private var server: TvAuthServer? = null

    fun startServer() {
        if (server != null) return
        val ip = TvNetwork.localIpv4()
        if (ip == null) {
            serverError = "No network found. Connect your TV to the same Wi-Fi/Ethernet as your phone."
            return
        }
        val port = TvNetwork.freePort()
        val newServer = TvAuthServer(authRepository, port)
        try {
            newServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = newServer
            loginUrl = "http://$ip:$port/"
            serverError = null
        } catch (e: Exception) {
            serverError = "Could not start sign-in server: ${e.message}"
        }
    }

    fun stopServer() {
        server?.stop()
        server = null
    }

    fun logout() {
        authRepository.logout()
    }

    override fun onCleared() {
        super.onCleared()
        stopServer()
    }
}
