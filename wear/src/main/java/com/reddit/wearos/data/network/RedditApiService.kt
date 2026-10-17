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
        val token = authManager.refreshTokenIfNeeded() ?: tokenStorage.getAccessToken() ?: tokenStorage.getSessionCookie() ?: return null
        val clean = token.trim()
        return if (clean.contains("reddit_session=") || clean.contains("=")) {
            Pair("Cookie", clean)
        } else if (clean.startsWith("Bearer ")) {
            Pair("Authorization", clean)
        } else {
            Pair("Authorization", "Bearer $clean")
        }
    }

    private suspend fun isBearerAuth(): Boolean {
        val header = getAuthHeader() ?: return false
        return header.first == "Authorization"
    }

    private suspend fun ensureModhash(): String? {
        tokenStorage.getModhash()?.takeIf { it.isNotEmpty() }?.let { return it }
        getMe()
        return tokenStorage.getModhash()
    }

    var lastDiagnostic: String = "Iniciando consulta..."

    suspend fun getFeed(
        subreddit: String? = null,
        sort: String = "new",
        after: String? = null,
        limit: Int = 25
    ): RedditListing<RedditPost> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val sessionCookie = tokenStorage.getSessionCookie()
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
            if (authHeader != null || !sessionCookie.isNullOrEmpty()) {
                urlsToTry.add("https://old.reddit.com/$sortPath.json")
                urlsToTry.add("https://api.reddit.com/$sortPath.json")
            }
            urlsToTry.add("https://api.reddit.com/r/popular/$sortPath.json")
            urlsToTry.add("https://api.reddit.com/r/all/$sortPath.json")
            urlsToTry.add("https://api.reddit.com/$sortPath.json")
        } else {
            urlsToTry.add("https://old.reddit.com/r/$sub/$sortPath.json")
            urlsToTry.add("https://api.reddit.com/r/$sub/$sortPath.json")
            urlsToTry.add("https://api.reddit.com/r/$sub.json")
        }

        var currentAuthHeader = authHeader

        for (url in urlsToTry) {
            try {
                Log.d(TAG, "Intentando feed: url=$url, sort=$sortPath, authHeader=${currentAuthHeader?.first}")
                var response = httpClient.get(url) {
                    if (url.contains("oauth.reddit.com") && currentAuthHeader?.first == "Authorization") {
                        header("Authorization", currentAuthHeader!!.second)
                    } else if (currentAuthHeader?.first == "Cookie") {
                        header("Cookie", currentAuthHeader!!.second)
                    } else if (!sessionCookie.isNullOrEmpty()) {
                        header("Cookie", sessionCookie)
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

                if ((status == 401 || status == 403) && url.contains("oauth.reddit.com")) {
                    Log.w(TAG, "$status recibido en OAuth. Intentando forceRefreshToken...")
                    val newToken = authManager.forceRefreshToken()
                    if (!newToken.isNullOrEmpty() && !newToken.contains("reddit_session=")) {
                        currentAuthHeader = Pair("Authorization", if (newToken.startsWith("Bearer ")) newToken else "Bearer $newToken")
                        response = httpClient.get(url) {
                            header("Authorization", currentAuthHeader!!.second)
                            header("User-Agent", USER_AGENT)
                            header("Cache-Control", "no-cache, no-store, must-revalidate")
                            header("Pragma", "no-cache")
                            parameter("limit", limit)
                            after?.let { parameter("after", it) }
                            parameter("raw_json", 1)
                        }
                        status = response.status.value
                        responseText = response.bodyAsText()
                    } else if (!newToken.isNullOrEmpty()) {
                        currentAuthHeader = Pair("Cookie", newToken)
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

                if ((status == 403 || status == 401) && currentAuthHeader != null && !url.contains("oauth.reddit.com")) {
                    val publicUrl = if (url.endsWith(".json")) url else "$url.json"
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

        RedditListing(kind = "Listing", data = ListingData(children = emptyList(), after = null))
    }

    suspend fun getComments(
        subreddit: String,
        postId: String,
        sort: String = "confidence",
        limit: Int = 30
    ): Pair<RedditPost?, List<RedditComment>> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val sessionCookie = tokenStorage.getSessionCookie()
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
                    } else if (!sessionCookie.isNullOrEmpty()) {
                        header("Cookie", sessionCookie)
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
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val url = if (isBearer) "https://oauth.reddit.com/api/vote" else "https://old.reddit.com/api/vote"
        val uh = if (!isBearer) ensureModhash() else null
        try {
            httpClient.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("id", postFullName)
                    append("dir", dir.toString())
                    if (!uh.isNullOrEmpty()) append("uh", uh)
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
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val endpoint = if (isBearer) {
            if (save) "https://oauth.reddit.com/api/save" else "https://oauth.reddit.com/api/unsave"
        } else {
            if (save) "https://old.reddit.com/api/save" else "https://old.reddit.com/api/unsave"
        }
        val uh = if (!isBearer) ensureModhash() else null
        try {
            httpClient.submitForm(
                url = endpoint,
                formParameters = Parameters.build {
                    append("id", postFullName)
                    if (!uh.isNullOrEmpty()) append("uh", uh)
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
        var currentAuthHeader = getAuthHeader()
        val isBearer = currentAuthHeader?.first == "Authorization"
        val sessionCookie = tokenStorage.getSessionCookie()
        val storedUser = tokenStorage.getUsername()?.trim()?.removePrefix("u/")?.removePrefix("/u/")
        val cleanUser = if (username.isNotBlank() && username != "me") {
            username.trim().removePrefix("u/").removePrefix("/u/")
        } else {
            storedUser ?: "me"
        }
        val urlsToTry = mutableListOf<String>()
        if (isBearer) {
            urlsToTry.add("https://oauth.reddit.com/user/me/saved")
            if (cleanUser.isNotEmpty() && cleanUser != "me") {
                urlsToTry.add("https://oauth.reddit.com/user/$cleanUser/saved")
            }
        }
        if (cleanUser.isNotEmpty() && cleanUser != "me") {
            urlsToTry.add("https://old.reddit.com/user/$cleanUser/saved.json")
            urlsToTry.add("https://api.reddit.com/user/$cleanUser/saved.json")
            urlsToTry.add("https://www.reddit.com/user/$cleanUser/saved.json")
        }
        urlsToTry.add("https://old.reddit.com/saved.json")
        urlsToTry.add("https://api.reddit.com/saved.json")

        for (url in urlsToTry) {
            try {
                Log.d(TAG, "Consultando guardados: url=$url, auth=${currentAuthHeader?.first}")
                var response = httpClient.get(url) {
                    if (url.contains("oauth.reddit.com") && currentAuthHeader?.first == "Authorization") {
                        header("Authorization", currentAuthHeader!!.second)
                    } else if (currentAuthHeader?.first == "Cookie") {
                        header("Cookie", currentAuthHeader!!.second)
                    } else if (!sessionCookie.isNullOrEmpty()) {
                        header("Cookie", sessionCookie)
                    }
                    header("User-Agent", USER_AGENT)
                    parameter("limit", limit)
                    after?.let { parameter("after", it) }
                    parameter("raw_json", 1)
                }

                var status = response.status.value
                var responseText = response.bodyAsText()

                if ((status == 401 || status == 403) && url.contains("oauth.reddit.com")) {
                    Log.w(TAG, "$status en guardados. Intentando refrescar token...")
                    val newToken = authManager.forceRefreshToken()
                    if (!newToken.isNullOrEmpty() && !newToken.contains("reddit_session=")) {
                        currentAuthHeader = Pair("Authorization", if (newToken.startsWith("Bearer ")) newToken else "Bearer $newToken")
                        response = httpClient.get(url) {
                            header("Authorization", currentAuthHeader!!.second)
                            header("User-Agent", USER_AGENT)
                            parameter("limit", limit)
                            after?.let { parameter("after", it) }
                            parameter("raw_json", 1)
                        }
                        status = response.status.value
                        responseText = response.bodyAsText()
                    } else if (!newToken.isNullOrEmpty()) {
                        currentAuthHeader = Pair("Cookie", newToken)
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
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val sessionCookie = tokenStorage.getSessionCookie()
        val urls = if (isBearer) {
            listOf("https://oauth.reddit.com/subreddits/mine/subscriber", "https://old.reddit.com/subreddits/mine/subscriber.json")
        } else {
            listOf("https://old.reddit.com/subreddits/mine/subscriber.json")
        }
        for (url in urls) {
            try {
                val responseText = httpClient.get(url) {
                    if (url.contains("oauth.reddit.com") && isBearer) {
                        authHeader?.let { (k, v) -> header(k, v) }
                    } else if (authHeader?.first == "Cookie") {
                        header("Cookie", authHeader.second)
                    } else if (!sessionCookie.isNullOrEmpty()) {
                        header("Cookie", sessionCookie)
                    }
                    header("User-Agent", USER_AGENT)
                    parameter("limit", 50)
                    parameter("raw_json", 1)
                }.bodyAsText()

                if (!responseText.trim().startsWith("<") && responseText.isNotBlank()) {
                    val response: RedditListing<RedditSubreddit> = NetworkClient.json.decodeFromString(responseText)
                    if (response.data.children.isNotEmpty()) {
                        return@withContext response.data.children.map { it.data }
                    }
                }
            } catch (_: Exception) {}
        }
        emptyList()
    }

    suspend fun getMe(): RedditUser? = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val sessionCookie = tokenStorage.getSessionCookie()
        val urls = if (isBearer) {
            listOf("https://oauth.reddit.com/api/v1/me", "https://old.reddit.com/api/me.json")
        } else {
            listOf("https://old.reddit.com/api/me.json")
        }
        for (url in urls) {
            try {
                val responseText = httpClient.get(url) {
                    if (url.contains("oauth.reddit.com") && isBearer) {
                        authHeader?.let { (k, v) -> header(k, v) }
                    } else if (authHeader?.first == "Cookie") {
                        header("Cookie", authHeader.second)
                    } else if (!sessionCookie.isNullOrEmpty()) {
                        header("Cookie", sessionCookie)
                    }
                    header("User-Agent", USER_AGENT)
                    parameter("raw_json", 1)
                }.bodyAsText()

                if (!responseText.trim().startsWith("<") && responseText.isNotBlank()) {
                    val root = NetworkClient.json.parseToJsonElement(responseText).jsonObject
                    val targetObj = root["data"]?.jsonObject ?: root
                    targetObj["modhash"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                        tokenStorage.setModhash(it)
                    }
                    val user = NetworkClient.json.decodeFromJsonElement<RedditUser>(targetObj)
                    if (user.name.isNotEmpty()) {
                        tokenStorage.setUsername(user.name)
                        return@withContext user
                    }
                }
            } catch (_: Exception) {}
        }
        null
    }

    suspend fun addComment(thingId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val url = if (isBearer) "https://oauth.reddit.com/api/comment" else "https://old.reddit.com/api/comment"
        val formattedThingId = if (thingId.startsWith("t1_") || thingId.startsWith("t3_")) thingId else if (thingId.length <= 8) "t3_$thingId" else thingId
        val uh = if (!isBearer) ensureModhash() else null
        try {
            val response = httpClient.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("api_type", "json")
                    append("thing_id", formattedThingId)
                    append("text", text)
                    if (!uh.isNullOrEmpty()) append("uh", uh)
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

    suspend fun editComment(thingId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val url = if (isBearer) "https://oauth.reddit.com/api/editusertext" else "https://old.reddit.com/api/editusertext"
        val formattedThingId = if (thingId.startsWith("t1_") || thingId.startsWith("t3_")) thingId else "t1_$thingId"
        val uh = if (!isBearer) ensureModhash() else null
        try {
            val response = httpClient.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("api_type", "json")
                    append("thing_id", formattedThingId)
                    append("text", text)
                    if (!uh.isNullOrEmpty()) append("uh", uh)
                }
            ) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
            }
            val resText = response.bodyAsText()
            Log.d(TAG, "editComment result for $formattedThingId: status=${response.status.value}")
            !resText.contains("NO_USER") && !resText.contains("USER_REQUIRED") && response.status.value in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error editing comment: ${e.message}")
            false
        }
    }

    suspend fun deleteComment(thingId: String): Boolean = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val url = if (isBearer) "https://oauth.reddit.com/api/del" else "https://old.reddit.com/api/del"
        val formattedThingId = if (thingId.startsWith("t1_") || thingId.startsWith("t3_")) thingId else "t1_$thingId"
        val uh = if (!isBearer) ensureModhash() else null
        try {
            val response = httpClient.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("id", formattedThingId)
                    if (!uh.isNullOrEmpty()) append("uh", uh)
                }
            ) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
            }
            val resText = response.bodyAsText()
            Log.d(TAG, "deleteComment result for $formattedThingId: status=${response.status.value}")
            !resText.contains("NO_USER") && !resText.contains("USER_REQUIRED") && response.status.value in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting comment: ${e.message}")
            false
        }
    }

    suspend fun getInbox(limit: Int = 25): List<RedditMessage> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val sessionCookie = tokenStorage.getSessionCookie()
        val urls = if (isBearer) {
            listOf("https://oauth.reddit.com/message/inbox", "https://old.reddit.com/message/inbox.json")
        } else {
            listOf("https://old.reddit.com/message/inbox.json", "https://api.reddit.com/message/inbox.json")
        }
        for (url in urls) {
            try {
                val responseText = httpClient.get(url) {
                    if (url.contains("oauth.reddit.com") && isBearer) {
                        authHeader?.let { (k, v) -> header(k, v) }
                    } else if (authHeader?.first == "Cookie") {
                        header("Cookie", authHeader.second)
                    } else if (!sessionCookie.isNullOrEmpty()) {
                        header("Cookie", sessionCookie)
                    }
                    header("User-Agent", USER_AGENT)
                    parameter("limit", limit)
                    parameter("raw_json", 1)
                }.bodyAsText()

                if (!responseText.trim().startsWith("<") && responseText.isNotBlank()) {
                    val response: RedditListing<RedditMessage> = NetworkClient.json.decodeFromString(responseText)
                    return@withContext response.data.children.map { it.data }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading inbox from $url: ${e.message}")
            }
        }
        emptyList()
    }

    suspend fun getUserSubmitted(
        username: String,
        after: String? = null,
        limit: Int = 25
    ): RedditListing<RedditPost> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val url = if (isBearer) "https://oauth.reddit.com/user/$username/submitted" else "https://old.reddit.com/user/$username/submitted.json"
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
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val url = if (isBearer) "https://oauth.reddit.com/user/$username/comments" else "https://old.reddit.com/user/$username/comments.json"
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
        timeRange: String = "all",
        after: String? = null,
        limit: Int = 25
    ): RedditListing<RedditPost> = withContext(Dispatchers.IO) {
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val url = if (isBearer) {
            if (!subreddit.isNullOrEmpty() && subreddit != "home") "https://oauth.reddit.com/r/$subreddit/search" else "https://oauth.reddit.com/search"
        } else {
            if (!subreddit.isNullOrEmpty() && subreddit != "home") "https://old.reddit.com/r/$subreddit/search.json" else "https://old.reddit.com/search.json"
        }
        try {
            val responseText = httpClient.get(url) {
                authHeader?.let { (k, v) -> header(k, v) }
                header("User-Agent", USER_AGENT)
                parameter("q", query)
                parameter("sort", sort)
                parameter("t", timeRange)
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
        val authHeader = getAuthHeader()
        val isBearer = authHeader?.first == "Authorization"
        val url = if (isBearer) "https://oauth.reddit.com/subreddits/search" else "https://old.reddit.com/subreddits/search.json"
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
