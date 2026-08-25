package com.reddit.wearos.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.model.RedditComment
import com.reddit.wearos.data.model.RedditListing
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.data.network.NetworkClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

data class FlatComment(
    val comment: RedditComment,
    val depth: Int,
    val isCollapsed: Boolean = false
)

sealed class CommentsUiState {
    object Loading : CommentsUiState()
    data class Success(val comments: List<FlatComment>) : CommentsUiState()
    data class Error(val message: String) : CommentsUiState()
}

class CommentsViewModel(
    private val repository: RedditRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CommentsUiState>(CommentsUiState.Loading)
    val uiState: StateFlow<CommentsUiState> = _uiState.asStateFlow()

    private var rawCommentTree: List<RedditComment> = emptyList()

    fun loadComments(postId: String, subreddit: String) {
        viewModelScope.launch {
            _uiState.value = CommentsUiState.Loading
            try {
                val (_, comments) = repository.getComments(subreddit, postId)
                rawCommentTree = comments
                val flattened = flattenComments(comments, depth = 0)
                _uiState.value = CommentsUiState.Success(flattened)
            } catch (e: Exception) {
                _uiState.value = CommentsUiState.Error(e.localizedMessage ?: "Error al cargar comentarios")
            }
        }
    }

    fun voteComment(comment: RedditComment, direction: Int) {
        viewModelScope.launch {
            val postEquivalent = RedditPost(
                id = comment.id,
                name = "t1_${comment.id}",
                title = "",
                author = comment.author,
                subreddit = "",
                subredditPrefixed = "",
                selfText = comment.body,
                score = comment.score
            )
            val success = repository.vote(postEquivalent, direction)
            if (success) {
                val state = _uiState.value
                if (state is CommentsUiState.Success) {
                    val updated = state.comments.map { flat ->
                        if (flat.comment.id == comment.id) {
                            val newLikes = when (direction) {
                                1 -> true
                                -1 -> false
                                else -> null
                            }
                            val scoreDiff = when {
                                flat.comment.likes == true && direction == 0 -> -1
                                flat.comment.likes == true && direction == -1 -> -2
                                flat.comment.likes == false && direction == 1 -> 2
                                flat.comment.likes == false && direction == 0 -> 1
                                flat.comment.likes == null && direction == 1 -> 1
                                flat.comment.likes == null && direction == -1 -> -1
                                else -> 0
                            }
                            flat.copy(comment = flat.comment.copy(likes = newLikes, score = flat.comment.score + scoreDiff))
                        } else {
                            flat
                        }
                    }
                    _uiState.value = state.copy(comments = updated)
                }
            }
        }
    }

    fun submitComment(postId: String, subreddit: String, thingId: String, text: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = repository.addComment(thingId, text)
            if (success) {
                loadComments(postId, subreddit)
            }
            onComplete?.invoke(success)
        }
    }

    private fun flattenComments(comments: List<RedditComment>, depth: Int): List<FlatComment> {
        val result = mutableListOf<FlatComment>()
        for (c in comments) {
            result.add(FlatComment(comment = c.copy(depth = depth), depth = depth))
            if (c.replies != null) {
                val subComments = com.reddit.wearos.data.model.parseCommentsFromListingElement(c.replies)
                if (subComments.isNotEmpty()) {
                    result.addAll(flattenComments(subComments, depth + 1))
                }
            }
        }
        return result
    }
}
