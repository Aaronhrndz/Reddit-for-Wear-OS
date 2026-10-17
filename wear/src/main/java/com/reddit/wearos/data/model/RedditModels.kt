package com.reddit.wearos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun parseCommentsFromListingElement(element: JsonElement): List<RedditComment> {
    val comments = mutableListOf<RedditComment>()
    try {
        if (element !is JsonObject) return emptyList()
        val data = element.jsonObject["data"]?.jsonObject ?: return emptyList()
        val children = data["children"]?.jsonArray ?: return emptyList()
        for (child in children) {
            try {
                val childObj = child.jsonObject
                val kind = childObj["kind"]?.jsonPrimitive?.content
                if (kind == "t1") {
                    val commentData = childObj["data"]
                    if (commentData != null) {
                        val comment = com.reddit.wearos.data.network.NetworkClient.json.decodeFromJsonElement<RedditComment>(commentData)
                        comments.add(comment)
                    }
                }
            } catch (_: Exception) {}
        }
    } catch (_: Exception) {}
    return comments
}

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token")
    val accessToken: String = "",
    @SerialName("token_type")
    val tokenType: String = "",
    @SerialName("expires_in")
    val expiresIn: Long = 3600L,
    val scope: String = "",
    @SerialName("refresh_token")
    val refreshToken: String? = null
)

@Serializable
data class RedditListing<T>(
    val kind: String = "Listing",
    val data: ListingData<T>
)

@Serializable
data class ListingData<T>(
    val after: String? = null,
    val before: String? = null,
    val dist: Int? = null,
    val children: List<ListingChild<T>> = emptyList()
)

@Serializable
data class ListingChild<T>(
    val kind: String = "t3",
    val data: T
)

@Serializable
data class RedditPost(
    val id: String = "",
    val name: String = "", // e.g. t3_xxxx
    val title: String = "",
    val author: String = "",
    val subreddit: String = "",
    @SerialName("subreddit_name_prefixed")
    val subredditPrefixed: String = "r/$subreddit",
    val score: Int = 0,
    @SerialName("num_comments")
    val numComments: Int = 0,
    @SerialName("selftext")
    val selfText: String = "",
    val url: String = "",
    val thumbnail: String? = null,
    @SerialName("created_utc")
    val createdUtc: Double = 0.0,
    @SerialName("is_self")
    val isSelf: Boolean = true,
    @SerialName("is_video")
    val isVideo: Boolean = false,
    @SerialName("is_gallery")
    val isGallery: Boolean = false,
    @SerialName("gallery_data")
    val galleryData: JsonElement? = null,
    @SerialName("media_metadata")
    val mediaMetadata: JsonElement? = null,
    val media: JsonElement? = null,
    val preview: JsonElement? = null,
    val likes: Boolean? = null,
    val saved: Boolean = false,
    val permalink: String = "",
    val over_18: Boolean = false
) {
    fun hasVideo(): Boolean {
        return isVideo || url.endsWith(".mp4") || url.endsWith(".gifv") || url.contains("v.redd.it") || url.contains("redgifs.com") || getVideoUrl() != null
    }

    fun getGalleryImageUrls(): List<String> {
        val result = mutableListOf<String>()
        if (mediaMetadata != null) {
            try {
                val metaObj = mediaMetadata.jsonObject
                val orderedIds = mutableListOf<String>()
                if (galleryData != null) {
                    val items = galleryData.jsonObject["items"]?.jsonArray
                    items?.forEach { item ->
                        item.jsonObject["media_id"]?.jsonPrimitive?.content?.let { orderedIds.add(it) }
                    }
                }
                val keysToProcess = if (orderedIds.isNotEmpty()) orderedIds else metaObj.keys.toList()
                for (mediaId in keysToProcess) {
                    val entry = metaObj[mediaId]?.jsonObject ?: continue
                    val previews = entry["p"]?.jsonArray
                    var chosenUrl: String? = null
                    if (!previews.isNullOrEmpty()) {
                        val target = previews.find {
                            val w = it.jsonObject["x"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            w in 320..960
                        } ?: previews.last()
                        chosenUrl = target.jsonObject["u"]?.jsonPrimitive?.content
                    }
                    if (chosenUrl.isNullOrEmpty()) {
                        val source = entry["s"]?.jsonObject
                        chosenUrl = source?.get("u")?.jsonPrimitive?.content
                            ?: source?.get("gif")?.jsonPrimitive?.content
                    }
                    if (!chosenUrl.isNullOrEmpty()) {
                        result.add(chosenUrl.replace("&amp;", "&"))
                    }
                }
            } catch (_: Exception) {}
        }
        if (result.isEmpty()) {
            getImageUrl()?.let { result.add(it) }
        }
        return result.distinct()
    }

    fun getVideoUrl(): String? = getVideoUrls().firstOrNull()

    /**
     * URLs de vídeo candidatas en orden de preferencia:
     * 1. HLS oficial (mismo stream que la app de Reddit, audio muxed CMAF)
     * 2. MP4 progresivo de fallback (solo vídeo, sin audio)
     */
    fun getVideoUrls(): List<String> {
        val candidates = mutableListOf<String>()
        if (url.endsWith(".mp4")) candidates.add(url)
        if (url.endsWith(".gifv")) candidates.add(url.replace(".gifv", ".mp4"))
        if (media != null) {
            try {
                val redditVideo = media.jsonObject["reddit_video"]?.jsonObject
                redditVideo?.get("hls_url")?.jsonPrimitive?.content?.let { hls ->
                    if (hls.isNotEmpty()) candidates.add(hls.replace("&amp;", "&"))
                }
                redditVideo?.get("fallback_url")?.jsonPrimitive?.content?.let { fb ->
                    if (fb.isNotEmpty()) candidates.add(fb.replace("&amp;", "&"))
                }
            } catch (_: Exception) {}
        }
        if (preview != null && candidates.isEmpty()) {
            try {
                val redditVideoPreview = preview.jsonObject["reddit_video_preview"]?.jsonObject
                redditVideoPreview?.get("hls_url")?.jsonPrimitive?.content?.let { hls ->
                    if (hls.isNotEmpty()) candidates.add(hls.replace("&amp;", "&"))
                }
                redditVideoPreview?.get("fallback_url")?.jsonPrimitive?.content?.let { fb ->
                    if (fb.isNotEmpty()) candidates.add(fb.replace("&amp;", "&"))
                }
            } catch (_: Exception) {}
        }
        if (candidates.isEmpty() && url.contains("v.redd.it")) {
            val clean = url.substringBefore("?")
            candidates.add("$clean/HLSPlaylist.m3u8")
            candidates.add("$clean/DASH_480.mp4")
        }
        return candidates.distinct()
    }

    fun getImageUrl(): String? {
        val lower = url.lowercase()
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")) {
            return url
        }
        if (url.contains("i.redd.it") || url.contains("i.imgur.com")) {
            return url
        }
        if (preview != null) {
            try {
                val images = preview.jsonObject["images"]?.jsonArray
                if (!images.isNullOrEmpty()) {
                    val firstImg = images[0].jsonObject
                    val sourceUrl = firstImg["source"]?.jsonObject?.get("url")?.jsonPrimitive?.content
                    if (!sourceUrl.isNullOrEmpty()) {
                        return sourceUrl.replace("&amp;", "&")
                    }
                }
            } catch (_: Exception) {}
        }
        if (mediaMetadata != null) {
            try {
                val metaObj = mediaMetadata.jsonObject
                val firstEntry = metaObj.values.firstOrNull()?.jsonObject
                val sourceUrl = firstEntry?.get("s")?.jsonObject?.get("u")?.jsonPrimitive?.content
                if (!sourceUrl.isNullOrEmpty()) {
                    return sourceUrl.replace("&amp;", "&")
                }
            } catch (_: Exception) {}
        }
        if (!thumbnail.isNullOrEmpty() && thumbnail.startsWith("http") && !thumbnail.contains("default") && !thumbnail.contains("self")) {
            return thumbnail
        }
        return null
    }

    fun getThumbnailUrl(): String? {
        if (preview != null) {
            try {
                val images = preview.jsonObject["images"]?.jsonArray
                if (!images.isNullOrEmpty()) {
                    val firstImg = images[0].jsonObject
                    val resolutions = firstImg["resolutions"]?.jsonArray
                    if (!resolutions.isNullOrEmpty()) {
                        val target = resolutions.find {
                            val w = it.jsonObject["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                            w in 240..480
                        } ?: resolutions.last()
                        val resUrl = target.jsonObject["url"]?.jsonPrimitive?.content
                        if (!resUrl.isNullOrEmpty()) {
                            return resUrl.replace("&amp;", "&")
                        }
                    }
                    val sourceUrl = firstImg["source"]?.jsonObject?.get("url")?.jsonPrimitive?.content
                    if (!sourceUrl.isNullOrEmpty()) {
                        return sourceUrl.replace("&amp;", "&")
                    }
                }
            } catch (_: Exception) {}
        }
        if (mediaMetadata != null) {
            try {
                val urls = getGalleryImageUrls()
                if (urls.isNotEmpty()) return urls.first()
            } catch (_: Exception) {}
        }
        return getImageUrl()
    }
}

@Serializable
data class RedditComment(
    val id: String = "",
    val author: String = "[deleted]",
    val body: String = "",
    val score: Int = 0,
    @SerialName("created_utc")
    val createdUtc: Double = 0.0,
    val likes: Boolean? = null,
    val depth: Int = 0,
    val replies: JsonElement? = null
)

@Serializable
data class RedditSubreddit(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
    val title: String = "",
    val subscribers: Long = 0,
    @SerialName("icon_img")
    val iconImg: String? = null,
    @SerialName("community_icon")
    val communityIcon: String? = null,
    @SerialName("user_is_subscriber")
    val userIsSubscriber: Boolean = false
)

@Serializable
data class RedditUser(
    val id: String = "",
    val name: String = "",
    @SerialName("icon_img")
    val iconImg: String? = null,
    @SerialName("total_karma")
    val totalKarma: Int = 0,
    @SerialName("link_karma")
    val linkKarma: Int = 0,
    @SerialName("comment_karma")
    val commentKarma: Int = 0,
    @SerialName("created_utc")
    val createdUtc: Double = 0.0
)

@Serializable
data class RedditMessage(
    val id: String = "",
    val name: String = "",
    val author: String = "[desconocido]",
    val body: String = "",
    val subject: String = "",
    val subreddit: String? = null,
    @SerialName("created_utc")
    val createdUtc: Double = 0.0,
    val new: Boolean = false,
    @SerialName("was_comment")
    val wasComment: Boolean = false,
    @SerialName("parent_id")
    val parentId: String? = null,
    val context: String = ""
)

fun formatRelativeTime(createdUtc: Double): String {
    if (createdUtc <= 0.0) return ""
    val nowSec = System.currentTimeMillis() / 1000L
    val diffSec = (nowSec - createdUtc.toLong()).coerceAtLeast(0L)
    val lang = java.util.Locale.getDefault().language.lowercase()

    return when (lang) {
        "es" -> when {
            diffSec < 60L -> "ahora"
            diffSec < 3600L -> "hace ${diffSec / 60L}m"
            diffSec < 86400L -> "hace ${diffSec / 3600L}h"
            diffSec < 604800L -> "hace ${diffSec / 86400L}d"
            diffSec < 2592000L -> "hace ${diffSec / 604800L}sem"
            diffSec < 31536000L -> "hace ${diffSec / 2592000L}mes"
            else -> "hace ${diffSec / 31536000L}a"
        }
        "fr" -> when {
            diffSec < 60L -> "maintenant"
            diffSec < 3600L -> "il y a ${diffSec / 60L}m"
            diffSec < 86400L -> "il y a ${diffSec / 3600L}h"
            diffSec < 604800L -> "il y a ${diffSec / 86400L}j"
            diffSec < 2592000L -> "il y a ${diffSec / 604800L}sem"
            diffSec < 31536000L -> "il y a ${diffSec / 2592000L}m"
            else -> "il y a ${diffSec / 31536000L}a"
        }
        "de" -> when {
            diffSec < 60L -> "jetzt"
            diffSec < 3600L -> "vor ${diffSec / 60L}m"
            diffSec < 86400L -> "vor ${diffSec / 3600L}h"
            diffSec < 604800L -> "vor ${diffSec / 86400L}t"
            diffSec < 2592000L -> "vor ${diffSec / 604800L}w"
            diffSec < 31536000L -> "vor ${diffSec / 2592000L}m"
            else -> "vor ${diffSec / 31536000L}j"
        }
        "pt" -> when {
            diffSec < 60L -> "agora"
            diffSec < 3600L -> "há ${diffSec / 60L}m"
            diffSec < 86400L -> "há ${diffSec / 3600L}h"
            diffSec < 604800L -> "há ${diffSec / 86400L}d"
            diffSec < 2592000L -> "há ${diffSec / 604800L}sem"
            diffSec < 31536000L -> "há ${diffSec / 2592000L}m"
            else -> "há ${diffSec / 31536000L}a"
        }
        "it" -> when {
            diffSec < 60L -> "adesso"
            diffSec < 3600L -> "${diffSec / 60L}m fa"
            diffSec < 86400L -> "${diffSec / 3600L}h fa"
            diffSec < 604800L -> "${diffSec / 86400L}g fa"
            diffSec < 2592000L -> "${diffSec / 604800L}sett fa"
            diffSec < 31536000L -> "${diffSec / 2592000L}m fa"
            else -> "${diffSec / 31536000L}a fa"
        }
        else -> when {
            diffSec < 60L -> "just now"
            diffSec < 3600L -> "${diffSec / 60L}m ago"
            diffSec < 86400L -> "${diffSec / 3600L}h ago"
            diffSec < 604800L -> "${diffSec / 86400L}d ago"
            diffSec < 2592000L -> "${diffSec / 604800L}w ago"
            diffSec < 31536000L -> "${diffSec / 2592000L}mo ago"
            else -> "${diffSec / 31536000L}y ago"
        }
    }
}

