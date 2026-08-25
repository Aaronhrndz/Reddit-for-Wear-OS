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

    init {
        if (getAccessToken().isNullOrEmpty()) {
            saveTokens(
                accessToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IlNIQTI1NjpzS3dsMnlsV0VtMjVmcXhwTU40cWY4MXE2OWFFdWFyMnpLMUdhVGxjdWNZIiwidHlwIjoiSldUIn0.eyJzdWIiOiJ1c2VyIiwiZXhwIjoxNzg3NjkwNjU0Ljk1OTg2MiwiaWF0IjoxNzg3NjA0MjU0Ljk1OTg2MiwianRpIjoiZ0stclVtN3hkcGhlcXJZVXBDVmdXeVhrWTJuZ0J3IiwiY2lkIjoiMFItV0FNaHVvby1NeVEiLCJsaWQiOiJ0Ml84cDZzZXVodyIsImFpZCI6InQyXzhwNnNldWh3IiwiYXQiOjEsImxjYSI6MTYwNDE1NTc2MjAwMCwic2NwIjoiZUp4a2tkR090REFJaGQtRmE1X2dmNVVfbTAxdGNZYXNMUWFvazNuN0RWb2NrNzA3Y0Q0cEhQOURLb3FGRENaWGdxbkFCRmdUclREQlJ1VDluTG0zZzJpTmU4dFlzWm5DQkZtd0ZEcmttTEdzaVFRbWVKSWF5eHNtb0lMTnlGeXV0R05OTFQwUUpxaGNNcmVGSHBjMm9ia2JpNTZkR0ZXNXJEeW9zVmZsMHRqR0ZMWW54amNicXcycHVDNm5Na25MUXZrc1h2VGpOOVczOXZtel9TYTBKOE9LcXVtQjNobEpDRzRzZnBpbTNkOVRrNTZ0Q3hhMTkzcVEydWQ2M0s1OTFpdzBPN2VmNl9sckl4bVhZMmgtSnZ0MzF5LWhBNDg4THpQcUFFYXM0VWNaZG1RZF9sVUhVTG1nSkdNSjR0TUk1TXJsMjM4SnRtdlR2OGJ0RXo5OE0tS21OX3pXRE5SekNlTFFwX0gxR3dBQV9fOFExZVRSIiwicmNpZCI6IkxPSzR3N1RHYm5OMzRFSjRjS1hvLTVZXzFVSXQ3bXVzQzA0dzg1Vk9reDAiLCJmbG8iOjJ9.pTuJGhob9-4Q_GZ53uvzu4D0VwgkDVboxgBRmIDZU1HiKkqhJeUeU4dcLSwY_kvx8ApK_dztJpjU1Lz-Fz_hpgun0rUNopo99-FxtcYRpfn_8VslPD8XsQSD6JWctBOXXAb6daCTCGIklouKrEZcWj-UL8uKo-9H9qXMRD1G2YD5-8XFvUXjBzKZCc2s4WZaUAnKteoInlvt0KkL51GjfCTppnVEA5VBUEBa1bxSIRze2p_hyoZWXc7uRUAO0GCtJ18inpm375Q6bi3caLAyLn9e0h_V_Uisb55Jli6paKwaoqojdgmDw-6xVEd2Z4WcB7oWzR5XRByms_SBOMK5LA",
                refreshToken = null,
                expiresInSeconds = 86400L * 30L,
                username = "Aaronhrnndz"
            )
            setClientId("0R-WAMhuoo-MyQ")
        }
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
