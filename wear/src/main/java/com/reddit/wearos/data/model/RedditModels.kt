package com.reddit.wearos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
                val fallbackUrl = redditVideo?.get("fallback_url")?.jsonPrimitive?.content
                if (!fallbackUrl.isNullOrEmpty()) return fallbackUrl
            } catch (_: Exception) {}
        }
        if (preview != null) {
            try {
                val redditVideoPreview = preview.jsonObject["reddit_video_preview"]?.jsonObject
                val fallbackUrl = redditVideoPreview?.get("fallback_url")?.jsonPrimitive?.content
                if (!fallbackUrl.isNullOrEmpty()) return fallbackUrl
            } catch (_: Exception) {}
        }
        if (url.contains("v.redd.it")) {
            val clean = url.substringBefore("?")
            return "$clean/DASH_480.mp4"
        }
        return null
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
