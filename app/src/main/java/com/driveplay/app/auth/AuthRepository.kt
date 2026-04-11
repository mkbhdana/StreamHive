package com.driveplay.app.auth

import com.driveplay.app.data.model.AuthCredentials
import com.driveplay.app.data.model.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val oAuth2Client: OAuth2Client,
    private val serviceAccountClient: ServiceAccountClient
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkExistingAuth()
    }

    private fun checkExistingAuth() {
        val credentials = tokenManager.getStoredCredentials()
        if (credentials != null) {
            _authState.value = AuthState.Authenticated(credentials)
        }
    }

    fun initiateOAuth2(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        scope: String
    ): String {
        return oAuth2Client.buildAuthorizationUrl(clientId, redirectUri, scope)
    }

    suspend fun completeOAuth2(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        scope: String,
        authorizationCode: String
    ) {
        _authState.value = AuthState.Loading
        val result = oAuth2Client.exchangeAuthorizationCode(
            clientId, clientSecret, redirectUri, scope, authorizationCode
        )
        result.fold(
            onSuccess = { credentials ->
                _authState.value = AuthState.Authenticated(credentials)
            },
            onFailure = { error ->
                _authState.value = AuthState.Error(error.message ?: "OAuth2 authentication failed")
            }
        )
    }

    suspend fun authenticateWithServiceAccount(jsonContent: String) {
        _authState.value = AuthState.Loading
        val parseResult = serviceAccountClient.parseServiceAccountJson(jsonContent)
        parseResult.fold(
            onSuccess = { serviceAccount ->
                val authResult = serviceAccountClient.authenticate(serviceAccount)
                authResult.fold(
                    onSuccess = { credentials ->
                        _authState.value = AuthState.Authenticated(credentials)
                    },
                    onFailure = { error ->
                        _authState.value = AuthState.Error(error.message ?: "Service account authentication failed")
                    }
                )
            },
            onFailure = { error ->
                _authState.value = AuthState.Error(error.message ?: "Failed to parse service account file")
            }
        )
    }

    suspend fun getValidAccessToken(): String? {
        if (tokenManager.isTokenExpired()) {
            val refreshResult = when (tokenManager.getStoredCredentials()) {
                is AuthCredentials.OAuth2Credentials -> oAuth2Client.refreshAccessToken()
                is AuthCredentials.ServiceAccountCredentials -> serviceAccountClient.refreshAccessToken()
                else -> return null
            }
            refreshResult.fold(
                onSuccess = { return it },
                onFailure = {
                    _authState.value = AuthState.Error("Token refresh failed: ${it.message}")
                    return null
                }
            )
        }
        return tokenManager.getAccessToken()
    }

    fun logout() {
        tokenManager.clearAll()
        _authState.value = AuthState.Unauthenticated
    }

    fun isAuthenticated(): Boolean = _authState.value is AuthState.Authenticated
}
