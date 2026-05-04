package com.mkbhdana.streamhive.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mkbhdana.streamhive.data.model.AuthCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TokenManager"
        private const val PREFS_NAME = "streamhive_secure_prefs"

        private const val KEY_AUTH_TYPE = "auth_type"
        private const val AUTH_TYPE_OAUTH2 = "oauth2"
        private const val AUTH_TYPE_SERVICE_ACCOUNT = "service_account"

        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_CLIENT_SECRET = "client_secret"
        private const val KEY_REDIRECT_URI = "redirect_uri"
        private const val KEY_SCOPE = "scope"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"

        private const val KEY_SA_CLIENT_EMAIL = "sa_client_email"
        private const val KEY_SA_PRIVATE_KEY = "sa_private_key"
        private const val KEY_SA_TOKEN_URI = "sa_token_uri"
        private const val KEY_SA_PROJECT_ID = "sa_project_id"
    }

    @Volatile private var securePrefs: SharedPreferences? = null

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun prefs(): SharedPreferences {
        return securePrefs ?: synchronized(this) {
            securePrefs ?: runCatching { createPrefs() }
                .getOrElse { error ->
                    resetSecurePrefs(error)
                    createPrefs()
                }
                .also { securePrefs = it }
        }
    }

    private fun resetSecurePrefs(error: Throwable) {
        Log.w(TAG, "Secure auth storage was unreadable; clearing it", error)
        securePrefs = null
        runCatching { context.deleteSharedPreferences(PREFS_NAME) }
    }

    fun saveOAuth2Credentials(credentials: AuthCredentials.OAuth2Credentials) {
        runCatching {
            prefs().edit()
                .putString(KEY_AUTH_TYPE, AUTH_TYPE_OAUTH2)
                .putString(KEY_CLIENT_ID, credentials.clientId)
                .putString(KEY_CLIENT_SECRET, credentials.clientSecret)
                .putString(KEY_REDIRECT_URI, credentials.redirectUri)
                .putString(KEY_SCOPE, credentials.scope)
                .putString(KEY_ACCESS_TOKEN, credentials.accessToken)
                .putString(KEY_REFRESH_TOKEN, credentials.refreshToken)
                .putLong(KEY_EXPIRES_AT, credentials.expiresAt)
                .apply()
        }.onFailure { resetSecurePrefs(it) }
    }

    fun saveServiceAccountCredentials(credentials: AuthCredentials.ServiceAccountCredentials) {
        runCatching {
            prefs().edit()
                .putString(KEY_AUTH_TYPE, AUTH_TYPE_SERVICE_ACCOUNT)
                .putString(KEY_SA_CLIENT_EMAIL, credentials.clientEmail)
                .putString(KEY_SA_PRIVATE_KEY, credentials.privateKey)
                .putString(KEY_SA_TOKEN_URI, credentials.tokenUri)
                .putString(KEY_SA_PROJECT_ID, credentials.projectId)
                .putString(KEY_ACCESS_TOKEN, credentials.accessToken)
                .putLong(KEY_EXPIRES_AT, credentials.expiresAt)
                .apply()
        }.onFailure { resetSecurePrefs(it) }
    }

    fun updateAccessToken(token: String, expiresAt: Long) {
        runCatching {
            prefs().edit()
                .putString(KEY_ACCESS_TOKEN, token)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .apply()
        }.onFailure { resetSecurePrefs(it) }
    }

    fun getStoredCredentials(): AuthCredentials? {
        return runCatching {
            readStoredCredentials()
        }.getOrElse { error ->
            resetSecurePrefs(error)
            null
        }
    }

    private fun readStoredCredentials(): AuthCredentials? {
        val prefs = prefs()
        val authType = prefs.getString(KEY_AUTH_TYPE, null) ?: return null

        return when (authType) {
            AUTH_TYPE_OAUTH2 -> {
                AuthCredentials.OAuth2Credentials(
                    clientId = prefs.getString(KEY_CLIENT_ID, "") ?: "",
                    clientSecret = prefs.getString(KEY_CLIENT_SECRET, "") ?: "",
                    redirectUri = prefs.getString(KEY_REDIRECT_URI, "") ?: "",
                    scope = prefs.getString(KEY_SCOPE, "") ?: "",
                    accessToken = prefs.getString(KEY_ACCESS_TOKEN, null),
                    refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null),
                    expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
                )
            }
            AUTH_TYPE_SERVICE_ACCOUNT -> {
                AuthCredentials.ServiceAccountCredentials(
                    clientEmail = prefs.getString(KEY_SA_CLIENT_EMAIL, "") ?: "",
                    privateKey = prefs.getString(KEY_SA_PRIVATE_KEY, "") ?: "",
                    tokenUri = prefs.getString(KEY_SA_TOKEN_URI, "") ?: "",
                    projectId = prefs.getString(KEY_SA_PROJECT_ID, null),
                    accessToken = prefs.getString(KEY_ACCESS_TOKEN, null),
                    expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
                )
            }
            else -> null
        }
    }

    fun getAccessToken(): String? {
        return runCatching { prefs().getString(KEY_ACCESS_TOKEN, null) }
            .getOrElse { error ->
                resetSecurePrefs(error)
                null
            }
    }

    fun getRefreshToken(): String? {
        return runCatching { prefs().getString(KEY_REFRESH_TOKEN, null) }
            .getOrElse { error ->
                resetSecurePrefs(error)
                null
            }
    }

    fun isTokenExpired(): Boolean {
        return runCatching {
            val expiresAt = prefs().getLong(KEY_EXPIRES_AT, 0L)
            System.currentTimeMillis() >= expiresAt - 60_000 // 1 min buffer
        }.getOrElse { error ->
            resetSecurePrefs(error)
            true
        }
    }

    fun clearAll() {
        runCatching { prefs().edit().clear().apply() }
        securePrefs = null
        runCatching { context.deleteSharedPreferences(PREFS_NAME) }
    }
}
