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
    val kind: String,
    val data: T
)

@Serializable
data class RedditPost(
    val id: String,
    val name: String = "", // e.g. t3_xxxx
    val title: String,
    val author: String,
    val subreddit: String,
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

    fun getVideoUrl(): String? {
        if (url.endsWith(".mp4")) return url
        if (url.endsWith(".gifv")) return url.replace(".gifv", ".mp4")
        if (media != null) {
            try {
                val redditVideo = media.jsonObject["reddit_video"]?.jsonObject
                val hlsUrl = redditVideo?.get("hls_url")?.jsonPrimitive?.content
                if (!hlsUrl.isNullOrEmpty()) return hlsUrl.replace("&amp;", "&")
                val fallbackUrl = redditVideo?.get("fallback_url")?.jsonPrimitive?.content
                if (!fallbackUrl.isNullOrEmpty()) return fallbackUrl.replace("&amp;", "&")
            } catch (_: Exception) {}
        }
        if (preview != null) {
            try {
                val redditVideoPreview = preview.jsonObject["reddit_video_preview"]?.jsonObject
                val hlsUrl = redditVideoPreview?.get("hls_url")?.jsonPrimitive?.content
                if (!hlsUrl.isNullOrEmpty()) return hlsUrl.replace("&amp;", "&")
                val fallbackUrl = redditVideoPreview?.get("fallback_url")?.jsonPrimitive?.content
                if (!fallbackUrl.isNullOrEmpty()) return fallbackUrl.replace("&amp;", "&")
            } catch (_: Exception) {}
        }
        if (url.contains("v.redd.it")) {
            val clean = url.substringBefore("?")
            return "$clean/HLSPlaylist.m3u8"
        }
        return null
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
