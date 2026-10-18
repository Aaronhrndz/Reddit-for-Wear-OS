package com.reddit.wearos.companion

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
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
    private val capabilityClient = Wearable.getCapabilityClient(context)

    companion object {
        private const val TAG = "WatchSyncManager"
        const val AUTH_SYNC_PATH = "/reddit/auth_sync"
        const val INSTALL_UPDATE_PATH = "/reddit/install_update"
        const val VERSION_REQUEST_PATH = "/reddit/version_request"
        const val WEAR_CAPABILITY = "reddit_wear_app"
    }

    suspend fun getConnectedWatches(): List<Node> = withContext(Dispatchers.IO) {
        val foundNodes = mutableSetOf<Node>()

        // 1. Intentar por NodeClient connectedNodes
        try {
            val task = nodeClient.connectedNodes
            val nodes = Tasks.await(task, 7, TimeUnit.SECONDS)
            if (!nodes.isNullOrEmpty()) {
                Log.d(TAG, "NodeClient found ${nodes.size} connected nodes: ${nodes.map { "${it.displayName} (${it.id})" }}")
                foundNodes.addAll(nodes)
            } else {
                Log.w(TAG, "NodeClient.connectedNodes returned 0 nodes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in NodeClient.connectedNodes", e)
        }

        // 2. Si no encontró, intentar por CapabilityClient
        if (foundNodes.isEmpty()) {
            try {
                val capTask = capabilityClient.getCapability(WEAR_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                val capInfo = Tasks.await(capTask, 5, TimeUnit.SECONDS)
                if (capInfo != null && capInfo.nodes.isNotEmpty()) {
                    Log.d(TAG, "CapabilityClient found ${capInfo.nodes.size} nodes: ${capInfo.nodes.map { it.displayName }}")
                    foundNodes.addAll(capInfo.nodes)
                } else {
                    Log.w(TAG, "CapabilityClient.getCapability returned 0 nodes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in CapabilityClient.getCapability", e)
            }
        }

        foundNodes.toList()
    }

    suspend fun sendAuthToWatch(payload: AuthSyncPayload): Boolean = withContext(Dispatchers.IO) {
        val nodes = getConnectedWatches()
        if (nodes.isEmpty()) {
            Log.w(TAG, "sendAuthToWatch: No nodes available")
            return@withContext false
        }

        val jsonString = Json.encodeToString(payload)
        val data = jsonString.toByteArray(Charsets.UTF_8)

        var anySuccess = false
        for (node in nodes) {
            try {
                val task = messageClient.sendMessage(node.id, AUTH_SYNC_PATH, data)
                Tasks.await(task, 7, TimeUnit.SECONDS)
                Log.d(TAG, "Sent auth to node: ${node.displayName} (${node.id})")
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send auth to node: ${node.id}", e)
            }
        }
        anySuccess
    }

    suspend fun triggerWatchUpdate(wearApkUrl: String): Boolean = withContext(Dispatchers.IO) {
        val nodes = getConnectedWatches()
        if (nodes.isEmpty()) {
            Log.w(TAG, "triggerWatchUpdate: No connected watches found")
            return@withContext false
        }

        val data = wearApkUrl.toByteArray(Charsets.UTF_8)
        var anySuccess = false
        for (node in nodes) {
            try {
                Log.d(TAG, "Sending update order to node: ${node.displayName} (${node.id}) -> $wearApkUrl")
                val task = messageClient.sendMessage(node.id, INSTALL_UPDATE_PATH, data)
                Tasks.await(task, 10, TimeUnit.SECONDS)
                Log.d(TAG, "Update order delivered to: ${node.id}")
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending update order to: ${node.id}", e)
            }
        }
        anySuccess
    }

    suspend fun requestWatchVersion(): Boolean = withContext(Dispatchers.IO) {
        val nodes = getConnectedWatches()
        if (nodes.isEmpty()) {
            Log.w(TAG, "requestWatchVersion: No connected watches found")
            return@withContext false
        }

        var anySuccess = false
        for (node in nodes) {
            try {
                val task = messageClient.sendMessage(node.id, VERSION_REQUEST_PATH, ByteArray(0))
                Tasks.await(task, 7, TimeUnit.SECONDS)
                Log.d(TAG, "Requested version from: ${node.displayName} (${node.id})")
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed requesting version from: ${node.id}", e)
            }
        }
        anySuccess
    }
}
