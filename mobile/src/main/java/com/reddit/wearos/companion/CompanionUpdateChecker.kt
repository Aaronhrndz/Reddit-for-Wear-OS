package com.reddit.wearos.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

data class CompanionReleaseInfo(
    val latestTag: String,
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val releaseHtmlUrl: String,
    val wearApkUrl: String?,
    val mobileApkUrl: String?
)

object CompanionUpdateChecker {
    private const val REPO_API = "https://api.github.com/repos/Aaronhrndz/Reddit-for-Wear-OS/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkLatestRelease(currentVersionName: String): CompanionReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(REPO_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RedForReddit-CompanionUpdateChecker")
                connectTimeout = 6000
                readTimeout = 6000
            }

            if (conn.responseCode != 200) return@withContext null

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(body).jsonObject

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
                val dl = obj["browser_download_url"]?.jsonPrimitive?.content
                if (name.contains("WearOS", ignoreCase = true) && name.endsWith(".apk")) {
                    wearUrl = dl
                } else if (name.contains("Mobile", ignoreCase = true) && name.endsWith(".apk")) {
                    mobileUrl = dl
                }
            }

            CompanionReleaseInfo(
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

    fun isNewerVersion(remote: String, local: String): Boolean {
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
