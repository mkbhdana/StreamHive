package com.mkbhdana.streamhive.auth

import com.mkbhdana.streamhive.data.model.AuthCredentials
import com.mkbhdana.streamhive.util.Constants
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAuth2Client @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager
) {
    private val gson = Gson()

    fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String,
        scope: String
    ): String {
        val encodedRedirectUri = URLEncoder.encode(redirectUri, "UTF-8")
        val encodedScope = URLEncoder.encode(scope, "UTF-8")
        return "${Constants.GOOGLE_AUTH_URL}?" +
                "client_id=$clientId" +
                "&redirect_uri=$encodedRedirectUri" +
                "&response_type=code" +
                "&scope=$encodedScope" +
                "&access_type=offline" +
                "&prompt=consent"
    }

    suspend fun exchangeAuthorizationCode(
        clientId: String,
        clientSecret: String,
        redirectUri: String,
        scope: String,
        authCode: String
    ): Result<AuthCredentials.OAuth2Credentials> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("code", authCode)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", redirectUri)
                .add("grant_type", "authorization_code")
                .build()

            val request = Request.Builder()
                .url(Constants.GOOGLE_TOKEN_URL)
                .post(formBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(
                    Exception("Token exchange failed: ${response.code} - $body")
                )
            }

            val tokenResponse = gson.fromJson(body, TokenResponse::class.java)
            val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)

            val credentials = AuthCredentials.OAuth2Credentials(
                clientId = clientId,
                clientSecret = clientSecret,
                redirectUri = redirectUri,
                scope = scope,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresAt = expiresAt
            )

            tokenManager.saveOAuth2Credentials(credentials)
            Result.success(credentials)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val credentials = tokenManager.getStoredCredentials()
            if (credentials !is AuthCredentials.OAuth2Credentials) {
                return@withContext Result.failure(Exception("No OAuth2 credentials stored"))
            }

            val refreshToken = credentials.refreshToken
                ?: return@withContext Result.failure(Exception("No refresh token available"))

            val formBody = FormBody.Builder()
                .add("client_id", credentials.clientId)
                .add("client_secret", credentials.clientSecret)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url(Constants.GOOGLE_TOKEN_URL)
                .post(formBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(
                    Exception("Token refresh failed: ${response.code} - $body")
                )
            }

            val tokenResponse = gson.fromJson(body, TokenResponse::class.java)
            val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)

            tokenManager.updateAccessToken(tokenResponse.accessToken, expiresAt)
            Result.success(tokenResponse.accessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class TokenResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("refresh_token") val refreshToken: String? = null,
        @SerializedName("expires_in") val expiresIn: Int = 3600,
        @SerializedName("token_type") val tokenType: String = "Bearer",
        @SerializedName("scope") val scope: String? = null
    )
}
