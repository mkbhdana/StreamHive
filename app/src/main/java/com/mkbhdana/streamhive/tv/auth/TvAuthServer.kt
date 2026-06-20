package com.mkbhdana.streamhive.tv.auth

import android.util.Log
import com.mkbhdana.streamhive.auth.AuthRepository
import com.mkbhdana.streamhive.data.model.AuthState
import com.mkbhdana.streamhive.util.Constants
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Local web server that lets the user sign in from a phone (scanned via QR).
 *
 * Serves a login page and accepts the OAuth 2.0 / Service Account credentials,
 * delegating to the shared [AuthRepository] — the very same singleton the TV UI
 * observes, so a successful sign-in flips `authState` and the TV advances.
 */
class TvAuthServer(
    private val authRepository: AuthRepository,
    port: Int
) : NanoHTTPD("0.0.0.0", port) {

    init {
        java.util.logging.Logger.getLogger(NanoHTTPD::class.java.name).level = java.util.logging.Level.OFF
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/" ->
                    newFixedLengthResponse(Response.Status.OK, "text/html", LoginWebAssets.PAGE)

                session.method == Method.POST && session.uri == "/oauth/url" -> handleOauthUrl(session)
                session.method == Method.POST && session.uri == "/oauth/complete" -> handleOauthComplete(session)
                session.method == Method.POST && session.uri == "/service-account" -> handleServiceAccount(session)

                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "serve error", e)
            jsonResponse(JSONObject().put("ok", false).put("message", e.message ?: "Server error"))
        }
    }

    private fun handleOauthUrl(session: IHTTPSession): Response {
        val body = readJson(session)
        val url = authRepository.initiateOAuth2(
            clientId = body.optString("clientId"),
            clientSecret = body.optString("clientSecret"),
            redirectUri = body.optString("redirectUri"),
            scope = Constants.DEFAULT_SCOPE
        )
        return jsonResponse(JSONObject().put("url", url))
    }

    private fun handleOauthComplete(session: IHTTPSession): Response {
        val body = readJson(session)
        val code = extractCode(body.optString("authorizationCode"))
        val ok = runBlocking {
            authRepository.completeOAuth2(
                clientId = body.optString("clientId"),
                clientSecret = body.optString("clientSecret"),
                redirectUri = body.optString("redirectUri"),
                scope = Constants.DEFAULT_SCOPE,
                authorizationCode = code
            )
            authRepository.authState.value is AuthState.Authenticated
        }
        return jsonResponse(resultJson(ok))
    }

    private fun handleServiceAccount(session: IHTTPSession): Response {
        val body = readJson(session)
        val ok = runBlocking {
            authRepository.authenticateWithServiceAccount(body.optString("json"))
            authRepository.authState.value is AuthState.Authenticated
        }
        return jsonResponse(resultJson(ok))
    }

    private fun resultJson(ok: Boolean): JSONObject {
        val obj = JSONObject().put("ok", ok)
        if (!ok) {
            val state = authRepository.authState.value
            obj.put("message", (state as? AuthState.Error)?.message ?: "Authentication failed")
        }
        return obj
    }

    /** Accepts either a raw authorization code or a full redirected URL containing code=. */
    private fun extractCode(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.contains("code=")) return trimmed
        return runCatching {
            android.net.Uri.parse(trimmed).getQueryParameter("code") ?: trimmed
        }.getOrDefault(trimmed).trim()
    }

    private fun readJson(session: IHTTPSession): JSONObject {
        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            val data = files["postData"] ?: "{}"
            JSONObject(data.ifBlank { "{}" })
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun jsonResponse(obj: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", obj.toString())

    companion object {
        private const val TAG = "TvAuthServer"
    }
}
