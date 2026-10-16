package com.reddit.wearos.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.reddit.wearos.data.model.OAuthTokenResponse
import com.reddit.wearos.data.model.RedditUser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

class RedditAuthManager(
    private val context: Context,
    private val tokenStorage: TokenStorage,
    private val httpClient: HttpClient
) {

    private var currentCodeVerifier: String? = null
    private var currentState: String? = null

    companion object {
        const val TAG = "RedditAuthManager"
        const val DEFAULT_CLIENT_ID = "pA-pE4qfVj5W_A"
        const val REDIRECT_URI = "http://localhost:8080"
        const val AUTH_URL = "https://www.reddit.com/api/v1/authorize"
        const val TOKEN_URL = "https://www.reddit.com/api/v1/access_token"
        const val SCOPES = "identity read mysubreddits vote save history"
        const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
    }

    private fun generateRandomString(length: Int = 32): String {
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes, 0, bytes.size)
        val digest = md.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun buildAuthorizationUrl(clientId: String = tokenStorage.getClientId()): String {
        val verifier = generateRandomString(32)
        val state = generateRandomString(16)
        currentCodeVerifier = verifier
        currentState = state
        val challenge = generateCodeChallenge(verifier)

        val uriBuilder = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId.ifEmpty { DEFAULT_CLIENT_ID })
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", state)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("duration", "permanent")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")

        return uriBuilder.build().toString()
    }

    suspend fun exchangeCodeForToken(clientId: String, code: String, redirectUri: String = REDIRECT_URI) = withContext(Dispatchers.IO) {
        val verifier = currentCodeVerifier ?: ""
        val effectiveClientId = clientId.ifEmpty { DEFAULT_CLIENT_ID }
        val credentials = Base64.encodeToString("$effectiveClientId:".toByteArray(), Base64.NO_WRAP)

        val response: OAuthTokenResponse = httpClient.submitForm(
            url = TOKEN_URL,
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", redirectUri)
                append("code_verifier", verifier)
            }
        ) {
            header("Authorization", "Basic $credentials")
            header("User-Agent", "android:com.reddit.wearos:v1.0.0 (by /u/wear_reddit_user)")
        }.body()

        tokenStorage.saveTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresInSeconds = response.expiresIn
        )

        try {
            val me: RedditUser = httpClient.get("https://oauth.reddit.com/api/v1/me") {
                header("Authorization", "Bearer ${response.accessToken}")
                header("User-Agent", "android:com.reddit.wearos:v1.0.0 (by /u/wear_reddit_user)")
            }.body()
            if (me.name.isNotEmpty()) {
                tokenStorage.setUsername(me.name)
            }
        } catch (_: Exception) {}
    }

    suspend fun refreshTokenIfNeeded(): String? = withContext(Dispatchers.IO) {
        val currentToken = tokenStorage.getAccessToken()
        if (currentToken != null && !tokenStorage.isTokenExpired()) {
            return@withContext currentToken
        }
        return@withContext forceRefreshToken() ?: tokenStorage.getSessionCookie() ?: currentToken
    }

    suspend fun forceRefreshToken(): String? = withContext(Dispatchers.IO) {
        val sessionCookie = tokenStorage.getSessionCookie()
        val refreshToken = tokenStorage.getRefreshToken()

        // 1. Si tenemos un refresh_token OAuth real (que no sea cookie de sesión)
        if (!refreshToken.isNullOrEmpty() && !refreshToken.contains("reddit_session=")) {
            val clientId = tokenStorage.getClientId().takeIf { it.isNotEmpty() && it != "cookie_session" } ?: DEFAULT_CLIENT_ID
            val credentials = Base64.encodeToString("$clientId:".toByteArray(), Base64.NO_WRAP)
            try {
                val response: OAuthTokenResponse = httpClient.submitForm(
                    url = TOKEN_URL,
                    formParameters = Parameters.build {
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                    }
                ) {
                    header("Authorization", "Basic $credentials")
                    header("User-Agent", "android:com.reddit.wearos:v1.4.0 (by /u/wear_reddit_user)")
                }.body()

                if (response.accessToken.isNotEmpty()) {
                    tokenStorage.saveTokens(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken ?: refreshToken,
                        expiresInSeconds = response.expiresIn
                    )
                    return@withContext response.accessToken
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al refrescar OAuth token: ${e.message}")
            }
        }

        // 2. Si tenemos cookie persistente reddit_session (dura 1 año), renovamos autónomamente en el reloj
        if (!sessionCookie.isNullOrEmpty()) {
            return@withContext refreshFromSessionCookie(sessionCookie)
        }

        return@withContext null
    }

    private suspend fun refreshFromSessionCookie(sessionCookie: String): String = withContext(Dispatchers.IO) {
        var latestSessionCookie = sessionCookie
        var newTokenV2: String? = null

        // A. Actualizar modhash y usuario desde old.reddit.com/api/me.json y revisar Set-Cookie
        try {
            val meResp = httpClient.get("https://old.reddit.com/api/me.json") {
                header("Cookie", latestSessionCookie)
                header("User-Agent", BROWSER_UA)
            }
            val setCookies = meResp.headers.getAll("Set-Cookie") ?: emptyList()
            for (sc in setCookies) {
                Regex("""reddit_session=([^;\s]+)""").find(sc)?.let {
                    val v = it.groupValues[1].trim()
                    if (v.isNotEmpty() && v != "deleted") {
                        latestSessionCookie = "reddit_session=$v"
                    }
                }
                Regex("""token_v2=([^;\s]+)""").find(sc)?.let {
                    val v = it.groupValues[1].trim()
                    if (v.length > 50) newTokenV2 = v
                }
            }

            if (meResp.status.value == 200) {
                val bodyStr = meResp.bodyAsText()
                if (bodyStr.trim().startsWith("{")) {
                    val dataObj = JSONObject(bodyStr).optJSONObject("data")
                    if (dataObj != null) {
                        val uh = dataObj.optString("modhash", "")
                        if (uh.isNotEmpty()) {
                            tokenStorage.setModhash(uh)
                        }
                        val name = dataObj.optString("name", "")
                        if (name.isNotEmpty()) {
                            tokenStorage.setUsername(name)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error consultando old.reddit.com/api/me.json: ${e.message}")
        }

        // B. Intentar obtener un nuevo token_v2 desde www.reddit.com enviando reddit_session
        if (newTokenV2 == null) {
            try {
                val webResp = httpClient.get("https://www.reddit.com/") {
                    header("Cookie", latestSessionCookie)
                    header("User-Agent", BROWSER_UA)
                }
                val setCookies = webResp.headers.getAll("Set-Cookie") ?: emptyList()
                for (sc in setCookies) {
                    Regex("""reddit_session=([^;\s]+)""").find(sc)?.let {
                        val v = it.groupValues[1].trim()
                        if (v.isNotEmpty() && v != "deleted") {
                            latestSessionCookie = "reddit_session=$v"
                        }
                    }
                    Regex("""token_v2=([^;\s]+)""").find(sc)?.let {
                        val v = it.groupValues[1].trim()
                        if (v.length > 50) newTokenV2 = v
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error renovando token_v2 en www.reddit.com: ${e.message}")
            }
        }

        tokenStorage.setSessionCookie(latestSessionCookie)

        if (!newTokenV2.isNullOrEmpty()) {
            Log.d(TAG, "✅ token_v2 renovado autónomamente desde reddit_session en el reloj")
            tokenStorage.saveTokens(
                accessToken = newTokenV2!!,
                refreshToken = latestSessionCookie,
                expiresInSeconds = 3600L
            )
            return@withContext newTokenV2!!
        } else {
            Log.d(TAG, "✅ Usando reddit_session persistente directamente (válida 1 año)")
            tokenStorage.saveTokens(
                accessToken = latestSessionCookie,
                refreshToken = latestSessionCookie,
                expiresInSeconds = 31536000L
            )
            return@withContext latestSessionCookie
        }
    }
}

