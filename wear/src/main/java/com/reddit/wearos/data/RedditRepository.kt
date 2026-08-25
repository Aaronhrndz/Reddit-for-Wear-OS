package com.reddit.wearos.data

import com.reddit.wearos.data.model.RedditComment
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.data.model.RedditSubreddit
import com.reddit.wearos.data.model.RedditUser
import com.reddit.wearos.data.network.RedditApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RedditRepository(
    private val apiService: RedditApiService
) {
    // Por defecto inicia en el Feed Personal (Home) de tu cuenta
    private val _currentSubreddit = MutableStateFlow("home")
    val currentSubreddit: StateFlow<String> = _currentSubreddit.asStateFlow()

    // Criterio de ordenación actual (new por defecto, hot, rising, top)
    private val _currentSort = MutableStateFlow("new")
    val currentSort: StateFlow<String> = _currentSort.asStateFlow()

    fun setSubreddit(subreddit: String) {
        _currentSubreddit.value = subreddit
    }

    fun setSort(sort: String) {
        _currentSort.value = sort
    }

    fun getLastDiagnostic(): String = apiService.lastDiagnostic

    suspend fun getFeed(
        subreddit: String = _currentSubreddit.value,
        sort: String = _currentSort.value,
        after: String? = null
    ): Pair<List<RedditPost>, String?> {
        val target = if (subreddit.equals("home", ignoreCase = true) || subreddit.equals("principal", ignoreCase = true)) null else subreddit
        val listing = apiService.getFeed(subreddit = target, sort = sort, after = after)
        val posts = listing.data.children.map { it.data }
        return Pair(posts, listing.data.after)
    }

    suspend fun getComments(
        subreddit: String,
        postId: String
    ): Pair<RedditPost?, List<RedditComment>> {
        return apiService.getComments(subreddit = subreddit, postId = postId)
    }

    suspend fun vote(post: RedditPost, direction: Int): Boolean {
        val fullName = if (post.name.isNotEmpty()) post.name else "t3_${post.id}"
        return apiService.vote(fullName, direction)
    }

    suspend fun savePost(post: RedditPost, save: Boolean): Boolean {
        val fullName = if (post.name.isNotEmpty()) post.name else "t3_${post.id}"
        return apiService.savePost(fullName, save)
    }

    suspend fun getUserSubreddits(): List<RedditSubreddit> {
        return apiService.getUserSubreddits()
    }

    suspend fun getMe(): RedditUser? {
        return apiService.getMe()
    }

    suspend fun addComment(thingId: String, text: String): Boolean {
        return apiService.addComment(thingId, text)
    }

    suspend fun getSavedPosts(
        username: String,
        after: String? = null
    ): Pair<List<RedditPost>, String?> {
        val listing = apiService.getSavedPosts(username = username, after = after)
        val posts = listing.data.children.map { it.data }
        return Pair(posts, listing.data.after)
    }

    suspend fun getInbox(): List<com.reddit.wearos.data.model.RedditMessage> {
        return apiService.getInbox()
    }

    suspend fun searchPosts(
        query: String,
        subreddit: String? = null,
        sort: String = "relevance",
        after: String? = null
    ): Pair<List<RedditPost>, String?> {
        val listing = apiService.searchPosts(query = query, subreddit = subreddit, sort = sort, after = after)
        val posts = listing.data.children.map { it.data }
        return Pair(posts, listing.data.after)
    }

    suspend fun searchSubreddits(query: String): List<RedditSubreddit> {
        return apiService.searchSubreddits(query)
    }

    suspend fun getUserSubmitted(username: String, after: String? = null): Pair<List<RedditPost>, String?> {
        val listing = apiService.getUserSubmitted(username = username, after = after)
        val posts = listing.data.children.map { it.data }
        return Pair(posts, listing.data.after)
    }

    suspend fun getUserComments(username: String, after: String? = null): List<RedditComment> {
        return apiService.getUserComments(username = username, after = after)
    }
}
