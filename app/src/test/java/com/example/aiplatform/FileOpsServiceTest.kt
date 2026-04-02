package com.example.aiplatform

import com.example.aiplatform.assistant.FileOpsAssistantService
import com.example.aiplatform.assistant.FileOpsPromptBuilder
import com.example.aiplatform.assistant.FileOpsTaskPlanner
import com.example.aiplatform.data.mcp.github.GithubMcpServer
import com.example.aiplatform.data.mcp.github.GithubMcpToolCall
import com.example.aiplatform.data.mcp.github.GithubMcpToolData
import com.example.aiplatform.data.mcp.github.GithubMcpToolExecutor
import com.example.aiplatform.data.mcp.github.GithubMcpToolResult
import com.example.aiplatform.data.mcp.github.GithubMcpTools
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.GithubBranchInfo
import com.example.aiplatform.domain.model.GithubCreatedPullRequest
import com.example.aiplatform.domain.model.GithubFileContent
import com.example.aiplatform.domain.model.GithubFileSearchMatch
import com.example.aiplatform.domain.model.GithubFileUpsertResult
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.MessageRole
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectGithubBinding
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOpsServiceTest {

    @Test
    fun `multi file search uses MCP and returns findings`() = runTest {
        val fixture = fixture()

        val result = fixture.service.runTask("project-1", "chat-1", "найди где используется SupportMcpServer")

        assertTrue(result.success)
        assertFalse(result.openedPr)
        assertTrue(result.answer.contains("Найдено совпадений"))
        assertTrue(fixture.executor.calls.any { it.tool == GithubMcpTools.SEARCH_IN_FILES })
    }

    @Test
    fun `docs update produces changed files and opens PR`() = runTest {
        val fixture = fixture()

        val result = fixture.service.runTask("project-1", "chat-1", "обнови README по support assistant")

        assertTrue(result.success)
        assertEquals(listOf("README.md"), result.changedFiles)
        assertTrue(result.openedPr)
        assertTrue(result.prUrl?.contains("github.com") == true)
        assertTrue(fixture.executor.calls.any { it.tool == GithubMcpTools.UPSERT_FILE_CONTENT })
        assertTrue(fixture.executor.calls.any { it.tool == GithubMcpTools.CREATE_PULL_REQUEST })
    }

    @Test
    fun `safe fallback on MCP errors`() = runTest {
        val fixture = fixture()
        fixture.executor.failSearch = true

        val result = fixture.service.runTask("project-1", "chat-1", "найди где используется SupportMcpServer")

        assertFalse(result.success)
        assertTrue(result.answer.contains("search failed", ignoreCase = true))
    }

    @Test
    fun `safe fallback when repo binding missing`() = runTest {
        val fixture = fixture()
        fixture.executor.hasBinding = false

        val result = fixture.service.runTask("project-1", "chat-1", "обнови README")

        assertFalse(result.success)
        assertTrue(result.answer.contains("No GitHub repo bound", ignoreCase = true))
    }

    private fun fixture(): Fixture {
        val projectRepository = FakeProjectRepository()
        val chatRepository = FakeChatRepository()
        val memoryRepository = FakeMemoryRepository()
        val openAiRepository = FakeOpenAiRepository()
        val executor = FakeGithubMcpExecutor()
        val githubMcpServer = GithubMcpServer(executor)

        val service = FileOpsAssistantService(
            projectRepository = projectRepository,
            chatRepository = chatRepository,
            memoryRepository = memoryRepository,
            openAiRepository = openAiRepository,
            githubMcpServer = githubMcpServer,
            planner = FileOpsTaskPlanner(),
            promptBuilder = FileOpsPromptBuilder()
        )

        return Fixture(service, executor)
    }

    private data class Fixture(
        val service: FileOpsAssistantService,
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
            Message("m1", chatId, MessageRole.USER, "context", "{}", 1L)
        )
        override suspend fun addMessage(message: Message) {}
        override suspend fun archiveMessages(chatId: String, messageIds: List<String>) {}
        override suspend fun deleteMessages(messageIds: List<String>) {}
    }

    private class FakeMemoryRepository : MemoryRepository {
        override suspend fun getMemory(projectId: String): ProjectMemory? =
            ProjectMemory(projectId, "memory", 1L)

        override suspend fun upsertMemory(memory: ProjectMemory) {}
    }

    private class FakeOpenAiRepository : OpenAiRepository {
        override suspend fun responses(
            model: ProjectTextModel,
            systemPrompt: String,
            context: String,
            userInput: String
        ): String {
            return if (userInput.contains("Generate updated content")) {
                "# Updated README\n\nSupport Assistant + FileOps Assistant"
            } else {
                "Findings:\n- usage found"
            }
        }

        override suspend fun summarizeMemory(
            model: ProjectTextModel,
            currentSummary: String,
            archivedConversation: String
        ): String = currentSummary

        override suspend fun embeddings(input: List<String>): List<List<Double>> = emptyList()
    }

    private class FakeGithubMcpExecutor : GithubMcpToolExecutor {
        val calls = mutableListOf<GithubMcpToolCall>()
        var hasBinding: Boolean = true
        var failSearch: Boolean = false

        override suspend fun execute(call: GithubMcpToolCall): GithubMcpToolResult {
            calls += call
            return when (call.tool) {
                GithubMcpTools.GET_BOUND_REPO -> {
                    if (!hasBinding) {
                        GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")
                    } else {
                        GithubMcpToolResult(
                            success = true,
                            data = GithubMcpToolData.BoundRepo(
                                ProjectGithubBinding(
                                    projectId = "project-1",
                                    owner = "org",
                                    repo = "repo",
                                    repoUrl = "https://github.com/org/repo",
                                    defaultBranch = "main",
                                    readmeImportedAt = null,
                                    ragIndexId = null,
                                    createdAt = 1L
                                )
                            )
                        )
                    }
                }

                GithubMcpTools.SEARCH_IN_FILES -> {
                    if (failSearch) {
                        GithubMcpToolResult(success = false, error = "search failed")
                    } else {
                        GithubMcpToolResult(
                            success = true,
                            data = GithubMcpToolData.FileSearchPayload(
                                query = call.arguments["query"].orEmpty(),
                                matches = listOf(
                                    GithubFileSearchMatch(
                                        path = "app/src/main/java/com/example/aiplatform/data/mcp/support/SupportMcpServer.kt",
                                        line = 10,
                                        snippet = "class SupportMcpServer"
                                    ),
                                    GithubFileSearchMatch(
                                        path = "README.md",
                                        line = 34,
                                        snippet = "Support MCP"
                                    )
                                )
                            )
                        )
                    }
                }

                GithubMcpTools.GET_FILE_CONTENT -> GithubMcpToolResult(
                    success = true,
                    data = GithubMcpToolData.FileContentPayload(
                        GithubFileContent(
                            path = call.arguments["path"].orEmpty(),
                            sha = "abc",
                            ref = call.arguments["ref"],
                            content = "# README\n\nold"
                        )
                    )
                )

                GithubMcpTools.CREATE_BRANCH -> GithubMcpToolResult(
                    success = true,
                    data = GithubMcpToolData.BranchPayload(
                        GithubBranchInfo(
                            name = call.arguments["branch"].orEmpty(),
                            ref = "refs/heads/${call.arguments["branch"].orEmpty()}",
                            sha = "sha-1"
                        )
                    )
                )

                GithubMcpTools.UPSERT_FILE_CONTENT -> GithubMcpToolResult(
                    success = true,
                    data = GithubMcpToolData.FileUpsertPayload(
                        GithubFileUpsertResult(
                            path = call.arguments["path"].orEmpty(),
                            fileSha = "file-sha",
                            commitSha = "commit-sha",
                            commitUrl = "https://github.com/org/repo/commit/commit-sha"
                        )
                    )
                )

                GithubMcpTools.CREATE_PULL_REQUEST -> GithubMcpToolResult(
                    success = true,
                    data = GithubMcpToolData.CreatedPullRequestPayload(
                        GithubCreatedPullRequest(
                            number = 12,
                            title = call.arguments["title"].orEmpty(),
                            htmlUrl = "https://github.com/org/repo/pull/12"
                        )
                    )
                )

                else -> GithubMcpToolResult(success = false, error = "Unexpected tool: ${call.tool}")
            }
        }
    }
}
