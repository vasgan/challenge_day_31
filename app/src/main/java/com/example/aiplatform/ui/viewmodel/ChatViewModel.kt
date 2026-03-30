package com.example.aiplatform.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiplatform.agent.AgentOrchestrator
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val projectId: String,
    private val chatId: String,
    private val chatRepository: ChatRepository,
    private val orchestrator: AgentOrchestrator
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    val messages: StateFlow<List<Message>> = chatRepository.observeMessages(chatId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun send(input: String) {
        if (_isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            try {
                orchestrator.sendMessage(projectId, chatId, input)
            } catch (t: Throwable) {
                _error.value = t.message ?: "Unknown error"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
