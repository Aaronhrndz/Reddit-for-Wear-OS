package com.reddit.wearos.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val latestTag: String,
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val releaseHtmlUrl: String,
    val wearApkUrl: String?,
    val mobileApkUrl: String?
)

object UpdateChecker {
    private const val REPO_API_LATEST = "https://api.github.com/repos/Aaronhrndz/Reddit-for-Wear-OS/releases/latest"
    private const val REPO_API_ALL = "https://api.github.com/repos/Aaronhrndz/Reddit-for-Wear-OS/releases?per_page=1"
    private const val PREFS_NAME = "ota_prefs"
    private const val KEY_BETA_CHANNEL = "beta_channel_enabled"
    private val json = Json { ignoreUnknownKeys = true }

    fun isBetaChannel(context: android.content.Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_BETA_CHANNEL, false)
    }

    fun setBetaChannel(context: android.content.Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BETA_CHANNEL, enabled)
            .apply()
    }

    suspend fun checkLatestRelease(currentVersionName: String, isBeta: Boolean = false): GitHubReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val apiUrl = if (isBeta) REPO_API_ALL else REPO_API_LATEST
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RedForReddit-UpdateChecker")
                connectTimeout = 6000
                readTimeout = 6000
            }

            if (conn.responseCode != 200) return@withContext null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = if (isBeta) {
                val arr = json.parseToJsonElement(body).jsonArray
                if (arr.isNotEmpty()) arr[0].jsonObject else null
            } else {
                json.parseToJsonElement(body).jsonObject
            } ?: return@withContext null

            val tagName = root["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
            val htmlUrl = root["html_url"]?.jsonPrimitive?.content
                ?: "https://github.com/Aaronhrndz/Reddit-for-Wear-OS/releases/latest"
            val cleanLatest = tagName.removePrefix("v").trim()
            val cleanCurrent = currentVersionName.removePrefix("v").trim()

            var wearUrl: String? = null
            var mobileUrl: String? = null

            val assets = root["assets"]?.jsonArray
            assets?.forEach { elem ->
                val obj = elem.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val dl = obj["browser_download_url"]?.jsonPrimitive?.content ?: return@forEach
                if (name.contains("wear", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)) {
                    if (wearUrl == null || !name.contains("debug", ignoreCase = true)) {
                        wearUrl = dl
                    }
                } else if (name.contains("mobile", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true)) {
                    if (mobileUrl == null || !name.contains("debug", ignoreCase = true)) {
                        mobileUrl = dl
                    }
                }
            }

            GitHubReleaseInfo(
                latestTag = tagName,
                latestVersion = cleanLatest,
                isUpdateAvailable = isNewerVersion(cleanLatest, cleanCurrent),
                releaseHtmlUrl = htmlUrl,
                wearApkUrl = wearUrl,
                mobileApkUrl = mobileUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val lParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
