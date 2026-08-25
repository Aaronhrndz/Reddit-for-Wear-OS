package com.reddit.wearos.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.reddit.wearos.data.model.OAuthTokenResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

class RedditAuthManager(
    private val context: Context,
    private val tokenStorage: TokenStorage,
    private val httpClient: HttpClient
) {

    private var currentCodeVerifier: String? = null
    private var currentState: String? = null

    // Reddit OAuth constants
    companion object {
        const val DEFAULT_CLIENT_ID = "pA-pE4qfVj5W_A" // Open source client ID
        const val REDIRECT_URI = "http://localhost:8080"
        const val AUTH_URL = "https://www.reddit.com/api/v1/authorize"
        const val TOKEN_URL = "https://www.reddit.com/api/v1/access_token"
        const val SCOPES = "identity read mysubreddits vote save history"
    }

    init {
        if (tokenStorage.getClientId().isEmpty()) {
            tokenStorage.setClientId(DEFAULT_CLIENT_ID)
        }
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
    }

    suspend fun refreshTokenIfNeeded(): String? = withContext(Dispatchers.IO) {
        val currentToken = tokenStorage.getAccessToken() ?: return@withContext null
        if (!tokenStorage.isTokenExpired()) {
            return@withContext currentToken
        }

        val refreshToken = tokenStorage.getRefreshToken() ?: return@withContext null
        val clientId = tokenStorage.getClientId().ifEmpty { DEFAULT_CLIENT_ID }
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
                header("User-Agent", "android:com.reddit.wearos:v1.0.0 (by /u/wear_reddit_user)")
            }.body()

            tokenStorage.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: refreshToken,
                expiresInSeconds = response.expiresIn
            )
            return@withContext response.accessToken
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun forceRefreshToken(): String? = withContext(Dispatchers.IO) {
        val refreshToken = tokenStorage.getRefreshToken() ?: return@withContext null
        val clientId = tokenStorage.getClientId().ifEmpty { DEFAULT_CLIENT_ID }
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
                header("User-Agent", "android:com.reddit.wearos:v1.0.0 (by /u/wear_reddit_user)")
            }.body()

            tokenStorage.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken ?: refreshToken,
                expiresInSeconds = response.expiresIn
            )
            return@withContext response.accessToken
        } catch (e: Exception) {
            return@withContext null
        }
    }
}
