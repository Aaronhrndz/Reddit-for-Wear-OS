package com.reddit.wearos.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reddit.wearos.auth.TokenStorage
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.model.RedditPost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SavedUiState {
    object Loading : SavedUiState()
    data class Success(val posts: List<RedditPost>, val hasMore: Boolean) : SavedUiState()
    data class Error(val message: String) : SavedUiState()
}

class SavedViewModel(
    private val repository: RedditRepository,
    private val tokenStorage: TokenStorage? = null,
    private val fallbackUsername: String = "me"
) : ViewModel() {

    private val _uiState = MutableStateFlow<SavedUiState>(SavedUiState.Loading)
    val uiState: StateFlow<SavedUiState> = _uiState.asStateFlow()

    private var after: String? = null
    private var isLoadingMore = false
    private val currentPosts = mutableListOf<RedditPost>()

    init {
        loadSavedPosts(forceRefresh = true)
    }

    private fun resolveUsername(): String {
        return tokenStorage?.getUsername()?.takeIf { it.isNotBlank() } ?: fallbackUsername
    }

    fun loadSavedPosts(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value is SavedUiState.Success && currentPosts.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.value = SavedUiState.Loading
            try {
                val user = resolveUsername()
                val (posts, nextAfter) = repository.getSavedPosts(user)
                currentPosts.clear()
                currentPosts.addAll(posts)
                after = nextAfter
                _uiState.value = SavedUiState.Success(currentPosts.toList(), after != null)
            } catch (e: Exception) {
                _uiState.value = SavedUiState.Error(e.message ?: "Error al cargar guardados")
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || after == null) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val user = resolveUsername()
                val (posts, nextAfter) = repository.getSavedPosts(user, after)
                currentPosts.addAll(posts)
                after = nextAfter
                _uiState.value = SavedUiState.Success(currentPosts.toList(), after != null)
            } catch (_: Exception) {
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun vote(post: RedditPost, direction: Int) {
        viewModelScope.launch {
            val updatedLikes = when (direction) {
                1 -> true
                -1 -> false
                else -> null
            }
            val index = currentPosts.indexOfFirst { it.id == post.id }
            if (index != -1) {
                val current = currentPosts[index]
                val scoreDiff = when {
                    current.likes == null && direction == 1 -> 1
                    current.likes == null && direction == -1 -> -1
                    current.likes == true && direction == 0 -> -1
                    current.likes == false && direction == 0 -> 1
                    current.likes == true && direction == -1 -> -2
                    current.likes == false && direction == 1 -> 2
                    else -> 0
                }
                currentPosts[index] = current.copy(
                    likes = updatedLikes,
                    score = current.score + scoreDiff
                )
                _uiState.value = SavedUiState.Success(currentPosts.toList(), after != null)
            }
            repository.vote(post, direction)
        }
    }

    fun toggleSave(post: RedditPost) {
        viewModelScope.launch {
            val newSaved = !post.saved
            val index = currentPosts.indexOfFirst { it.id == post.id }
            if (index != -1) {
                currentPosts[index] = currentPosts[index].copy(saved = newSaved)
                _uiState.value = SavedUiState.Success(currentPosts.toList(), after != null)
            }
            repository.savePost(post, newSaved)
        }
    }
}
