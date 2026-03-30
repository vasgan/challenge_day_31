package com.example.aiplatform.data.repository

import com.example.aiplatform.data.local.dao.ChatDao
import com.example.aiplatform.data.local.dao.MessageDao
import com.example.aiplatform.data.mapper.toDomain
import com.example.aiplatform.data.mapper.toEntity
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepositoryImpl(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) : ChatRepository {
    override fun observeChats(projectId: String): Flow<List<Chat>> = chatDao.observeChats(projectId).map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun getChat(chatId: String): Chat? = chatDao.getChat(chatId)?.toDomain()

    override suspend fun createChat(chat: Chat) {
        chatDao.upsert(chat.toEntity())
    }

    override fun observeMessages(chatId: String): Flow<List<Message>> = messageDao.observeMessages(chatId).map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun getMessages(chatId: String): List<Message> = messageDao.getMessages(chatId).map { it.toDomain() }

    override suspend fun addMessage(message: Message) {
        messageDao.upsert(message.toEntity())
    }

    override suspend fun archiveMessages(chatId: String, messageIds: List<String>) {
        if (messageIds.isNotEmpty()) messageDao.archiveMessages(messageIds)
    }

    override suspend fun deleteMessages(messageIds: List<String>) {
        if (messageIds.isNotEmpty()) messageDao.deleteMessages(messageIds)
    }
}
