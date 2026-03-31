package com.example.aiplatform

import com.example.aiplatform.agent.AgentOrchestrator
import com.example.aiplatform.agent.ChatAgent
import com.example.aiplatform.agent.McpAgent
import com.example.aiplatform.agent.MemoryAgent
import com.example.aiplatform.agent.RagAgent
import com.example.aiplatform.assistant.DeveloperAssistantHandler
import com.example.aiplatform.assistant.DeveloperAssistantResult
import com.example.aiplatform.assistant.PullRequestListResult
import com.example.aiplatform.assistant.PullRequestReviewExecutionResult
import com.example.aiplatform.assistant.PullRequestReviewHandler
import com.example.aiplatform.data.mcp.GitBranchTool
import com.example.aiplatform.data.memory.ProjectMemoryManager
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.McpConnection
import com.example.aiplatform.domain.model.McpConnectionType
import com.example.aiplatform.domain.model.Message
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRequestReviewRoutingTest {

    @Test
    fun `review command without number routes to list mode`() = runTest {
        val fixture = fixture()

        fixture.orchestrator.sendMessage("p1", "chat", "/review_pr")

        assertEquals(1, fixture.reviewHandler.listCalls)
        assertEquals(0, fixture.reviewHandler.runCalls)
    }

    @Test
    fun `review command with number routes to run mode`() = runTest {
        val fixture = fixture()

        fixture.orchestrator.sendMessage("p1", "chat", "/review_pr 123")

        assertEquals(0, fixture.reviewHandler.listCalls)
        assertEquals(1, fixture.reviewHandler.runCalls)
        assertEquals(123, fixture.reviewHandler.lastPr)
        val last = fixture.chatRepository.getMessages("chat").last()
        assertEquals("review", last.content)
    }

    @Test
    fun `invalid review command is handled safely in chat`() = runTest {
        val fixture = fixture()

        val result = fixture.orchestrator.sendMessage("p1", "chat", "/review_pr abc")

        assertTrue(result.answer.contains("Неверный формат"))
        val last = fixture.chatRepository.getMessages("chat").last()
        assertTrue(last.content.contains("Неверный формат"))
    }

    private fun fixture(): Fixture {
        val projectRepository = FakeProjectRepository(
            listOf(Project("p1", "P1", "", ProjectTextModel.GPT_5_MINI, 0L, ""))
        )
        val chatRepository = FakeChatRepository(mapOf("chat" to Chat("chat", "p1", "General")))
        val openAiRepository = FakeOpenAiRepository()
        val reviewHandler = CaptureReviewHandler()

        val orchestrator = AgentOrchestrator(
            projectRepository = projectRepository,
            chatRepository = chatRepository,
            openAiRepository = openAiRepository,
            chatAgent = ChatAgent(chatRepository),
            ragAgent = RagAgent(FakeRagRepository()),
            mcpAgent = McpAgent(FakeMcpRepository(), GitBranchTool()),
            memoryAgent = MemoryAgent(ProjectMemoryManager(chatRepository, FakeMemoryRepository(), openAiRepository)),
            developerAssistantHandler = NoopDeveloperAssistantHandler(),
            pullRequestReviewHandler = reviewHandler
        )

        return Fixture(orchestrator, chatRepository, reviewHandler)
    }

    private data class Fixture(
        val orchestrator: AgentOrchestrator,
        val chatRepository: FakeChatRepository,
        val reviewHandler: CaptureReviewHandler
    )

    private class CaptureReviewHandler : PullRequestReviewHandler {
        var listCalls = 0
        var runCalls = 0
        var lastPr: Int? = null

        override suspend fun listOpenPrs(projectId: String): PullRequestListResult {
            listCalls += 1
            return PullRequestListResult("list", success = true)
        }

        override suspend fun reviewPr(projectId: String, chatId: String, prNumber: Int): PullRequestReviewExecutionResult {
            runCalls += 1
            lastPr = prNumber
            return PullRequestReviewExecutionResult("review", usedRag = true, usedMcp = true, postedToGithub = false)
        }
    }

    private class NoopDeveloperAssistantHandler : DeveloperAssistantHandler {
        override suspend fun handleHelp(projectId: String, chatId: String, userQuestion: String): DeveloperAssistantResult {
            return DeveloperAssistantResult("help", usedRag = false, usedMcp = false)
        }
    }

    private class FakeProjectRepository(projects: List<Project>) : ProjectRepository {
        private val byId = projects.associateBy { it.id }
        override fun observeProjects(): Flow<List<Project>> = MutableStateFlow(byId.values.toList())
        override suspend fun getProject(projectId: String): Project? = byId[projectId]
        override suspend fun createProject(project: Project) {}
        override suspend fun updateProjectModel(projectId: String, model: ProjectTextModel) {}
        override suspend fun updateProjectRootPath(projectId: String, rootPath: String) {}
    }

    private class FakeChatRepository(chats: Map<String, Chat>) : ChatRepository {
        private val chatsById = chats.toMutableMap()
        private val messages = mutableMapOf<String, MutableList<Message>>()

        override fun observeChats(projectId: String): Flow<List<Chat>> = emptyFlow()
        override suspend fun getChat(chatId: String): Chat? = chatsById[chatId]
        override suspend fun createChat(chat: Chat) {}
        override fun observeMessages(chatId: String): Flow<List<Message>> = emptyFlow()
        override suspend fun getMessages(chatId: String): List<Message> = messages[chatId].orEmpty()
        override suspend fun addMessage(message: Message) {
            messages.getOrPut(message.chatId) { mutableListOf() }.add(message)
        }
        override suspend fun archiveMessages(chatId: String, messageIds: List<String>) {}
        override suspend fun deleteMessages(messageIds: List<String>) {}
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
        override fun observeConnections(projectId: String): Flow<List<McpConnection>> = emptyFlow()
        override suspend fun listConnections(projectId: String): List<McpConnection> = emptyList()
        override suspend fun listConnections(projectId: String, type: McpConnectionType): List<McpConnection> = emptyList()
        override suspend fun upsertConnection(connection: McpConnection) {}
    }

    private class FakeOpenAiRepository : OpenAiRepository {
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
        ): String = currentSummary

        override suspend fun embeddings(input: List<String>): List<List<Double>> = emptyList()
    }
}
