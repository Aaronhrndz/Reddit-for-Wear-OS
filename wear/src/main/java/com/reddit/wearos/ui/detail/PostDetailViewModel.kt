package com.reddit.wearos.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.model.RedditComment
import com.reddit.wearos.data.model.RedditPost
import com.reddit.wearos.ui.comments.FlatComment
import com.reddit.wearos.ui.comments.flattenCommentTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
                    val flattened = flattenCommentTree(comments)
                    _uiState.value = PostDetailUiState.Success(
                        post = post,
                        comments = flattened,
                        commentsLoading = false
                    )
                } else {
                    _uiState.value = PostDetailUiState.Error("Publicación no encontrada")
                }
            } catch (e: Exception) {
                _uiState.value = PostDetailUiState.Error(e.message ?: "Error al cargar publicación")
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
                        val flattened = flattenCommentTree(comments)
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

    fun editComment(commentId: String, newText: String) {
        val state = _uiState.value
        if (state is PostDetailUiState.Success) {
            // Actualización optimista inmediata
            val cleanId = commentId.removePrefix("t1_")
            val optimistic = state.comments.map { flat ->
                if (flat.comment.id == cleanId) {
                    flat.copy(comment = flat.comment.copy(body = newText))
                } else flat
            }
            _uiState.value = state.copy(comments = optimistic)
            viewModelScope.launch {
                val success = repository.editComment("t1_$cleanId", newText)
                if (success) {
                    try {
                        val (_, comments) = repository.getComments(state.post.subreddit, state.post.id)
                        val current = _uiState.value
                        if (current is PostDetailUiState.Success) {
                            _uiState.value = current.copy(comments = flattenCommentTree(comments))
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun deleteComment(commentId: String) {
        val state = _uiState.value
        if (state is PostDetailUiState.Success) {
            val cleanId = commentId.removePrefix("t1_")
            val optimistic = state.comments.filterNot { it.comment.id == cleanId }
            _uiState.value = state.copy(comments = optimistic)
            viewModelScope.launch {
                repository.deleteComment("t1_$cleanId")
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

    fun vote(direction: Int) {
        val state = _uiState.value
        if (state is PostDetailUiState.Success) {
            val post = state.post
            val currentLikes = post.likes
            val newLikes = when (direction) {
                1 -> true
                -1 -> false
                else -> null
            }
            val scoreDiff = when {
                currentLikes == true && direction == 0 -> -1
                currentLikes == true && direction == -1 -> -2
                currentLikes == false && direction == 0 -> 1
                currentLikes == false && direction == 1 -> 2
                currentLikes == null && direction == 1 -> 1
                currentLikes == null && direction == -1 -> -1
                else -> 0
            }
            val updatedPost = post.copy(likes = newLikes, score = post.score + scoreDiff)
            _uiState.value = state.copy(post = updatedPost)

            viewModelScope.launch {
                val success = repository.vote(post, direction)
                if (!success) {
                    _uiState.value = state
                }
            }
        }
    }
}
