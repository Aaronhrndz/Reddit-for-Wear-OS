package com.reddit.wearos.service

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.reddit.wearos.RedditWearApp
import com.reddit.wearos.util.ApkSelfInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/reddit/auth_sync" -> {
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
                } catch (_: Exception) {
                }
            }
            "/reddit/version_request" -> {
                val ver = try {
                    packageManager.getPackageInfo(packageName, 0).versionName ?: "1.6.0"
                } catch (_: Exception) {
                    "1.6.0"
                }
                Wearable.getMessageClient(applicationContext).sendMessage(
                    messageEvent.sourceNodeId,
                    "/reddit/version_response",
                    ver.toByteArray(Charsets.UTF_8)
                )
            }
            "/reddit/install_update" -> {
                val apkUrl = String(messageEvent.data, Charsets.UTF_8).trim()
                if (apkUrl.startsWith("http")) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Descargando actualización en el reloj...", Toast.LENGTH_LONG).show()
                    }
                    serviceScope.launch {
                        ApkSelfInstaller.downloadAndInstallFromUrl(
                            context = applicationContext,
                            apkUrl = apkUrl,
                            onStatus = { status ->
                                Wearable.getMessageClient(applicationContext).sendMessage(
                                    messageEvent.sourceNodeId,
                                    "/reddit/update_status",
                                    status.toByteArray(Charsets.UTF_8)
                                )
                            }
                        )
                    }
                }
            }
            else -> super.onMessageReceived(messageEvent)
        }
    }
}
