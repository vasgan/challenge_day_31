package com.example.aiplatform

import com.example.aiplatform.data.memory.ProjectMemoryManager
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.MessageRole
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPolicyTest {
    @Test
    fun `summarization runs when messages exceed short 10 + archived 30`() = runTest {
        val chatRepo = FakeChatRepository()
        val memoryRepo = FakeMemoryRepository()
        val openAi = FakeOpenAiRepository()
        val manager = ProjectMemoryManager(chatRepo, memoryRepo, openAi)

        val project = Project("p1", "P", "", ProjectTextModel.GPT_5_MINI, 0)
        val chatId = "chat-1"
        chatRepo.chats[chatId] = Chat(chatId, "p1", "General")

        repeat(45) { index ->
            chatRepo.addMessage(
                Message(
                    id = "m$index",
                    chatId = chatId,
                    role = MessageRole.USER,
                    content = "msg-$index",
                    metadata = "{}",
                    createdAt = index.toLong()
                )
            )
        }

        manager.prepare(project, chatId)

        assertEquals(1, openAi.summarizeCalls)
        assertTrue(memoryRepo.memoryByProject["p1"]?.summary?.isNotBlank() == true)
        assertEquals(40, chatRepo.messagesByChat[chatId]?.size)
    }

    private class FakeChatRepository : ChatRepository {
        val chats = mutableMapOf<String, Chat>()
        val messagesByChat = mutableMapOf<String, MutableList<Message>>()

        override fun observeChats(projectId: String): Flow<List<Chat>> = emptyFlow()

        override suspend fun getChat(chatId: String): Chat? = chats[chatId]

        override suspend fun createChat(chat: Chat) {
            chats[chat.id] = chat
        }

        override fun observeMessages(chatId: String): Flow<List<Message>> = emptyFlow()

        override suspend fun getMessages(chatId: String): List<Message> =
            messagesByChat[chatId].orEmpty().sortedBy { it.createdAt }

        override suspend fun addMessage(message: Message) {
            messagesByChat.getOrPut(message.chatId) { mutableListOf() }.add(message)
        }

        override suspend fun archiveMessages(chatId: String, messageIds: List<String>) {}

        override suspend fun deleteMessages(messageIds: List<String>) {
            messagesByChat.values.forEach { list -> list.removeAll { it.id in messageIds } }
        }
    }

    private class FakeMemoryRepository : MemoryRepository {
        val memoryByProject = mutableMapOf<String, ProjectMemory>()

        override suspend fun getMemory(projectId: String): ProjectMemory? = memoryByProject[projectId]

        override suspend fun upsertMemory(memory: ProjectMemory) {
            memoryByProject[memory.projectId] = memory
        }
    }

    private class FakeOpenAiRepository : OpenAiRepository {
        var summarizeCalls = 0

        override suspend fun responses(
            model: ProjectTextModel,
            systemPrompt: String,
            context: String,
            userInput: String
        ): String = "ok"

        override suspend fun summarizeMemory(
            model: ProjectTextModel,
            currentSummary: String,
            archivedConversation: String
        ): String {
            summarizeCalls += 1
            return "summary-$summarizeCalls"
        }

        override suspend fun embeddings(input: List<String>): List<List<Double>> = emptyList()
    }
}
