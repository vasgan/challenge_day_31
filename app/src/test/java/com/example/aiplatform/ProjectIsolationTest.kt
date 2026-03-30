package com.example.aiplatform

import com.example.aiplatform.agent.AgentOrchestrator
import com.example.aiplatform.agent.ChatAgent
import com.example.aiplatform.agent.McpAgent
import com.example.aiplatform.agent.MemoryAgent
import com.example.aiplatform.agent.RagAgent
import com.example.aiplatform.assistant.DeveloperAssistantHandler
import com.example.aiplatform.assistant.DeveloperAssistantResult
import com.example.aiplatform.data.mcp.GitBranchTool
import com.example.aiplatform.data.memory.ProjectMemoryManager
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.McpConnection
import com.example.aiplatform.domain.model.McpConnectionType
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.MessageRole
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.McpRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIsolationTest {
    @Test
    fun `orchestrator context includes only active chat messages`() = runTest {
        val projectRepo = FakeProjectRepository(Project("p1", "P", "", ProjectTextModel.GPT_5_MINI, 0, ""))
        val chatRepo = FakeChatRepository(
            chats = mapOf(
                "c1" to Chat("c1", "p1", "Chat 1"),
                "c2" to Chat("c2", "p1", "Chat 2")
            ),
            initial = mapOf(
                "c1" to mutableListOf(
                    Message("a", "c1", MessageRole.USER, "only-chat1", "{}", 1)
                ),
                "c2" to mutableListOf(
                    Message("b", "c2", MessageRole.USER, "private-chat2", "{}", 1)
                )
            )
        )
        val openAi = CaptureOpenAiRepository()

        val orchestrator = AgentOrchestrator(
            projectRepository = projectRepo,
            chatRepository = chatRepo,
            openAiRepository = openAi,
            chatAgent = ChatAgent(chatRepo),
            ragAgent = RagAgent(FakeRagRepository()),
            mcpAgent = McpAgent(FakeMcpRepository(), GitBranchTool()),
            memoryAgent = MemoryAgent(ProjectMemoryManager(chatRepo, FakeMemoryRepository(), openAi)),
            developerAssistantHandler = NoopDeveloperAssistantHandler()
        )

        orchestrator.sendMessage("p1", "c1", "new")

        assertTrue(openAi.lastContext.contains("only-chat1"))
        assertFalse(openAi.lastContext.contains("private-chat2"))
    }

    private class FakeProjectRepository(private val project: Project) : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = emptyFlow()
        override suspend fun getProject(projectId: String): Project? = if (projectId == project.id) project else null
        override suspend fun createProject(project: Project) {}
        override suspend fun updateProjectModel(projectId: String, model: ProjectTextModel) {}
        override suspend fun updateProjectRootPath(projectId: String, rootPath: String) {}
    }

    private class FakeChatRepository(
        private val chats: Map<String, Chat>,
        initial: Map<String, MutableList<Message>>
    ) : ChatRepository {
        val messages = initial.toMutableMap()

        override fun observeChats(projectId: String): Flow<List<Chat>> = emptyFlow()
        override suspend fun getChat(chatId: String): Chat? = chats[chatId]
        override suspend fun createChat(chat: Chat) {}
        override fun observeMessages(chatId: String): Flow<List<Message>> = emptyFlow()
        override suspend fun getMessages(chatId: String): List<Message> = messages[chatId].orEmpty().sortedBy { it.createdAt }
        override suspend fun addMessage(message: Message) { messages.getOrPut(message.chatId) { mutableListOf() }.add(message) }
        override suspend fun archiveMessages(chatId: String, messageIds: List<String>) {}
        override suspend fun deleteMessages(messageIds: List<String>) {}
    }

    private class CaptureOpenAiRepository : OpenAiRepository {
        var lastContext: String = ""

        override suspend fun responses(
            model: ProjectTextModel,
            systemPrompt: String,
            context: String,
            userInput: String
        ): String {
            lastContext = context
            return "ok"
        }

        override suspend fun summarizeMemory(
            model: ProjectTextModel,
            currentSummary: String,
            archivedConversation: String
        ): String = "summary"

        override suspend fun embeddings(input: List<String>): List<List<Double>> = emptyList()
    }

    private class FakeMemoryRepository : MemoryRepository {
        override suspend fun getMemory(projectId: String): ProjectMemory? = null
        override suspend fun upsertMemory(memory: ProjectMemory) {}
    }

    private class FakeRagRepository : RagRepository {
        override fun observeIndexes(projectId: String): Flow<List<RagIndex>> = emptyFlow()
        override suspend fun listActiveIndexes(projectId: String): List<RagIndex> = emptyList()
        override suspend fun upsertIndex(index: RagIndex) {}
        override suspend fun addDocuments(index: RagIndex, chunks: List<RagDocumentChunk>) {}
        override suspend fun retrieve(projectId: String, query: String, topK: Int): List<RagChunk> = emptyList()
    }

    private class FakeMcpRepository : McpRepository {
        override fun observeConnections(projectId: String) = emptyFlow<List<McpConnection>>()
        override suspend fun listConnections(projectId: String) = emptyList<McpConnection>()
        override suspend fun listConnections(projectId: String, type: McpConnectionType) = emptyList<McpConnection>()
        override suspend fun upsertConnection(connection: McpConnection) {}
    }

    private class NoopDeveloperAssistantHandler : DeveloperAssistantHandler {
        override suspend fun handleHelp(projectId: String, chatId: String, userQuestion: String): DeveloperAssistantResult {
            return DeveloperAssistantResult("help", false, false)
        }
    }
}
