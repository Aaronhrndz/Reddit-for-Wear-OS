package com.reddit.wearos.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reddit.wearos.data.RedditRepository
import com.reddit.wearos.data.model.RedditMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class InboxUiState {
    object Loading : InboxUiState()
    data class Success(val messages: List<RedditMessage>) : InboxUiState()
    data class Error(val message: String) : InboxUiState()
}

class InboxViewModel(
    private val repository: RedditRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<InboxUiState>(InboxUiState.Loading)
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        loadInbox()
    }

    fun loadInbox() {
        viewModelScope.launch {
            _uiState.value = InboxUiState.Loading
            try {
                val list = repository.getInbox()
                _uiState.value = InboxUiState.Success(list)
            } catch (e: Exception) {
                _uiState.value = InboxUiState.Error(e.localizedMessage ?: "Error al cargar mensajes")
            }
        }
    }

    fun replyToMessage(thingId: String, text: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = repository.addComment(thingId, text)
            if (success) {
                loadInbox()
            }
            onComplete?.invoke(success)
        }
    }
}
