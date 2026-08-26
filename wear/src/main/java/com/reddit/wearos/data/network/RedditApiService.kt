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
import kotlinx.serialization.json.*

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
        return if (token.startsWith("Bearer ")) {
            Pair("Authorization", token)
        } else if (token.startsWith("eyJ") && !token.contains("=") && !token.contains(";")) {
            Pair("Authorization", "Bearer $token")
        } else {
            val cookieVal = if (!token.contains("=")) "reddit_session=$token" else token
            Pair("Cookie", cookieVal)
        }
    }

    private suspend fun isBearerAuth(): Boolean {
        val token = tokenStorage.getAccessToken() ?: return false
        return token.startsWith("Bearer ") || (token.startsWith("eyJ") && !token.contains("=") && !token.contains(";"))
    }

    var lastDiagnostic: String = "Iniciando consulta..."

    suspend fun getFeed(
        subreddit: String? = null,
        sort: String = "new",
        after: String? = null,
        limit: Int = 25
    ): RedditListing<RedditPost> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val sub = subreddit?.trim()
        val isHome = sub.isNullOrEmpty() || sub.equals("home", ignoreCase = true) || sub.equals("principal", ignoreCase = true) || sub.equals("frontpage", ignoreCase = true)

        val urlsToTry = mutableListOf<String>()
        val sortPath = if (sort.isBlank()) "new" else sort.lowercase()
        if (isBearer) {
            if (isHome) {
                urlsToTry.add("https://oauth.reddit.com/$sortPath")
                if (sortPath == "hot") {
                    urlsToTry.add("https://oauth.reddit.com/best")
                }
            } else {
                urlsToTry.add("https://oauth.reddit.com/r/$sub/$sortPath")
            }
        }
        if (isHome) {
            if (authHeader != null) {
                urlsToTry.add("https://api.reddit.com/$sortPath.json")
            }
            urlsToTry.add("https://api.reddit.com/r/popular/$sortPath.json")
            urlsToTry.add("https://api.reddit.com/r/all/$sortPath.json")
            urlsToTry.add("https://api.reddit.com/$sortPath.json")
        } else {
            urlsToTry.add("https://api.reddit.com/r/$sub/$sortPath.json")
            urlsToTry.add("https://api.reddit.com/r/$sub.json")
        }

        var currentAuthHeader = authHeader

        for (url in urlsToTry) {
            try {
                Log.d(TAG, "Intentando feed: url=$url, sort=$sortPath, authHeader=${currentAuthHeader?.first} -> ${currentAuthHeader?.second?.take(15)}...")
                var response = httpClient.get(url) {
                    if (url.contains("oauth.reddit.com") && isBearer) {
                        currentAuthHeader?.let { (k, v) -> header(k, v) }
                    } else if (currentAuthHeader?.first == "Cookie") {
                        header("Cookie", currentAuthHeader.second)
                    }
                    header("User-Agent", USER_AGENT)
                    header("Cache-Control", "no-cache, no-store, must-revalidate")
                    header("Pragma", "no-cache")
                    parameter("limit", limit)
                    after?.let { parameter("after", it) }
                    parameter("raw_json", 1)
                }

                var status = response.status.value
                var responseText = response.bodyAsText()
                lastDiagnostic = "HTTP $status (${url.substringAfter("://").take(30)}): ${responseText.take(60).replace("\n", " ")}"
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
                        }
                        status = response.status.value
                        responseText = response.bodyAsText()
                        lastDiagnostic = "Reintento $status: ${responseText.take(60).replace("\n", " ")}"
                    }
                }

                if (status == 200 && !responseText.trim().startsWith("<") && responseText.isNotBlank()) {
                    val listing = NetworkClient.json.decodeFromString<RedditListing<RedditPost>>(responseText)
                    if (listing.data.children.isNotEmpty()) {
                        Log.d(TAG, "✅ Feed cargado con éxito: ${listing.data.children.size} posts de $url")
                        lastDiagnostic = "OK (${listing.data.children.size} posts)"
                        return@withContext listing
                    }
                }

                // Si la sesión dio 403 o no trajo posts, intentamos inmediatamente en modo público
                if ((status == 403 || status == 401) && currentAuthHeader != null) {
                    val publicUrl = if (url.endsWith(".json")) url else "$url.json"
                    Log.d(TAG, "Reintentando $publicUrl de forma pública...")
                    val publicResp = httpClient.get(publicUrl) {
                        header("User-Agent", USER_AGENT)
                        header("Cache-Control", "no-cache, no-store, must-revalidate")
                        header("Pragma", "no-cache")
                        parameter("limit", limit)
                        after?.let { parameter("after", it) }
                        parameter("raw_json", 1)
                    }
                    if (publicResp.status.value == 200) {
                        val pubText = publicResp.bodyAsText()
                        if (!pubText.trim().startsWith("<") && pubText.isNotBlank()) {
                            val listing = NetworkClient.json.decodeFromString<RedditListing<RedditPost>>(pubText)
                            if (listing.data.children.isNotEmpty()) {
                                Log.d(TAG, "✅ Feed público cargado: ${listing.data.children.size} posts de $publicUrl")
                                lastDiagnostic = "OK Público (${listing.data.children.size} posts)"
                                return@withContext listing
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                lastDiagnostic = "Excepción en ${url.substringAfter("://").take(25)}: ${e.message}"
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
        username: String = "me",
        after: String? = null,
        limit: Int = 25
    ): RedditListing<RedditPost> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        var currentAuthHeader = getAuthHeader()
        val cleanUser = username.trim().removePrefix("u/").removePrefix("/u/")
        val urlsToTry = mutableListOf<String>()
        if (isBearer) {
            urlsToTry.add("https://oauth.reddit.com/user/me/saved")
            if (cleanUser.isNotEmpty() && cleanUser != "me") {
                urlsToTry.add("https://oauth.reddit.com/user/$cleanUser/saved")
            }
        }
        if (cleanUser.isNotEmpty() && cleanUser != "me") {
            urlsToTry.add("https://api.reddit.com/user/$cleanUser/saved.json")
            urlsToTry.add("https://old.reddit.com/user/$cleanUser/saved.json")
            urlsToTry.add("https://www.reddit.com/user/$cleanUser/saved.json")
        }
        urlsToTry.add("https://api.reddit.com/user/me/saved.json")
        urlsToTry.add("https://old.reddit.com/user/me/saved.json")
        urlsToTry.add("https://www.reddit.com/user/me/saved.json")
        urlsToTry.add("https://api.reddit.com/saved.json")
        urlsToTry.add("https://old.reddit.com/saved.json")
        urlsToTry.add("https://www.reddit.com/saved.json")

        for (url in urlsToTry) {
            try {
                Log.d(TAG, "Consultando guardados: url=$url, auth=${currentAuthHeader?.first}")
                var response = httpClient.get(url) {
                    if (url.contains("oauth.reddit.com") && isBearer) {
                        currentAuthHeader?.let { (k, v) -> header(k, v) }
                    } else if (currentAuthHeader?.first == "Cookie") {
                        header("Cookie", currentAuthHeader.second)
                    } else if (currentAuthHeader != null) {
                        header(currentAuthHeader.first, currentAuthHeader.second)
                    }
                    header("User-Agent", USER_AGENT)
                    parameter("limit", limit)
                    after?.let { parameter("after", it) }
                    parameter("raw_json", 1)
                }

                var status = response.status.value
                var responseText = response.bodyAsText()

                if (status == 401 && url.contains("oauth.reddit.com")) {
                    Log.w(TAG, "401 en guardados. Intentando refrescar token...")
                    val newToken = authManager.forceRefreshToken()
                    if (!newToken.isNullOrEmpty()) {
                        currentAuthHeader = Pair("Authorization", if (newToken.startsWith("Bearer ")) newToken else "Bearer $newToken")
                        response = httpClient.get(url) {
                            header("Authorization", currentAuthHeader.second)
                            header("User-Agent", USER_AGENT)
                            parameter("limit", limit)
                            after?.let { parameter("after", it) }
                            parameter("raw_json", 1)
                        }
                        status = response.status.value
                        responseText = response.bodyAsText()
                    }
                }

                Log.d(TAG, "Respuesta guardados $url: status=$status, len=${responseText.length}")
                if (status == 200 && !responseText.trim().startsWith("<") && responseText.isNotBlank()) {
                    val root = NetworkClient.json.parseToJsonElement(responseText).jsonObject
                    val data = root["data"]?.jsonObject
                    val afterKey = data?.get("after")?.jsonPrimitive?.contentOrNull
                    val children = data?.get("children")?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

                    val postList = mutableListOf<ListingChild<RedditPost>>()
                    for (child in children) {
                        try {
                            val childObj = child.jsonObject
                            val kind = childObj["kind"]?.jsonPrimitive?.content
                            if (kind == "t3") {
                                val postData = NetworkClient.json.decodeFromJsonElement<RedditPost>(childObj["data"]!!)
                                postList.add(ListingChild(kind = "t3", data = postData))
                            } else if (kind == "t1") {
                                val commentObj = childObj["data"]?.jsonObject
                                if (commentObj != null) {
                                    val subName = commentObj["subreddit"]?.jsonPrimitive?.contentOrNull ?: ""
                                    val commentPost = RedditPost(
                                        id = commentObj["id"]?.jsonPrimitive?.contentOrNull ?: "",
                                        name = commentObj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                                        title = "💬 Comentario en r/$subName",
                                        selfText = commentObj["body"]?.jsonPrimitive?.contentOrNull ?: "",
                                        author = commentObj["author"]?.jsonPrimitive?.contentOrNull ?: "[desconocido]",
                                        subreddit = subName,
                                        score = commentObj["score"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                                        createdUtc = commentObj["created_utc"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0,
                                        saved = true,
                                        permalink = commentObj["permalink"]?.jsonPrimitive?.contentOrNull ?: ""
                                    )
                                    postList.add(ListingChild(kind = "t3", data = commentPost))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error decodificando elemento guardado: ${e.message}")
                        }
                    }
                    if (postList.isNotEmpty() || afterKey != null || url.contains("oauth.reddit.com")) {
                        Log.d(TAG, "✅ Guardados obtenidos: ${postList.size} items de $url")
                        return@withContext RedditListing(
                            kind = "Listing",
                            data = ListingData(children = postList, after = afterKey)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando guardados desde $url: ${e.message}")
            }
        }
        Log.w(TAG, "No se pudieron obtener guardados de ninguna URL.")
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
        val url = if (isBearer) "https://oauth.reddit.com/message/inbox" else "https://api.reddit.com/message/inbox"
        try {
            val responseText = httpClient.get(url) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
                parameter("limit", limit)
                parameter("raw_json", 1)
            }.bodyAsText()

            if (responseText.trim().startsWith("<") || responseText.isBlank()) {
                return@withContext emptyList()
            }

            val response: RedditListing<RedditMessage> = NetworkClient.json.decodeFromString(responseText)
            response.data.children.map { it.data }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading inbox: ${e.message}")
            emptyList()
        }
    }

    suspend fun getUserSubmitted(
        username: String,
        after: String? = null,
        limit: Int = 25
    ): RedditListing<RedditPost> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val url = if (isBearer) "https://oauth.reddit.com/user/$username/submitted" else "https://api.reddit.com/user/$username/submitted"
        try {
            val responseText = httpClient.get(url) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
                parameter("limit", limit)
                after?.let { parameter("after", it) }
                parameter("raw_json", 1)
            }.bodyAsText()

            if (responseText.trim().startsWith("<") || responseText.isBlank()) {
                return@withContext RedditListing(kind = "Listing", data = ListingData(children = emptyList(), after = null))
            }
            NetworkClient.json.decodeFromString<RedditListing<RedditPost>>(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user submitted posts: ${e.message}")
            RedditListing(kind = "Listing", data = ListingData(children = emptyList(), after = null))
        }
    }

    suspend fun getUserComments(
        username: String,
        after: String? = null,
        limit: Int = 25
    ): List<RedditComment> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val url = if (isBearer) "https://oauth.reddit.com/user/$username/comments" else "https://api.reddit.com/user/$username/comments"
        try {
            val responseText = httpClient.get(url) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
                parameter("limit", limit)
                after?.let { parameter("after", it) }
                parameter("raw_json", 1)
            }.bodyAsText()

            if (responseText.trim().startsWith("<") || responseText.isBlank()) {
                return@withContext emptyList()
            }
            val rootElement = NetworkClient.json.parseToJsonElement(responseText)
            com.reddit.wearos.data.model.parseCommentsFromListingElement(rootElement)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user comments: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchPosts(
        query: String,
        subreddit: String? = null,
        sort: String = "relevance",
        after: String? = null,
        limit: Int = 25
    ): RedditListing<RedditPost> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val url = if (isBearer) {
            if (!subreddit.isNullOrEmpty() && subreddit != "home") "https://oauth.reddit.com/r/$subreddit/search" else "https://oauth.reddit.com/search"
        } else {
            if (!subreddit.isNullOrEmpty() && subreddit != "home") "https://api.reddit.com/r/$subreddit/search" else "https://api.reddit.com/search"
        }
        try {
            val responseText = httpClient.get(url) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
                parameter("q", query)
                parameter("sort", sort)
                parameter("limit", limit)
                parameter("raw_json", 1)
                after?.let { parameter("after", it) }
                if (!subreddit.isNullOrEmpty() && subreddit != "home") {
                    parameter("restrict_sr", "1")
                }
            }.bodyAsText()

            if (responseText.trim().startsWith("<") || responseText.isBlank()) {
                return@withContext RedditListing(kind = "Listing", data = ListingData(children = emptyList(), after = null))
            }
            NetworkClient.json.decodeFromString<RedditListing<RedditPost>>(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching posts: ${e.message}")
            RedditListing(kind = "Listing", data = ListingData(children = emptyList(), after = null))
        }
    }

    suspend fun searchSubreddits(query: String, limit: Int = 20): List<RedditSubreddit> = withContext(Dispatchers.IO) {
        val isBearer = isBearerAuth()
        val authHeader = getAuthHeader()
        val url = if (isBearer) "https://oauth.reddit.com/subreddits/search" else "https://api.reddit.com/subreddits/search"
        try {
            val responseText = httpClient.get(url) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
                parameter("q", query)
                parameter("limit", limit)
                parameter("raw_json", 1)
            }.bodyAsText()

            if (responseText.trim().startsWith("<") || responseText.isBlank()) {
                return@withContext emptyList()
            }
            val response: RedditListing<RedditSubreddit> = NetworkClient.json.decodeFromString(responseText)
            response.data.children.map { it.data }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching subreddits: ${e.message}")
            emptyList()
        }
    }
}
