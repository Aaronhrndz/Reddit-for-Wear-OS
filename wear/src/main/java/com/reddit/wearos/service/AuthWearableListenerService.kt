package com.reddit.wearos.service

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.reddit.wearos.RedditWearApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AuthSyncPayload(
    val clientId: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
    val username: String? = null
)

class AuthWearableListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/reddit/auth_sync") {
            try {
                val jsonString = String(messageEvent.data, Charsets.UTF_8)
                val payload = Json { ignoreUnknownKeys = true }.decodeFromString<AuthSyncPayload>(jsonString)

                val app = application as? RedditWearApp ?: return
                app.tokenStorage.setClientId(payload.clientId)
                app.tokenStorage.saveTokens(
                    accessToken = payload.accessToken,
                    refreshToken = payload.refreshToken,
                    expiresInSeconds = payload.expiresInSeconds,
                    username = payload.username
                )

                // Disparar recarga reactiva instantánea del feed
                app.repository.triggerFeedRefresh()

                Handler(Looper.getMainLooper()).post {
                    val nameStr = if (!payload.username.isNullOrEmpty()) " (${payload.username})" else ""
                    Toast.makeText(applicationContext, "Reddit conectado$nameStr", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // error handling
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}
