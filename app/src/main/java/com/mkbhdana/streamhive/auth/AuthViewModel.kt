package com.mkbhdana.streamhive.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mkbhdana.streamhive.data.model.AuthState
import com.mkbhdana.streamhive.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    // Form fields
    private val _clientId = MutableStateFlow("")
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _clientSecret = MutableStateFlow("")
    val clientSecret: StateFlow<String> = _clientSecret.asStateFlow()

    private val _redirectUri = MutableStateFlow("")
    val redirectUri: StateFlow<String> = _redirectUri.asStateFlow()

    private val _scope = MutableStateFlow(Constants.DEFAULT_SCOPE)
    val scope: StateFlow<String> = _scope.asStateFlow()

    private val _authCode = MutableStateFlow("")
    val authCode: StateFlow<String> = _authCode.asStateFlow()

    private val _serviceAccountJson = MutableStateFlow("")
    val serviceAccountJson: StateFlow<String> = _serviceAccountJson.asStateFlow()

    private val _authUrl = MutableStateFlow<String?>(null)
    val authUrl: StateFlow<String?> = _authUrl.asStateFlow()

    fun updateClientId(value: String) { _clientId.value = value }
    fun updateClientSecret(value: String) { _clientSecret.value = value }
    fun updateRedirectUri(value: String) { _redirectUri.value = value }
    fun updateScope(value: String) { _scope.value = value }
    fun updateAuthCode(value: String) { 
        val extractedCode = try {
            if (value.contains("code=")) {
                val uri = android.net.Uri.parse(value)
                uri.getQueryParameter("code") ?: value
            } else {
                value
            }
        } catch (e: Exception) {
            value
        }
        _authCode.value = extractedCode.trim()
    }
    fun updateServiceAccountJson(value: String) { _serviceAccountJson.value = value }

    fun generateAuthUrl() {
        val url = authRepository.initiateOAuth2(
            clientId = _clientId.value,
            clientSecret = _clientSecret.value,
            redirectUri = _redirectUri.value,
            scope = _scope.value
        )
        _authUrl.value = url
    }

    fun submitAuthorizationCode() {
        viewModelScope.launch {
            authRepository.completeOAuth2(
                clientId = _clientId.value,
                clientSecret = _clientSecret.value,
                redirectUri = _redirectUri.value,
                scope = _scope.value,
                authorizationCode = _authCode.value.trim()
            )
        }
    }

    fun authenticateWithServiceAccount() {
        viewModelScope.launch {
            authRepository.authenticateWithServiceAccount(_serviceAccountJson.value)
        }
    }

    fun logout() {
        authRepository.logout()
        _clientId.value = ""
        _clientSecret.value = ""
        _authCode.value = ""
        _serviceAccountJson.value = ""
        _authUrl.value = null
    }
}
