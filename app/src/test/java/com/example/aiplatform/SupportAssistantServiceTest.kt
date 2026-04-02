package com.example.aiplatform

import com.example.aiplatform.assistant.SupportAssistantPromptBuilder
import com.example.aiplatform.assistant.SupportAssistantService
import com.example.aiplatform.data.mcp.support.SupportMcpServer
import com.example.aiplatform.data.mcp.support.SupportMcpToolCall
import com.example.aiplatform.data.mcp.support.SupportMcpToolData
import com.example.aiplatform.data.mcp.support.SupportMcpToolExecutor
import com.example.aiplatform.data.mcp.support.SupportMcpToolResult
import com.example.aiplatform.data.mcp.support.SupportMcpTools
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.MessageRole
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.model.SupportContext
import com.example.aiplatform.domain.model.SupportTicket
import com.example.aiplatform.domain.model.SupportUser
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportAssistantServiceTest {

    @Test
    fun `prompt includes user and ticket context`() = runTest {
        val fixture = fixture()

        val result = fixture.service.answer("project-1", "chat-1", "Почему не работает авторизация?")

        assertTrue(result.usedMcp)
        assertTrue(fixture.openAi.lastContext.contains("Active user context"))
        assertTrue(fixture.openAi.lastContext.contains("- id: u1"))
        assertTrue(fixture.openAi.lastContext.contains("- id: t1"))
        assertTrue(fixture.openAi.lastContext.contains("Login fails"))
    }

    @Test
    fun `prompt includes support rag chunks`() = runTest {
        val fixture = fixture()
        fixture.ragRepository.chunks = listOf(
            RagChunk(
                id = "c1",
                indexId = "idx",
                projectId = "project-1",
                content = "Auth troubleshooting steps from docs",
                embedding = listOf(1.0),
                source = "support_docs|support_docs.md",
                section = "part-1"
            )
        )

        val result = fixture.service.answer("project-1", "chat-1", "Как решить проблему авторизации?")

        assertTrue(result.usedRag)
        assertTrue(fixture.openAi.lastContext.contains("Auth troubleshooting steps from docs"))
        assertTrue(fixture.openAi.lastContext.contains("Source: support_docs|support_docs.md"))
    }

    @Test
    fun `fallback when context missing`() = runTest {
        val fixture = fixture()
        fixture.mcpExecutor.failBuildContext = true
        fixture.ragRepository.chunks = emptyList()

        val result = fixture.service.answer("project-1", "chat-1", "Что делать?")

        assertTrue(result.answer.contains("Недостаточно данных"))
        assertFalse(result.usedMcp)
        assertFalse(result.usedRag)
        assertEquals(0, fixture.openAi.calls)
    }

    @Test
    fun `no crash on mcp and rag failures`() = runTest {
        val fixture = fixture()
        fixture.mcpExecutor.failBuildContext = true
        fixture.ragRepository.throwOnRetrieve = true

        val result = fixture.service.answer("project-1", "chat-1", "Почему ошибка 401?")

        assertTrue(result.answer.contains("Недостаточно данных"))
        assertEquals(0, fixture.openAi.calls)
    }

    private fun fixture(): Fixture {
        val projectRepository = FakeProjectRepository()
        val chatRepository = FakeChatRepository().apply {
            messagesByChat["chat-1"] = mutableListOf(
                Message("m1", "chat-1", MessageRole.USER, "/support_user u1", "{}", 1L),
                Message("m2", "chat-1", MessageRole.USER, "/support_ticket t1", "{}", 2L)
            )
        }
        val memoryRepository = FakeMemoryRepository()
        val ragRepository = FakeRagRepository()
        val openAi = FakeOpenAiRepository()
        val mcpExecutor = FakeSupportMcpExecutor()
        val mcpServer = SupportMcpServer(mcpExecutor)

        val service = SupportAssistantService(
            projectRepository = projectRepository,
            chatRepository = chatRepository,
            memoryRepository = memoryRepository,
            ragRepository = ragRepository,
            openAiRepository = openAi,
            supportMcpServer = mcpServer,
            promptBuilder = SupportAssistantPromptBuilder()
        )

        return Fixture(service, ragRepository, openAi, mcpExecutor)
    }

    private data class Fixture(
        val service: SupportAssistantService,
        val ragRepository: FakeRagRepository,
        val openAi: FakeOpenAiRepository,
        val mcpExecutor: FakeSupportMcpExecutor
    )

    private class FakeProjectRepository : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = emptyFlow()
        override suspend fun getProject(projectId: String): Project? =
            Project(projectId, "Support Project", "", ProjectTextModel.GPT_5_MINI, 0L, "")

        override suspend fun createProject(project: Project) {}
        override suspend fun updateProjectModel(projectId: String, model: ProjectTextModel) {}
        override suspend fun updateProjectRootPath(projectId: String, rootPath: String) {}
    }

    private class FakeChatRepository : ChatRepository {
        val messagesByChat = mutableMapOf<String, MutableList<Message>>()

        override fun observeChats(projectId: String): Flow<List<Chat>> = emptyFlow()
        override suspend fun getChat(chatId: String): Chat? = Chat(chatId, "project-1", "General")
        override suspend fun createChat(chat: Chat) {}
        override fun observeMessages(chatId: String): Flow<List<Message>> = emptyFlow()
        override suspend fun getMessages(chatId: String): List<Message> = messagesByChat[chatId].orEmpty().sortedBy { it.createdAt }
        override suspend fun addMessage(message: Message) {
            messagesByChat.getOrPut(message.chatId) { mutableListOf() }.add(message)
        }
        override suspend fun archiveMessages(chatId: String, messageIds: List<String>) {}
        override suspend fun deleteMessages(messageIds: List<String>) {}
    }

    private class FakeMemoryRepository : MemoryRepository {
        override suspend fun getMemory(projectId: String): ProjectMemory? =
            ProjectMemory(projectId, "Support memory summary", 1L)

        override suspend fun upsertMemory(memory: ProjectMemory) {}
    }

    private class FakeRagRepository : RagRepository {
        var chunks: List<RagChunk> = emptyList()
        var throwOnRetrieve: Boolean = false

        override fun observeIndexes(projectId: String): Flow<List<RagIndex>> = emptyFlow()
        override suspend fun listActiveIndexes(projectId: String): List<RagIndex> = emptyList()
        override suspend fun upsertIndex(index: RagIndex) {}
        override suspend fun addDocuments(index: RagIndex, chunks: List<RagDocumentChunk>) {}

        override suspend fun retrieve(projectId: String, query: String, topK: Int): List<RagChunk> {
            if (throwOnRetrieve) {
                error("retrieve failed")
            }
            return chunks.take(topK)
        }
    }

    private class FakeOpenAiRepository : OpenAiRepository {
        var calls = 0
        var lastContext: String = ""

        override suspend fun responses(
            model: ProjectTextModel,
            systemPrompt: String,
            context: String,
            userInput: String
        ): String {
            calls += 1
            lastContext = context
            return "Support answer"
        }

        override suspend fun summarizeMemory(
            model: ProjectTextModel,
            currentSummary: String,
            archivedConversation: String
        ): String = currentSummary

        override suspend fun embeddings(input: List<String>): List<List<Double>> = emptyList()
    }

    private class FakeSupportMcpExecutor : SupportMcpToolExecutor {
        var failBuildContext: Boolean = false

        override suspend fun execute(call: SupportMcpToolCall): SupportMcpToolResult {
            return when (call.tool) {
                SupportMcpTools.BUILD_CONTEXT -> {
                    if (failBuildContext) {
                        SupportMcpToolResult(success = false, error = "context failed")
                    } else {
                        SupportMcpToolResult(
                            success = true,
                            data = SupportMcpToolData.ContextPayload(
                                SupportContext(
                                    user = SupportUser(
                                        id = "u1",
                                        name = "Ivan",
                                        email = "ivan@example.com",
                                        segment = "pro",
                                        metadata = mapOf("locale" to "ru")
                                    ),
                                    ticket = SupportTicket(
                                        id = "t1",
                                        userId = "u1",
                                        subject = "Login fails",
                                        status = "open",
                                        priority = "high",
                                        description = "Cannot login after reset",
                                        metadata = mapOf("channel" to "email")
                                    ),
                                    relatedTickets = listOf(
                                        SupportTicket(
                                            id = "t1",
                                            userId = "u1",
                                            subject = "Login fails",
                                            status = "open",
                                            priority = "high",
                                            description = "Cannot login after reset",
                                            metadata = emptyMap()
                                        )
                                    )
                                )
                            )
                        )
                    }
                }
                else -> SupportMcpToolResult(success = false, error = "Unexpected tool: ${call.tool}")
            }
        }
    }
}
