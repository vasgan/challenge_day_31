package com.example.aiplatform.domain.repository

import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeChats(projectId: String): Flow<List<Chat>>
    suspend fun getChat(chatId: String): Chat?
    suspend fun createChat(chat: Chat)
    fun observeMessages(chatId: String): Flow<List<Message>>
    suspend fun getMessages(chatId: String): List<Message>
    suspend fun addMessage(message: Message)
    suspend fun archiveMessages(chatId: String, messageIds: List<String>)
    suspend fun deleteMessages(messageIds: List<String>)
}
