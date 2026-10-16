package com.reddit.wearos.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class TokenStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("reddit_wear_auth_prefs", Context.MODE_PRIVATE)

    private val _isLoggedIn = MutableStateFlow(!getAccessToken().isNullOrEmpty() || !getSessionCookie().isNullOrEmpty())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    fun getClientId(): String {
        return prefs.getString(KEY_CLIENT_ID, "") ?: ""
    }

    fun setClientId(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun setUsername(username: String?) {
        if (!username.isNullOrEmpty()) {
            prefs.edit().putString(KEY_USERNAME, username.trim()).apply()
        }
    }

    fun getModhash(): String? {
        return prefs.getString(KEY_MODHASH, null)
    }

    fun setModhash(modhash: String?) {
        if (!modhash.isNullOrEmpty()) {
            prefs.edit().putString(KEY_MODHASH, modhash.trim()).apply()
        }
    }

    fun getSessionCookie(): String? {
        val explicit = prefs.getString(KEY_SESSION_COOKIE, null)
        if (!explicit.isNullOrEmpty()) return explicit

        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (!refresh.isNullOrEmpty() && refresh.contains("reddit_session=")) {
            return extractCookieHeader(refresh)
        }

        val access = prefs.getString(KEY_ACCESS_TOKEN, null)
        if (!access.isNullOrEmpty() && access.contains("reddit_session=")) {
            return extractCookieHeader(access)
        }
        return null
    }

    fun setSessionCookie(cookie: String?) {
        if (!cookie.isNullOrEmpty()) {
            prefs.edit().putString(KEY_SESSION_COOKIE, extractCookieHeader(cookie)).apply()
            _isLoggedIn.value = true
        }
    }

    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null) ?: getSessionCookie()
    }

    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getTokenExpiry(): Long {
        return prefs.getLong(KEY_EXPIRES_AT, 0L)
    }

    private fun extractCookieHeader(raw: String): String {
        val parts = mutableListOf<String>()
        Regex("""reddit_session=([^;\s]+)""").find(raw)?.let {
            parts.add("reddit_session=${it.groupValues[1].trim()}")
        }
        Regex("""loid=([^;\s]+)""").find(raw)?.let {
            parts.add("loid=${it.groupValues[1].trim()}")
        }
        return if (parts.isNotEmpty()) parts.joinToString("; ") else raw.trim()
    }

    private fun extractJwtExpiryMillis(jwt: String): Long? {
        return try {
            val clean = jwt.removePrefix("Bearer ").trim()
            val segments = clean.split(".")
            if (segments.size == 3) {
                val payloadJson = String(Base64.decode(segments[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
                val obj = JSONObject(payloadJson)
                if (obj.has("exp")) {
                    val expSec = obj.getLong("exp")
                    if (expSec > 0L) expSec * 1000L else null
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long, username: String? = null) {
        val combinedSource = listOfNotNull(accessToken, refreshToken).joinToString("; ")
        val sessionMatch = Regex("""reddit_session=([^;\s]+)""").find(combinedSource)
        val tokenV2Match = Regex("""token_v2=([^;\s]+)""").find(combinedSource)

        val effectiveAccess: String
        val expiresAt: Long

        if (sessionMatch != null) {
            effectiveAccess = extractCookieHeader(combinedSource)
            expiresAt = extractJwtExpiryMillis(sessionMatch.groupValues[1].trim())
                ?: (System.currentTimeMillis() + (365L * 24L * 3600L * 1000L))
        } else if (tokenV2Match != null) {
            val jwt = tokenV2Match.groupValues[1].trim()
            effectiveAccess = jwt
            expiresAt = extractJwtExpiryMillis(jwt) ?: (System.currentTimeMillis() + 3600_000L)
        } else if (accessToken.trim().startsWith("eyJ") && !accessToken.contains("=")) {
            val jwt = accessToken.trim()
            effectiveAccess = jwt
            expiresAt = extractJwtExpiryMillis(jwt) ?: (System.currentTimeMillis() + 3600_000L)
        } else {
            effectiveAccess = accessToken.trim()
            expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        }

        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, effectiveAccess)
            if (sessionMatch != null) {
                val cleanSession = extractCookieHeader(combinedSource)
                putString(KEY_SESSION_COOKIE, cleanSession)
                putString(KEY_REFRESH_TOKEN, cleanSession)
            } else if (!refreshToken.isNullOrEmpty()) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            if (!username.isNullOrEmpty()) {
                putString(KEY_USERNAME, username.trim())
            }
            putLong(KEY_EXPIRES_AT, expiresAt)
            apply()
        }
        _isLoggedIn.value = true
    }

    fun clearTokens() {
        prefs.edit().apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_SESSION_COOKIE)
            remove(KEY_MODHASH)
            remove(KEY_EXPIRES_AT)
            remove(KEY_USERNAME)
            apply()
        }
        _isLoggedIn.value = false
    }

    fun isTokenExpired(): Boolean {
        val access = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return true
        // Si estamos usando directamente reddit_session como access token, dura 1 año
        if (access.startsWith("reddit_session=")) return false
        // Si es un JWT (token_v2), comprobamos su exp real
        if (access.startsWith("eyJ")) {
            val jwtExp = extractJwtExpiryMillis(access)
            if (jwtExp != null) {
                return System.currentTimeMillis() >= (jwtExp - 120_000L)
            }
        }
        val expiresAt = getTokenExpiry()
        return System.currentTimeMillis() >= (expiresAt - 60_000L)
    }

    companion object {
        private const val KEY_CLIENT_ID = "custom_client_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_SESSION_COOKIE = "session_cookie"
        private const val KEY_MODHASH = "modhash"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USERNAME = "username"
    }
}

