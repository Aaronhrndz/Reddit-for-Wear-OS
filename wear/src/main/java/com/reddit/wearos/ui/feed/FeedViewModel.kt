package com.reddit.wearos.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.model.RedditPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FeedUiState {
    object Loading : FeedUiState()
    data class Success(
        val posts: List<RedditPost>,
        val subreddit: String,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true
    ) : FeedUiState()
    data class Error(val message: String) : FeedUiState()
}

class FeedViewModel(
    private val repository: RedditRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var afterCursor: String? = null
    private var isFetching = false

    init {
        loadFeed(refresh = true)
    }

    fun loadFeed(subreddit: String? = null, refresh: Boolean = false) {
        if (isFetching) return
        isFetching = true

        viewModelScope.launch {
            if (refresh) {
                _uiState.value = FeedUiState.Loading
                afterCursor = null
            }

            val targetSubreddit = subreddit ?: repository.currentSubreddit.value
            try {
                val (newPosts, nextAfter) = repository.getFeed(
                    subreddit = targetSubreddit,
                    after = if (refresh) null else afterCursor
                )
                afterCursor = nextAfter

                val currentState = _uiState.value
                val combinedPosts = if (refresh || currentState !is FeedUiState.Success) {
                    newPosts
                } else {
                    currentState.posts + newPosts
                }

                _uiState.value = FeedUiState.Success(
                    posts = combinedPosts,
                    subreddit = targetSubreddit,
                    isLoadingMore = false,
                    hasMore = nextAfter != null
                )
            } catch (e: Exception) {
                if (_uiState.value !is FeedUiState.Success) {
                    _uiState.value = FeedUiState.Error(e.localizedMessage ?: "Error al cargar feed")
                }
            } finally {
                isFetching = false
            }
        }
    }

    fun vote(post: RedditPost, direction: Int) {
        viewModelScope.launch {
            val success = repository.vote(post, direction)
            if (success) {
                val state = _uiState.value
                if (state is FeedUiState.Success) {
                    val updatedPosts = state.posts.map { current ->
                        if (current.id == post.id) {
                            val newLikes = when (direction) {
                                1 -> true
                                -1 -> false
                                else -> null
                            }
                            val scoreDiff = when {
                                post.likes == true && direction == 0 -> -1
                                post.likes == true && direction == -1 -> -2
                                post.likes == false && direction == 1 -> 2
                                post.likes == false && direction == 0 -> 1
                                post.likes == null && direction == 1 -> 1
                                post.likes == null && direction == -1 -> -1
                                else -> 0
                            }
                            current.copy(likes = newLikes, score = current.score + scoreDiff)
                        } else {
                            current
                        }
                    }
                    _uiState.value = state.copy(posts = updatedPosts)
                }
            }
        }
    }
}
