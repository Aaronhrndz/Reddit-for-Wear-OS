package com.reddit.wearos.data.network

import android.util.Log
import com.reddit.wearos.auth.RedditAuthManager
import com.reddit.wearos.auth.TokenStorage
import com.reddit.wearos.data.model.ListingChild
import com.reddit.wearos.data.model.ListingData
import com.reddit.wearos.data.model.RedditComment
import com.reddit.wearos.data.model.RedditListing
import com.reddit.wearos.data.model.RedditMessage
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.data.model.RedditSubreddit
import com.reddit.wearos.data.model.RedditUser
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

class RedditApiService(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val authManager: RedditAuthManager
) {

    companion object {
        const val TAG = "RedditApiService"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
    }

    private suspend fun getAuthHeader(): Pair<String, String>? {
        val token = authManager.refreshTokenIfNeeded() ?: tokenStorage.getAccessToken() ?: return null
        return if (token.startsWith("Bearer ") || (token.length > 50 && !token.contains("=") && !token.contains(";"))) {
            val clean = if (token.startsWith("Bearer ")) token else "Bearer $token"
            Pair("Authorization", clean)
        } else {
            val cookieVal = if (!token.contains("=")) "reddit_session=$token" else token
            Pair("Cookie", cookieVal)
        }
    }

    private suspend fun isBearerAuth(): Boolean {
        val token = tokenStorage.getAccessToken() ?: return false
        return token.startsWith("Bearer ") || (token.length > 50 && !token.contains("=") && !token.contains(";"))
    }

    private var cachedSubscribedSubs: List<String>? = null

    suspend fun getFeed(
        subreddit: String? = null,
        sort: String = "hot",
        after: String? = null,
        limit: Int = 25
    ): RedditListing<RedditPost> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val sub = subreddit?.trim()
        val isHome = sub.isNullOrEmpty() || sub.equals("home", ignoreCase = true) || sub.equals("principal", ignoreCase = true) || sub.equals("frontpage", ignoreCase = true)

        val urlsToTry = mutableListOf<String>()
        val sortPath = if (sort.isBlank()) "hot" else sort.lowercase()
        if (isBearer) {
            if (isHome) {
                // En la API OAuth de Reddit, el feed personal se consulta directamente en /hot, /new, /rising, /top
                urlsToTry.add("https://oauth.reddit.com/$sortPath")
                if (sortPath == "hot") {
                    urlsToTry.add("https://oauth.reddit.com/best")
                }
            } else {
                urlsToTry.add("https://oauth.reddit.com/r/$sub/$sortPath")
            }
        }
        if (isHome) {
            urlsToTry.add("https://old.reddit.com/$sortPath/.json")
            urlsToTry.add("https://old.reddit.com/.json")
            urlsToTry.add("https://www.reddit.com/$sortPath/.json")
            urlsToTry.add("https://www.reddit.com/.json")
        } else {
            urlsToTry.add("https://old.reddit.com/r/$sub/$sortPath/.json")
            urlsToTry.add("https://old.reddit.com/r/$sub/.json")
            urlsToTry.add("https://www.reddit.com/r/$sub/$sortPath/.json")
            urlsToTry.add("https://www.reddit.com/r/$sub/.json")
        }

        var currentAuthHeader = authHeader

        for (url in urlsToTry) {
            try {
                Log.d(TAG, "Intentando feed: url=$url, sort=$sortPath, authHeader=${currentAuthHeader?.first} -> ${currentAuthHeader?.second?.take(15)}...")
                var response = httpClient.get(url) {
                    if (url.contains("oauth.reddit.com") && isBearer) {
                        currentAuthHeader?.let { (k, v) -> header(k, v) }
                    } else if (currentAuthHeader?.first == "Cookie" && !url.contains("oauth.reddit.com")) {
                        header("Cookie", currentAuthHeader.second)
                    }
                    header("User-Agent", USER_AGENT)
                    header("Cache-Control", "no-cache, no-store, must-revalidate")
                    header("Pragma", "no-cache")
                    parameter("limit", limit)
                    after?.let { parameter("after", it) }
                    parameter("raw_json", 1)
                    parameter("_t", System.currentTimeMillis())
                }

                var status = response.status.value
                var responseText = response.bodyAsText()
                Log.d(TAG, "Respuesta de $url: status=$status, len=${responseText.length}, preview=${responseText.take(120).replace("\n", " ")}")

                // Si recibimos 401 en oauth.reddit.com, intentamos refrescar el token de forma forzada
                if (status == 401 && url.contains("oauth.reddit.com")) {
                    Log.w(TAG, "401 recibido en OAuth. Intentando forceRefreshToken...")
                    val newToken = authManager.forceRefreshToken()
                    if (!newToken.isNullOrEmpty()) {
                        currentAuthHeader = Pair("Authorization", if (newToken.startsWith("Bearer ")) newToken else "Bearer $newToken")
                        response = httpClient.get(url) {
                            header("Authorization", currentAuthHeader.second)
                            header("User-Agent", USER_AGENT)
                            header("Cache-Control", "no-cache, no-store, must-revalidate")
                            header("Pragma", "no-cache")
                            parameter("limit", limit)
                            after?.let { parameter("after", it) }
                            parameter("raw_json", 1)
                            parameter("_t", System.currentTimeMillis())
                        }
                        status = response.status.value
                        responseText = response.bodyAsText()
                        Log.d(TAG, "Reintento tras refresh en $url: status=$status, len=${responseText.length}")
                    }
                }

                if (status == 200 && !responseText.trim().startsWith("<") && responseText.isNotBlank()) {
                    val listing = NetworkClient.json.decodeFromString<RedditListing<RedditPost>>(responseText)
                    if (listing.data.children.isNotEmpty()) {
                        Log.d(TAG, "✅ Feed cargado con éxito: ${listing.data.children.size} posts de $url")
                        return@withContext listing
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallo al consultar $url: ${e.message}")
            }
        }

        Log.w(TAG, "Ninguna URL devolvió posts válidos. Devolviendo lista vacía.")
        RedditListing(kind = "Listing", data = ListingData(children = emptyList(), after = null))
    }

    suspend fun getComments(
        subreddit: String,
        postId: String,
        sort: String = "confidence",
        limit: Int = 30
    ): Pair<RedditPost?, List<RedditComment>> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val sub = if (subreddit.isEmpty() || subreddit == "home") "popular" else subreddit

        val urlsToTry = if (isBearer) {
            listOf(
                "https://oauth.reddit.com/r/$sub/comments/$postId",
                "https://old.reddit.com/r/$sub/comments/$postId.json"
            )
        } else {
            listOf(
                "https://old.reddit.com/r/$sub/comments/$postId.json",
                "https://www.reddit.com/r/$sub/comments/$postId.json"
            )
        }

        for (url in urlsToTry) {
            try {
                val responseText = httpClient.get(url) {
                    if (url.contains("oauth.reddit.com") && isBearer) {
                        authHeader?.let { (k, v) -> header(k, v) }
                    } else if (authHeader?.first == "Cookie") {
                        header("Cookie", authHeader.second)
                    }
                    header("User-Agent", USER_AGENT)
                    parameter("sort", sort)
                    parameter("limit", limit)
                    parameter("raw_json", 1)
                }.bodyAsText()

                if (!responseText.trim().startsWith("<") && responseText.isNotBlank()) {
                    val responseJson: JsonArray = NetworkClient.json.decodeFromString(responseText)
                    var post: RedditPost? = null
                    val comments = mutableListOf<RedditComment>()

                    if (responseJson.isNotEmpty()) {
                        val postListing: RedditListing<RedditPost> = NetworkClient.json.decodeFromJsonElement(responseJson[0])
                        post = postListing.data.children.firstOrNull()?.data
                    }

                    if (responseJson.size > 1) {
                        val parsed = com.reddit.wearos.data.model.parseCommentsFromListingElement(responseJson[1])
                        comments.addAll(parsed)
                    }

                    if (post != null || comments.isNotEmpty()) {
                        return@withContext Pair(post, comments)
                    }
                }
            } catch (_: Exception) {}
        }
        Pair(null, emptyList())
    }

    suspend fun vote(postFullName: String, dir: Int): Boolean = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val url = if (isBearer) "https://oauth.reddit.com/api/vote" else "https://old.reddit.com/api/vote"
        try {
            httpClient.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("id", postFullName)
                    append("dir", dir.toString())
                }
            ) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun savePost(postFullName: String, save: Boolean): Boolean = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val endpoint = if (isBearer) {
            if (save) "https://oauth.reddit.com/api/save" else "https://oauth.reddit.com/api/unsave"
        } else {
            if (save) "https://old.reddit.com/api/save" else "https://old.reddit.com/api/unsave"
        }
        try {
            httpClient.submitForm(
                url = endpoint,
                formParameters = Parameters.build {
                    append("id", postFullName)
                }
            ) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getSavedPosts(
        username: String,
        after: String? = null,
        limit: Int = 25
    ): RedditListing<RedditPost> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val url = if (isBearer) {
            "https://oauth.reddit.com/user/$username/saved"
        } else {
            "https://old.reddit.com/user/$username/saved.json"
        }
        try {
            val response = httpClient.get(url) {
                if (url.contains("oauth.reddit.com") && isBearer) {
                    authHeader?.let { (k, v) -> header(k, v) }
                }
                header("User-Agent", USER_AGENT)
                parameter("limit", limit)
                after?.let { parameter("after", it) }
                parameter("raw_json", 1)
            }
            val status = response.status.value
            val responseText = response.bodyAsText()
            if (status == 200 && !responseText.trim().startsWith("<") && responseText.isNotBlank()) {
                return@withContext NetworkClient.json.decodeFromString<RedditListing<RedditPost>>(responseText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando guardados: ${e.message}")
        }
        RedditListing(kind = "Listing", data = ListingData(children = emptyList(), after = null))
    }

    suspend fun getUserSubreddits(): List<RedditSubreddit> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val url = if (isBearer) "https://oauth.reddit.com/subreddits/mine/subscriber" else "https://old.reddit.com/subreddits/mine/subscriber.json"
        try {
            val responseText = httpClient.get(url) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
                parameter("limit", 50)
                parameter("raw_json", 1)
            }.bodyAsText()

            if (responseText.trim().startsWith("<")) {
                return@withContext emptyList()
            }

            val response: RedditListing<RedditSubreddit> = NetworkClient.json.decodeFromString(responseText)
            response.data.children.map { it.data }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMe(): RedditUser? = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val url = if (isBearer) "https://oauth.reddit.com/api/v1/me" else "https://old.reddit.com/api/v1/me.json"
        try {
            val responseText = httpClient.get(url) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
                parameter("raw_json", 1)
            }.bodyAsText()

            if (responseText.trim().startsWith("<")) {
                return@withContext null
            }
            NetworkClient.json.decodeFromString<RedditUser>(responseText)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun addComment(thingId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val url = if (isBearer) "https://oauth.reddit.com/api/comment" else "https://old.reddit.com/api/comment"
        val formattedThingId = if (thingId.startsWith("t1_") || thingId.startsWith("t3_")) thingId else if (thingId.length <= 8) "t3_$thingId" else thingId
        try {
            val response = httpClient.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("api_type", "json")
                    append("thing_id", formattedThingId)
                    append("text", text)
                }
            ) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
            }
            val resText = response.bodyAsText()
            Log.d(TAG, "addComment result for $formattedThingId: status=${response.status.value}, len=${resText.length}")
            !resText.contains("NO_USER") && !resText.contains("USER_REQUIRED") && response.status.value in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error posting comment: ${e.message}")
            false
        }
    }

    suspend fun getInbox(limit: Int = 25): List<RedditMessage> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val url = if (isBearer) "https://oauth.reddit.com/message/inbox" else "https://old.reddit.com/message/inbox.json"
        try {
            val responseText = httpClient.get(url) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
                parameter("limit", limit)
                parameter("raw_json", 1)
            }.bodyAsText()

            if (responseText.trim().startsWith("<")) {
                return@withContext emptyList()
            }

            val response: RedditListing<RedditMessage> = NetworkClient.json.decodeFromString(responseText)
            response.data.children.map { it.data }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading inbox: ${e.message}")
            emptyList()
        }
    }
}
