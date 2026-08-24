package com.reddit.wearos.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TokenStorage(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "reddit_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("reddit_fallback_prefs", Context.MODE_PRIVATE)
    }

    private val _isLoggedIn = MutableStateFlow(!getAccessToken().isNullOrEmpty())
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

    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getTokenExpiry(): Long {
        return prefs.getLong(KEY_EXPIRES_AT, 0L)
    }

    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long, username: String? = null) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            if (!refreshToken.isNullOrEmpty()) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            if (!username.isNullOrEmpty()) {
                putString(KEY_USERNAME, username)
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
            remove(KEY_EXPIRES_AT)
            remove(KEY_USERNAME)
            apply()
        }
        _isLoggedIn.value = false
    }

    fun isTokenExpired(): Boolean {
        val expiresAt = getTokenExpiry()
        return System.currentTimeMillis() >= (expiresAt - 60_000L) // 1 min buffer
    }

    companion object {
        private const val KEY_CLIENT_ID = "custom_client_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USERNAME = "username"
    }
}
