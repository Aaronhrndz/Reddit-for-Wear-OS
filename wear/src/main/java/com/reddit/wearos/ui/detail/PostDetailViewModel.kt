package com.reddit.wearos.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.model.RedditPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PostDetailUiState {
    object Loading : PostDetailUiState()
    data class Success(val post: RedditPost) : PostDetailUiState()
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
                val (post, _) = repository.getComments(subreddit, postId)
                if (post != null) {
                    _uiState.value = PostDetailUiState.Success(post)
                } else {
                    _uiState.value = PostDetailUiState.Error("Post no encontrado")
                }
            } catch (e: Exception) {
                _uiState.value = PostDetailUiState.Error(e.localizedMessage ?: "Error al cargar post")
            }
        }
    }

    fun setPostDirectly(post: RedditPost) {
        _uiState.value = PostDetailUiState.Success(post)
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
                    _uiState.value = PostDetailUiState.Success(post.copy(likes = newLikes))
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
                    _uiState.value = PostDetailUiState.Success(post.copy(saved = newSaveState))
                }
            }
        }
    }
}
