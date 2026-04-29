package com.mkbhdana.streamhive.data.model

import kotlinx.serialization.Serializable

@Serializable
sealed class AuthCredentials {
    @Serializable
    data class OAuth2Credentials(
        val clientId: String,
        val clientSecret: String,
        val redirectUri: String,
        val scope: String,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val expiresAt: Long = 0L
    ) : AuthCredentials()

    @Serializable
    data class ServiceAccountCredentials(
        val clientEmail: String,
        val privateKey: String,
        val tokenUri: String,
        val projectId: String? = null,
        val accessToken: String? = null,
        val expiresAt: Long = 0L
    ) : AuthCredentials()
}

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val credentials: AuthCredentials) : AuthState()
    data class Error(val message: String) : AuthState()
    data class NeedsAuthCode(val authUrl: String) : AuthState()
}
