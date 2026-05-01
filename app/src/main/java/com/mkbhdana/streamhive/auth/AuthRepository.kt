package com.mkbhdana.streamhive.auth

import android.content.Context
import android.util.Log
import com.mkbhdana.streamhive.data.model.AuthCredentials
import com.mkbhdana.streamhive.data.model.AuthState
import com.mkbhdana.streamhive.util.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    private val oAuth2Client: OAuth2Client,
    private val serviceAccountClient: ServiceAccountClient
) {
    companion object {
        private const val TAG = "AuthRepository"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkExistingAuth()
    }

    /**
     * Check for existing credentials in the datastore.
     *
     * If credentials exist (OAuth2 with refresh token, or Service Account with private key),
     * the session is considered valid — we set Authenticated immediately and kick off
     * a background token refresh if the access token is expired.
     *
     * This ensures the user is never kicked to the login screen just because the
     * short-lived access token expired while the app was closed / offline.
     */
    private fun checkExistingAuth() {
        val credentials = tokenManager.getStoredCredentials()
        if (credentials != null) {
            _authState.value = AuthState.Authenticated(credentials)

            // If the access token is already expired, proactively refresh in background
            if (tokenManager.isTokenExpired()) {
                Log.d(TAG, "Stored access token is expired — scheduling background refresh")
                scope.launch {
                    refreshTokenSilently()
                }
            }
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

    /**
     * Retrieve a valid access token, refreshing automatically if expired.
     *
     * - If the token is still valid, returns it immediately.
     * - If expired and network is available, refreshes using the refresh token
     *   (OAuth2) or service account key, persists the new token, and restores
     *   the auth state to [AuthState.Authenticated].
     * - If expired and no network, returns the expired token as a best-effort
     *   fallback (some cached/offline operations may still work).
     * - Auth state is NEVER set to Error here — callers handle failures
     *   gracefully so the user isn't unexpectedly kicked to the login screen.
     */
    suspend fun getValidAccessToken(): String? {
        if (tokenManager.isTokenExpired()) {
            Log.d(TAG, "Access token expired — attempting refresh")

            if (!NetworkUtils.isNetworkAvailable(context)) {
                Log.w(TAG, "No network — returning expired token as fallback")
                return tokenManager.getAccessToken()
            }

            val credentials = tokenManager.getStoredCredentials() ?: return null

            val refreshResult = when (credentials) {
                is AuthCredentials.OAuth2Credentials -> oAuth2Client.refreshAccessToken()
                is AuthCredentials.ServiceAccountCredentials -> serviceAccountClient.refreshAccessToken()
            }

            refreshResult.fold(
                onSuccess = { newToken ->
                    Log.d(TAG, "Token refreshed successfully")
                    // Restore auth state to Authenticated with updated credentials
                    val updated = tokenManager.getStoredCredentials()
                    if (updated != null) {
                        _authState.value = AuthState.Authenticated(updated)
                    }
                    return newToken
                },
                onFailure = { error ->
                    Log.e(TAG, "Token refresh failed: ${error.message}")
                    // Don't set AuthState.Error — the session credentials are still
                    // valid (refresh token / service account key), the network call
                    // just failed transiently. Callers will see null and can retry.
                    return null
                }
            )
        }
        return tokenManager.getAccessToken()
    }

    /**
     * Silently refresh the access token in the background.
     * Used at startup to proactively get a fresh token without blocking the UI.
     * Does not alter auth state on failure.
     */
    private suspend fun refreshTokenSilently() {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Log.d(TAG, "Background refresh skipped — no network")
            return
        }

        val credentials = tokenManager.getStoredCredentials() ?: return

        val refreshResult = when (credentials) {
            is AuthCredentials.OAuth2Credentials -> oAuth2Client.refreshAccessToken()
            is AuthCredentials.ServiceAccountCredentials -> serviceAccountClient.refreshAccessToken()
        }

        refreshResult.fold(
            onSuccess = {
                Log.d(TAG, "Background token refresh succeeded")
                val updated = tokenManager.getStoredCredentials()
                if (updated != null) {
                    _authState.value = AuthState.Authenticated(updated)
                }
            },
            onFailure = { error ->
                Log.w(TAG, "Background token refresh failed (will retry on next API call): ${error.message}")
            }
        )
    }

    fun logout() {
        tokenManager.clearAll()
        _authState.value = AuthState.Unauthenticated
    }

    fun isAuthenticated(): Boolean = _authState.value is AuthState.Authenticated
}
