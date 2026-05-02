package com.mkbhdana.streamhive.auth

import android.util.Base64
import com.mkbhdana.streamhive.data.model.AuthCredentials
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceAccountClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager
) {
    private val gson = Gson()

    data class ServiceAccountJson(
        @SerializedName("type") val type: String? = null,
        @SerializedName("project_id") val projectId: String? = null,
        @SerializedName("private_key_id") val privateKeyId: String? = null,
        @SerializedName("private_key") val privateKey: String = "",
        @SerializedName("client_email") val clientEmail: String = "",
        @SerializedName("client_id") val clientId: String? = null,
        @SerializedName("auth_uri") val authUri: String? = null,
        @SerializedName("token_uri") val tokenUri: String = "https://oauth2.googleapis.com/token"
    )

    fun parseServiceAccountJson(json: String): Result<ServiceAccountJson> {
        return try {
            val parsed = gson.fromJson(json, ServiceAccountJson::class.java)
            if (parsed.clientEmail.isBlank() || parsed.privateKey.isBlank()) {
                Result.failure(Exception("Invalid service account JSON: missing client_email or private_key"))
            } else {
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse service account JSON: ${e.message}"))
        }
    }

    suspend fun authenticate(
        serviceAccount: ServiceAccountJson,
        scope: String = "https://www.googleapis.com/auth/drive.readonly"
    ): Result<AuthCredentials.ServiceAccountCredentials> = withContext(Dispatchers.IO) {
        try {
            val jwt = createJwt(serviceAccount, scope)

            val formBody = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("assertion", jwt)
                .build()

            val request = Request.Builder()
                .url(serviceAccount.tokenUri)
                .post(formBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    return@withContext Result.failure(
                        Exception("Service account auth failed: ${response.code} - $errorBody")
                    )
                }

                val body = response.body ?: return@withContext Result.failure(
                    Exception("Service account auth failed: empty body")
                )

                val tokenResponse = gson.fromJson(body.charStream(), TokenResponse::class.java)
                val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)

                val credentials = AuthCredentials.ServiceAccountCredentials(
                    clientEmail = serviceAccount.clientEmail,
                    privateKey = serviceAccount.privateKey,
                    tokenUri = serviceAccount.tokenUri,
                    projectId = serviceAccount.projectId,
                    accessToken = tokenResponse.accessToken,
                    expiresAt = expiresAt
                )

                tokenManager.saveServiceAccountCredentials(credentials)
                Result.success(credentials)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val credentials = tokenManager.getStoredCredentials()
            if (credentials !is AuthCredentials.ServiceAccountCredentials) {
                return@withContext Result.failure(Exception("No service account credentials stored"))
            }

            val serviceAccount = ServiceAccountJson(
                clientEmail = credentials.clientEmail,
                privateKey = credentials.privateKey,
                tokenUri = credentials.tokenUri,
                projectId = credentials.projectId
            )

            val jwt = createJwt(serviceAccount, "https://www.googleapis.com/auth/drive.readonly")

            val formBody = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("assertion", jwt)
                .build()

            val request = Request.Builder()
                .url(credentials.tokenUri)
                .post(formBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    return@withContext Result.failure(
                        Exception("Token refresh failed: ${response.code} - $errorBody")
                    )
                }

                val body = response.body ?: return@withContext Result.failure(
                    Exception("Token refresh failed: empty body")
                )

                val tokenResponse = gson.fromJson(body.charStream(), TokenResponse::class.java)
                val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)

                tokenManager.updateAccessToken(tokenResponse.accessToken, expiresAt)
                Result.success(tokenResponse.accessToken)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createJwt(serviceAccount: ServiceAccountJson, scope: String): String {
        val now = System.currentTimeMillis() / 1000
        val exp = now + 3600

        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claimSet = """{
            "iss":"${serviceAccount.clientEmail}",
            "scope":"$scope",
            "aud":"${serviceAccount.tokenUri}",
            "exp":$exp,
            "iat":$now
        }""".trimIndent()

        val encodedHeader = base64UrlEncode(header.toByteArray())
        val encodedClaims = base64UrlEncode(claimSet.toByteArray())
        val signatureInput = "$encodedHeader.$encodedClaims"

        val signature = signRsa256(signatureInput.toByteArray(), serviceAccount.privateKey)
        val encodedSignature = base64UrlEncode(signature)

        return "$signatureInput.$encodedSignature"
    }

    private fun signRsa256(data: ByteArray, privateKeyPem: String): ByteArray {
        val keyContent = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .replace(" ", "")
            .trim()

        val keyBytes = Base64.decode(keyContent, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val key = keyFactory.generatePrivate(keySpec)

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(key)
        sig.update(data)
        return sig.sign()
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private data class TokenResponse(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("expires_in") val expiresIn: Int = 3600,
        @SerializedName("token_type") val tokenType: String = "Bearer"
    )
}
