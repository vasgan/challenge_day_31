package com.example.aiplatform

import com.example.aiplatform.assistant.PullRequestReviewPromptBuilder
import com.example.aiplatform.assistant.PullRequestReviewService
import com.example.aiplatform.data.mcp.github.GithubMcpServer
import com.example.aiplatform.data.mcp.github.GithubMcpToolCall
import com.example.aiplatform.data.mcp.github.GithubMcpToolData
import com.example.aiplatform.data.mcp.github.GithubMcpToolExecutor
import com.example.aiplatform.data.mcp.github.GithubMcpToolResult
import com.example.aiplatform.data.mcp.github.GithubMcpTools
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.GithubPullRequestDetails
import com.example.aiplatform.domain.model.GithubPullRequestDiff
import com.example.aiplatform.domain.model.GithubPullRequestFile
import com.example.aiplatform.domain.model.GithubPullRequestReviewResult
import com.example.aiplatform.domain.model.GithubPullRequestSummary
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.MessageRole
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectGithubBinding
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectGithubBindingRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRequestReviewServiceTest {

    @Test
    fun `review uses project scoped binding`() = runTest {
        val fixture = fixture(binding = null)

        val result = fixture.service.reviewPr("project-1", "chat-1", 12)

        assertTrue(result.answer.contains("GitHub repo не привязан"))
        assertFalse(fixture.openAi.called)
    }

    @Test
    fun `review context includes rag chunks when available`() = runTest {
        val fixture = fixture()
        fixture.ragRepository.chunks = listOf(
            RagChunk(
                id = "c1",
                indexId = "idx",
                projectId = "project-1",
                content = "RAG says service layer is mandatory",
                embedding = listOf(1.0),
                source = "README.md",
                section = "part-1"
            )
        )

        val result = fixture.service.reviewPr("project-1", "chat-1", 12)

        assertTrue(result.usedRag)
        assertTrue(fixture.openAi.lastContext.contains("RAG says service layer is mandatory"))
    }

    @Test
    fun `review fallback when rag empty still works`() = runTest {
        val fixture = fixture()
        fixture.ragRepository.chunks = emptyList()

        val result = fixture.service.reviewPr("project-1", "chat-1", 12)

        assertFalse(result.usedRag)
        assertTrue(result.answer.contains("Potential Bugs"))
    }

    @Test
    fun `review text returned even when github submit fails`() = runTest {
        val fixture = fixture()
        fixture.executor.failSubmit = true

        val result = fixture.service.reviewPr("project-1", "chat-1", 12)

        assertFalse(result.postedToGithub)
        assertTrue(result.answer.contains("Potential Bugs"))
        assertTrue(result.answer.contains("GitHub publish failed"))
    }

    private fun fixture(binding: ProjectGithubBinding? = defaultBinding()): Fixture {
        val projectRepository = FakeProjectRepository()
        val chatRepository = FakeChatRepository()
        val memoryRepository = FakeMemoryRepository()
        val ragRepository = FakeRagRepository()
        val openAi = FakeOpenAiRepository()
        val bindingRepository = FakeProjectGithubBindingRepository(binding)
        val executor = FakeGithubMcpExecutor()
        val githubMcpServer = GithubMcpServer(executor)

        val service = PullRequestReviewService(
            projectRepository = projectRepository,
            chatRepository = chatRepository,
            memoryRepository = memoryRepository,
            ragRepository = ragRepository,
            projectGithubBindingRepository = bindingRepository,
            openAiRepository = openAi,
            githubMcpServer = githubMcpServer,
            promptBuilder = PullRequestReviewPromptBuilder()
        )
        return Fixture(service, ragRepository, openAi, executor)
    }

    private data class Fixture(
        val service: PullRequestReviewService,
        val ragRepository: FakeRagRepository,
        val openAi: FakeOpenAiRepository,
        val executor: FakeGithubMcpExecutor
    )

    private class FakeProjectRepository : ProjectRepository {
        override fun observeProjects(): Flow<List<Project>> = emptyFlow()
        override suspend fun getProject(projectId: String): Project? =
            Project(projectId, "Project", "", ProjectTextModel.GPT_5_MINI, 0L, "")
        override suspend fun createProject(project: Project) {}
        override suspend fun updateProjectModel(projectId: String, model: ProjectTextModel) {}
        override suspend fun updateProjectRootPath(projectId: String, rootPath: String) {}
    }

    private class FakeChatRepository : ChatRepository {
        override fun observeChats(projectId: String): Flow<List<Chat>> = emptyFlow()
        override suspend fun getChat(chatId: String): Chat? = Chat(chatId, "project-1", "General")
        override suspend fun createChat(chat: Chat) {}
        override fun observeMessages(chatId: String): Flow<List<Message>> = emptyFlow()
        override suspend fun getMessages(chatId: String): List<Message> = listOf(
            Message("m1", chatId, MessageRole.USER, "Please review this PR", "{}", 1L)
        )
        override suspend fun addMessage(message: Message) {}
        override suspend fun archiveMessages(chatId: String, messageIds: List<String>) {}
        override suspend fun deleteMessages(messageIds: List<String>) {}
    }

    private class FakeMemoryRepository : MemoryRepository {
        override suspend fun getMemory(projectId: String): ProjectMemory? =
            ProjectMemory(projectId, "Memory summary", 1L)

        override suspend fun upsertMemory(memory: ProjectMemory) {}
    }

    private class FakeRagRepository : RagRepository {
        var chunks: List<RagChunk> = emptyList()

        override fun observeIndexes(projectId: String): Flow<List<RagIndex>> = emptyFlow()
        override suspend fun listActiveIndexes(projectId: String): List<RagIndex> = emptyList()
        override suspend fun upsertIndex(index: RagIndex) {}
        override suspend fun addDocuments(index: RagIndex, chunks: List<RagDocumentChunk>) {}
        override suspend fun retrieve(projectId: String, query: String, topK: Int): List<RagChunk> = chunks.take(topK)
    }

    private class FakeProjectGithubBindingRepository(
        private val binding: ProjectGithubBinding?
    ) : ProjectGithubBindingRepository {
        override suspend fun getBinding(projectId: String): ProjectGithubBinding? =
            binding?.takeIf { it.projectId == projectId }

        override suspend fun upsertBinding(binding: ProjectGithubBinding) {}
        override suspend fun updateReadmeImport(projectId: String, readmeImportedAt: Long, ragIndexId: String?) {}
    }

    private class FakeOpenAiRepository : OpenAiRepository {
        var called = false
        var lastContext: String = ""

        override suspend fun responses(
            model: ProjectTextModel,
            systemPrompt: String,
            context: String,
            userInput: String
        ): String {
            called = true
            lastContext = context
            return """
Potential Bugs
- Possible null handling issue in service call.

Architecture Concerns
- No major architecture regressions found.

Recommendations
- Add tests for edge cases.

Overall Verdict
- comment

Optional Inline Suggestions
app/src/main/java/com/example/aiplatform/Foo.kt|42|Guard against null before use
            """.trimIndent()
        }

        override suspend fun summarizeMemory(
            model: ProjectTextModel,
            currentSummary: String,
            archivedConversation: String
        ): String = currentSummary

        override suspend fun embeddings(input: List<String>): List<List<Double>> = emptyList()
    }

    private class FakeGithubMcpExecutor : GithubMcpToolExecutor {
        var failSubmit: Boolean = false

        override suspend fun execute(call: GithubMcpToolCall): GithubMcpToolResult {
            return when (call.tool) {
                GithubMcpTools.LIST_OPEN_PULL_REQUESTS -> GithubMcpToolResult(
                    success = true,
                    data = GithubMcpToolData.PullRequestList(
                        projectId = "project-1",
                        pullRequests = listOf(
                            GithubPullRequestSummary(
                                number = 12,
                                title = "Refactor service",
                                author = "octocat",
                                updatedAt = "2026-03-31T10:00:00Z",
                                htmlUrl = "https://github.com/o/r/pull/12"
                            )
                        )
                    )
                )
                GithubMcpTools.GET_PULL_REQUEST_DETAILS -> GithubMcpToolResult(
                    success = true,
                    data = GithubMcpToolData.PullRequestDetailsPayload(
                        GithubPullRequestDetails(
                            number = 12,
                            title = "Refactor service",
                            body = "Improve service isolation",
                            baseBranch = "main",
                            headBranch = "feature/review",
                            author = "octocat",
                            htmlUrl = "https://github.com/o/r/pull/12"
                        )
                    )
                )
                GithubMcpTools.GET_PULL_REQUEST_FILES -> GithubMcpToolResult(
                    success = true,
                    data = GithubMcpToolData.PullRequestFilesPayload(
                        listOf(
                            GithubPullRequestFile(
                                filename = "app/src/main/java/com/example/aiplatform/Foo.kt",
                                status = "modified",
                                additions = 8,
                                deletions = 2,
                                patch = "@@ -1 +1 @@"
                            )
                        )
                    )
                )
                GithubMcpTools.GET_PULL_REQUEST_DIFF -> GithubMcpToolResult(
                    success = true,
                    data = GithubMcpToolData.PullRequestDiffPayload(
                        GithubPullRequestDiff("diff --git a/Foo.kt b/Foo.kt\n+new")
                    )
                )
                GithubMcpTools.SUBMIT_PULL_REQUEST_REVIEW -> {
                    if (failSubmit) {
                        GithubMcpToolResult(success = false, error = "submit failed")
                    } else {
                        GithubMcpToolResult(
                            success = true,
                            data = GithubMcpToolData.PullRequestReviewPayload(
                                GithubPullRequestReviewResult(
                                    reviewId = "100",
                                    htmlUrl = "https://github.com/o/r/pull/12#pullrequestreview-100",
                                    submitted = true
                                )
                            )
                        )
                    }
                }
                else -> GithubMcpToolResult(success = false, error = "Unexpected tool ${call.tool}")
            }
        }
    }

    private companion object {
        fun defaultBinding(): ProjectGithubBinding = ProjectGithubBinding(
            projectId = "project-1",
            owner = "octocat",
            repo = "repo",
            repoUrl = "https://github.com/octocat/repo",
            defaultBranch = "main",
            readmeImportedAt = 1L,
            ragIndexId = "idx",
            createdAt = 1L
        )
    }
}
