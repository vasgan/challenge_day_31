package com.example.aiplatform.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ChatListViewModel(
    projectId: String,
    private val chatRepository: ChatRepository
) : ViewModel() {
    val chats: StateFlow<List<Chat>> = chatRepository.observeChats(projectId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun createChat(projectId: String, title: String) {
        viewModelScope.launch {
            chatRepository.createChat(
                Chat(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    title = title
                )
            )
        }
    }
}
