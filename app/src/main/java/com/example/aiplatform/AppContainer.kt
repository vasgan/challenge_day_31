package com.example.aiplatform

import android.content.Context
import androidx.room.Room
import com.example.aiplatform.assistant.DeveloperAssistantPromptBuilder
import com.example.aiplatform.assistant.DeveloperAssistantService
import com.example.aiplatform.assistant.PullRequestReviewPromptBuilder
import com.example.aiplatform.assistant.PullRequestReviewService
import com.example.aiplatform.agent.AgentOrchestrator
import com.example.aiplatform.agent.ChatAgent
import com.example.aiplatform.agent.McpAgent
import com.example.aiplatform.agent.MemoryAgent
import com.example.aiplatform.agent.RagAgent
import com.example.aiplatform.core.network.NetworkModule
import com.example.aiplatform.core.security.DefaultSecureConfigProvider
import com.example.aiplatform.data.github.GithubApiClient
import com.example.aiplatform.data.local.AppDatabase
import com.example.aiplatform.data.mcp.GitMcpServer
import com.example.aiplatform.data.mcp.GitBranchTool
import com.example.aiplatform.data.mcp.github.GithubMcpServer
import com.example.aiplatform.data.mcp.github.GithubMcpToolExecutorImpl
import com.example.aiplatform.data.mcp.github.GithubToolRegistry
import com.example.aiplatform.data.memory.ProjectMemoryManager
import com.example.aiplatform.data.repository.ChatRepositoryImpl
import com.example.aiplatform.data.repository.McpRepositoryImpl
import com.example.aiplatform.data.repository.MemoryRepositoryImpl
import com.example.aiplatform.data.repository.OpenAiRepositoryImpl
import com.example.aiplatform.data.repository.ProjectGithubBindingRepositoryImpl
import com.example.aiplatform.data.repository.ProjectRepositoryImpl
import com.example.aiplatform.data.repository.RagRepositoryImpl
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.McpRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectGithubBindingRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository

class AppContainer(context: Context) {
    private val secureConfig = DefaultSecureConfigProvider()

    private val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "assistant.db"
    ).fallbackToDestructiveMigration().build()

    private val openAiApi = NetworkModule.createOpenAiApiService(secureConfig)
    private val githubApi = NetworkModule.createGithubApiService(secureConfig)

    val openAiRepository: OpenAiRepository = OpenAiRepositoryImpl(openAiApi)
    val projectRepository: ProjectRepository = ProjectRepositoryImpl(database.projectDao())
    val chatRepository: ChatRepository = ChatRepositoryImpl(database.chatDao(), database.messageDao())
    val memoryRepository: MemoryRepository = MemoryRepositoryImpl(database.projectMemoryDao())
    val mcpRepository: McpRepository = McpRepositoryImpl(database.mcpDao())
    val ragRepository: RagRepository = RagRepositoryImpl(database.ragDao(), openAiRepository)
    val projectGithubBindingRepository: ProjectGithubBindingRepository =
        ProjectGithubBindingRepositoryImpl(database.projectGithubBindingDao())
    private val githubApiClient = GithubApiClient(githubApi)
    private val githubToolRegistry = GithubToolRegistry(
        githubApiClient = githubApiClient,
        projectGithubBindingRepository = projectGithubBindingRepository,
        ragRepository = ragRepository
    )
    val githubMcpServer = GithubMcpServer(
        executor = GithubMcpToolExecutorImpl(githubToolRegistry)
    )

    private val memoryManager = ProjectMemoryManager(chatRepository, memoryRepository, openAiRepository)
    private val gitMcpServer = GitMcpServer()
    private val developerAssistantService = DeveloperAssistantService(
        projectRepository = projectRepository,
        chatRepository = chatRepository,
        memoryRepository = memoryRepository,
        ragRepository = ragRepository,
        projectGithubBindingRepository = projectGithubBindingRepository,
        openAiRepository = openAiRepository,
        promptBuilder = DeveloperAssistantPromptBuilder()
    )
    private val pullRequestReviewService = PullRequestReviewService(
        projectRepository = projectRepository,
        chatRepository = chatRepository,
        memoryRepository = memoryRepository,
        ragRepository = ragRepository,
        projectGithubBindingRepository = projectGithubBindingRepository,
        openAiRepository = openAiRepository,
        githubMcpServer = githubMcpServer,
        promptBuilder = PullRequestReviewPromptBuilder()
    )

    private val chatAgent = ChatAgent(chatRepository)
    private val ragAgent = RagAgent(ragRepository)
    private val mcpAgent = McpAgent(mcpRepository, GitBranchTool(gitMcpServer))
    private val memoryAgent = MemoryAgent(memoryManager)

    val orchestrator = AgentOrchestrator(
        projectRepository = projectRepository,
        chatRepository = chatRepository,
        openAiRepository = openAiRepository,
        chatAgent = chatAgent,
        ragAgent = ragAgent,
        mcpAgent = mcpAgent,
        memoryAgent = memoryAgent,
        developerAssistantHandler = developerAssistantService,
        pullRequestReviewHandler = pullRequestReviewService
    )
}
