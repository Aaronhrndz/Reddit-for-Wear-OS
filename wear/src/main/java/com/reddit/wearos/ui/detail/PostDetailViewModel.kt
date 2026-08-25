package com.reddit.wearos.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.model.RedditComment
import com.reddit.wearos.data.model.RedditListing
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.data.network.NetworkClient
import com.reddit.wearos.ui.comments.FlatComment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

sealed class PostDetailUiState {
    object Loading : PostDetailUiState()
    data class Success(
        val post: RedditPost,
        val comments: List<FlatComment> = emptyList(),
        val commentsLoading: Boolean = false
    ) : PostDetailUiState()
    data class Error(val message: String) : PostDetailUiState()
}

class PostDetailViewModel(
    private val repository: RedditRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PostDetailUiState>(PostDetailUiState.Loading)
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    fun loadPost(postId: String, subreddit: String) {
        viewModelScope.launch {
            _uiState.value = PostDetailUiState.Loading
            try {
                val (post, comments) = repository.getComments(subreddit, postId)
                if (post != null) {
                    val flattened = flattenComments(comments, depth = 0)
                    _uiState.value = PostDetailUiState.Success(
                        post = post,
                        comments = flattened,
                        commentsLoading = false
                    )
                } else {
                    _uiState.value = PostDetailUiState.Error("Post no encontrado")
                }
            } catch (e: Exception) {
                _uiState.value = PostDetailUiState.Error(e.localizedMessage ?: "Error al cargar post")
            }
        }
    }

    fun setPostDirectly(post: RedditPost) {
        _uiState.value = PostDetailUiState.Success(post = post, comments = emptyList(), commentsLoading = true)
        viewModelScope.launch {
            try {
                val (_, comments) = repository.getComments(post.subreddit, post.id)
                val flattened = flattenComments(comments, depth = 0)
                val current = _uiState.value
                if (current is PostDetailUiState.Success) {
                    _uiState.value = current.copy(comments = flattened, commentsLoading = false)
                }
            } catch (_: Exception) {
                val current = _uiState.value
                if (current is PostDetailUiState.Success) {
                    _uiState.value = current.copy(commentsLoading = false)
                }
            }
        }
    }

    fun vote(direction: Int) {
        val state = _uiState.value
        if (state is PostDetailUiState.Success) {
            val post = state.post
            viewModelScope.launch {
                val success = repository.vote(post, direction)
                if (success) {
                    val newLikes = when (direction) {
                        1 -> true
                        -1 -> false
                        else -> null
                    }
                    val scoreDiff = when {
                        post.likes == true && direction == 0 -> -1
                        post.likes == true && direction == -1 -> -2
                        post.likes == false && direction == 0 -> 1
                        post.likes == false && direction == 1 -> 2
                        post.likes == null && direction == 1 -> 1
                        post.likes == null && direction == -1 -> -1
                        else -> 0
                    }
                    _uiState.value = state.copy(
                        post = post.copy(
                            likes = newLikes,
                            score = post.score + scoreDiff
                        )
                    )
                }
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
                likes = comment.likes,
                score = comment.score
            )
            val success = repository.vote(postEquivalent, direction)
            if (success) {
                val state = _uiState.value
                if (state is PostDetailUiState.Success) {
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
                                flat.comment.likes == false && direction == 0 -> 1
                                flat.comment.likes == false && direction == 1 -> 2
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

    fun toggleSave() {
        val state = _uiState.value
        if (state is PostDetailUiState.Success) {
            val post = state.post
            val newSaveState = !post.saved
            viewModelScope.launch {
                val success = repository.savePost(post, newSaveState)
                if (success) {
                    _uiState.value = state.copy(post = post.copy(saved = newSaveState))
                }
            }
        }
    }

    fun submitComment(thingId: String, text: String, onComplete: ((Boolean) -> Unit)? = null) {
        val state = _uiState.value
        if (state is PostDetailUiState.Success) {
            viewModelScope.launch {
                val success = repository.addComment(thingId, text)
                if (success) {
                    try {
                        val (_, comments) = repository.getComments(state.post.subreddit, state.post.id)
                        val flattened = flattenComments(comments, depth = 0)
                        val current = _uiState.value
                        if (current is PostDetailUiState.Success) {
                            _uiState.value = current.copy(comments = flattened)
                        }
                    } catch (_: Exception) {}
                }
                onComplete?.invoke(success)
            }
        }
    }

    private fun flattenComments(
        comments: List<RedditComment>,
        depth: Int,
        passThrough: List<Boolean> = emptyList()
    ): List<FlatComment> {
        val result = mutableListOf<FlatComment>()
        for (i in comments.indices) {
            val c = comments[i]
            val isLast = (i == comments.size - 1)
            val replies = if (c.replies != null) {
                com.reddit.wearos.data.model.parseCommentsFromListingElement(c.replies)
            } else {
                emptyList()
            }
            val hasReplies = replies.isNotEmpty()

            result.add(
                FlatComment(
                    comment = c.copy(depth = depth),
                    depth = depth,
                    hasReplies = hasReplies,
                    isLastChild = isLast,
                    passThroughColumns = passThrough
                )
            )

            if (hasReplies) {
                val nextPassThrough = if (depth == 0) {
                    emptyList()
                } else {
                    passThrough + (!isLast)
                }
                result.addAll(flattenComments(replies, depth + 1, nextPassThrough))
            }
        }
        return result
    }
}
