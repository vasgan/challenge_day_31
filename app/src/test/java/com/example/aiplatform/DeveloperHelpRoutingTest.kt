package com.example.aiplatform

import com.example.aiplatform.agent.AgentOrchestrator
import com.example.aiplatform.agent.ChatAgent
import com.example.aiplatform.agent.McpAgent
import com.example.aiplatform.agent.MemoryAgent
import com.example.aiplatform.agent.RagAgent
import com.example.aiplatform.assistant.DeveloperAssistantHandler
import com.example.aiplatform.assistant.DeveloperAssistantPromptBuilder
import com.example.aiplatform.assistant.DeveloperAssistantResult
import com.example.aiplatform.assistant.DeveloperAssistantService
import com.example.aiplatform.assistant.FileOpsAssistantHandler
import com.example.aiplatform.assistant.FileOpsResult
import com.example.aiplatform.assistant.PullRequestListResult
import com.example.aiplatform.assistant.PullRequestReviewExecutionResult
import com.example.aiplatform.assistant.PullRequestReviewHandler
import com.example.aiplatform.assistant.SupportAssistantHandler
import com.example.aiplatform.assistant.SupportAssistantResult
import com.example.aiplatform.data.mcp.GitBranchTool
import com.example.aiplatform.data.memory.ProjectMemoryManager
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.McpConnection
import com.example.aiplatform.domain.model.McpConnectionType
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectGithubBinding
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.McpRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectGithubBindingRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperHelpRoutingTest {

    @Test
    fun `help command routing goes to developer assistant handler`() = runTest {
        val projectRepo = FakeProjectRepository(
            listOf(Project("p1", "Project", "", ProjectTextModel.GPT_5_MINI, 0, ""))
        )
        val chatRepo = FakeChatRepository(mapOf("c1" to Chat("c1", "p1", "General")))
        val openAi = CaptureOpenAiRepository()
        val helpHandler = CaptureHelpHandler()

        val orchestrator = AgentOrchestrator(
            projectRepository = projectRepo,
            chatRepository = chatRepo,
            openAiRepository = openAi,
            chatAgent = ChatAgent(chatRepo),
            ragAgent = RagAgent(FakeRagRepository()),
            mcpAgent = McpAgent(FakeMcpRepository(), GitBranchTool()),
            memoryAgent = MemoryAgent(ProjectMemoryManager(chatRepo, FakeMemoryRepository(), openAi)),
            developerAssistantHandler = helpHandler,
            pullRequestReviewHandler = NoopPullRequestReviewHandler(),
            supportAssistantHandler = NoopSupportAssistantHandler(),
            fileOpsAssistantHandler = NoopFileOpsAssistantHandler()
        )

        val result = orchestrator.sendMessage("p1", "c1", "/help как устроен проект?")

        assertEquals("help-answer", result.answer)
        assertEquals(1, helpHandler.calls)
        assertEquals(0, openAi.responsesCalls)
    }

    private class NoopPullRequestReviewHandler : PullRequestReviewHandler {
        override suspend fun listOpenPrs(projectId: String): PullRequestListResult =
            PullRequestListResult("none", success = true)

        override suspend fun reviewPr(projectId: String, chatId: String, prNumber: Int): PullRequestReviewExecutionResult =
            PullRequestReviewExecutionResult("none", usedRag = false, usedMcp = false, postedToGithub = false)
    }

    private class NoopSupportAssistantHandler : SupportAssistantHandler {
        override suspend fun setActiveUser(projectId: String, chatId: String, userId: String): SupportAssistantResult =
            SupportAssistantResult("none", usedRag = false, usedMcp = false, activeUserId = null, activeTicketId = null)

        override suspend fun setActiveTicket(projectId: String, chatId: String, ticketId: String): SupportAssistantResult =
            SupportAssistantResult("none", usedRag = false, usedMcp = false, activeUserId = null, activeTicketId = null)

        override suspend fun answer(projectId: String, chatId: String, question: String): SupportAssistantResult =
            SupportAssistantResult("none", usedRag = false, usedMcp = false, activeUserId = null, activeTicketId = null)
    }

    private class NoopFileOpsAssistantHandler : FileOpsAssistantHandler {
        override suspend fun runTask(projectId: String, chatId: String, goal: String): FileOpsResult =
            FileOpsResult("none", success = true, changedFiles = emptyList(), openedPr = false, prUrl = null)
    }

    @Test
    fun `help uses README-derived rag context`() = runTest {
        val projectRepo = FakeProjectRepository(
            listOf(Project("p1", "Project", "", ProjectTextModel.GPT_5_MINI, 0, ""))
        )
        val ragRepo = FakeRagRepository().apply {
            chunksByProject["p1"] = listOf(
                RagChunk(
                    id = "c1",
                    indexId = "idx",
                    projectId = "p1",
                    content = "README says architecture uses orchestrator",
                    embedding = listOf(1.0),
                    source = "github_readme|octocat/Hello-World|README.md|main",
                    section = "part-1"
                )
            )
        }
        val openAi = CaptureOpenAiRepository()

        val service = DeveloperAssistantService(
            projectRepository = projectRepo,
            chatRepository = FakeChatRepository(mapOf("chat" to Chat("chat", "p1", "General"))),
            memoryRepository = FakeMemoryRepository(),
            ragRepository = ragRepo,
            projectGithubBindingRepository = FakeBindingRepository(
                ProjectGithubBinding(
                    projectId = "p1",
                    owner = "octocat",
                    repo = "Hello-World",
                    repoUrl = "https://github.com/octocat/Hello-World",
                    defaultBranch = "main",
                    readmeImportedAt = 1L,
                    ragIndexId = "idx",
                    createdAt = 1L
                )
            ),
            openAiRepository = openAi,
            promptBuilder = DeveloperAssistantPromptBuilder()
        )

        service.handleHelp("p1", "chat", "где описана архитектура?")

        assertTrue(openAi.lastContext.contains("Source: github_readme|octocat/Hello-World|README.md|main"))
        assertTrue(openAi.lastContext.contains("Current git branch: main"))
    }

    private class CaptureHelpHandler : DeveloperAssistantHandler {
        var calls = 0
        override suspend fun handleHelp(projectId: String, chatId: String, userQuestion: String): DeveloperAssistantResult {
            calls += 1
            return DeveloperAssistantResult("help-answer", usedRag = true, usedMcp = true)
        }
    }

    private class CaptureOpenAiRepository : OpenAiRepository {
        var responsesCalls = 0
        var lastContext: String = ""

        override suspend fun responses(
            model: ProjectTextModel,
            systemPrompt: String,
            context: String,
            userInput: String
        ): String {
            responsesCalls += 1
            lastContext = context
            return "ok"
        }

        override suspend fun summarizeMemory(
            model: ProjectTextModel,
            currentSummary: String,
            archivedConversation: String
        ): String = currentSummary

        override suspend fun embeddings(input: List<String>): List<List<Double>> = emptyList()
    }

    private class FakeProjectRepository(projects: List<Project>) : ProjectRepository {
        private val byId = projects.associateBy { it.id }.toMutableMap()

        override fun observeProjects(): Flow<List<Project>> = MutableStateFlow(byId.values.toList())
        override suspend fun getProject(projectId: String): Project? = byId[projectId]
        override suspend fun createProject(project: Project) { byId[project.id] = project }
        override suspend fun updateProjectModel(projectId: String, model: ProjectTextModel) {}
        override suspend fun updateProjectRootPath(projectId: String, rootPath: String) {}
    }

    private class FakeChatRepository(chats: Map<String, Chat>) : ChatRepository {
        private val chatsById = chats.toMutableMap()
        override fun observeChats(projectId: String): Flow<List<Chat>> = emptyFlow()
        override suspend fun getChat(chatId: String): Chat? = chatsById[chatId]
        override suspend fun createChat(chat: Chat) {}
        override fun observeMessages(chatId: String): Flow<List<Message>> = emptyFlow()
        override suspend fun getMessages(chatId: String): List<Message> = emptyList()
        override suspend fun addMessage(message: Message) {}
        override suspend fun archiveMessages(chatId: String, messageIds: List<String>) {}
        override suspend fun deleteMessages(messageIds: List<String>) {}
    }

    private class FakeMemoryRepository : MemoryRepository {
        override suspend fun getMemory(projectId: String): ProjectMemory? = null
        override suspend fun upsertMemory(memory: ProjectMemory) {}
    }

    private class FakeRagRepository : RagRepository {
        val chunksByProject = mutableMapOf<String, List<RagChunk>>()

        override fun observeIndexes(projectId: String): Flow<List<RagIndex>> = emptyFlow()
        override suspend fun listActiveIndexes(projectId: String): List<RagIndex> = emptyList()
        override suspend fun upsertIndex(index: RagIndex) {}
        override suspend fun addDocuments(index: RagIndex, chunks: List<RagDocumentChunk>) {}
        override suspend fun retrieve(projectId: String, query: String, topK: Int): List<RagChunk> =
            chunksByProject[projectId].orEmpty().take(topK)
    }

    private class FakeMcpRepository : McpRepository {
        override fun observeConnections(projectId: String): Flow<List<McpConnection>> = emptyFlow()
        override suspend fun listConnections(projectId: String): List<McpConnection> = emptyList()
        override suspend fun listConnections(projectId: String, type: McpConnectionType): List<McpConnection> = emptyList()
        override suspend fun upsertConnection(connection: McpConnection) {}
    }

    private class FakeBindingRepository(
        private val binding: ProjectGithubBinding?
    ) : ProjectGithubBindingRepository {
        override suspend fun getBinding(projectId: String): ProjectGithubBinding? = binding?.takeIf { it.projectId == projectId }
        override suspend fun upsertBinding(binding: ProjectGithubBinding) {}
        override suspend fun updateReadmeImport(projectId: String, readmeImportedAt: Long, ragIndexId: String?) {}
    }
}
