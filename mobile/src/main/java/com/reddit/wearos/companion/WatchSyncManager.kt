package com.reddit.wearos.companion

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@Serializable
data class AuthSyncPayload(
    val clientId: String,
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long,
    val username: String? = null
)

class WatchSyncManager(private val context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    companion object {
        const val AUTH_SYNC_PATH = "/reddit/auth_sync"
        const val INSTALL_UPDATE_PATH = "/reddit/install_update"
        const val VERSION_REQUEST_PATH = "/reddit/version_request"
    }

    suspend fun getConnectedWatches(): List<Node> = withContext(Dispatchers.IO) {
        try {
            val task = nodeClient.connectedNodes
            Tasks.await(task, 5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun sendAuthToWatch(payload: AuthSyncPayload): Boolean = withContext(Dispatchers.IO) {
        val nodes = getConnectedWatches()
        if (nodes.isEmpty()) return@withContext false

        val jsonString = Json.encodeToString(payload)
        val data = jsonString.toByteArray(Charsets.UTF_8)

        var anySuccess = false
        for (node in nodes) {
            try {
                val task = messageClient.sendMessage(node.id, AUTH_SYNC_PATH, data)
                Tasks.await(task, 5, TimeUnit.SECONDS)
                anySuccess = true
            } catch (_: Exception) {
            }
        }
        anySuccess
    }

    suspend fun triggerWatchUpdate(wearApkUrl: String): Boolean = withContext(Dispatchers.IO) {
        val nodes = getConnectedWatches()
        if (nodes.isEmpty()) return@withContext false

        val data = wearApkUrl.toByteArray(Charsets.UTF_8)
        var anySuccess = false
        for (node in nodes) {
            try {
                val task = messageClient.sendMessage(node.id, INSTALL_UPDATE_PATH, data)
                Tasks.await(task, 5, TimeUnit.SECONDS)
                anySuccess = true
            } catch (_: Exception) {
            }
        }
        anySuccess
    }

    suspend fun requestWatchVersion(): Boolean = withContext(Dispatchers.IO) {
        val nodes = getConnectedWatches()
        if (nodes.isEmpty()) return@withContext false

        var anySuccess = false
        for (node in nodes) {
            try {
                val task = messageClient.sendMessage(node.id, VERSION_REQUEST_PATH, ByteArray(0))
                Tasks.await(task, 5, TimeUnit.SECONDS)
                anySuccess = true
            } catch (_: Exception) {
            }
        }
        anySuccess
    }
}
