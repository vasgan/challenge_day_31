package com.example.aiplatform

import com.example.aiplatform.data.github.GithubApiGateway
import com.example.aiplatform.data.mcp.github.GithubMcpServer
import com.example.aiplatform.data.mcp.github.GithubMcpToolExecutorImpl
import com.example.aiplatform.data.mcp.github.GithubToolRegistry
import com.example.aiplatform.domain.model.GithubBranchInfo
import com.example.aiplatform.domain.model.GithubCreatedPullRequest
import com.example.aiplatform.domain.model.GithubFileContent
import com.example.aiplatform.domain.model.GithubFileSearchMatch
import com.example.aiplatform.domain.model.GithubFileUpsertResult
import com.example.aiplatform.domain.model.GithubPullRequestDetails
import com.example.aiplatform.domain.model.GithubPullRequestDiff
import com.example.aiplatform.domain.model.GithubPullRequestFile
import com.example.aiplatform.domain.model.GithubPullRequestReviewRequest
import com.example.aiplatform.domain.model.GithubPullRequestReviewResult
import com.example.aiplatform.domain.model.GithubPullRequestSummary
import com.example.aiplatform.domain.model.GithubReadme
import com.example.aiplatform.domain.model.GithubRepo
import com.example.aiplatform.domain.model.GithubRepoFileEntry
import com.example.aiplatform.domain.model.ProjectGithubBinding
import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.ProjectGithubBindingRepository
import com.example.aiplatform.domain.repository.RagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubFileOpsMcpToolsTest {

    @Test
    fun `list get and search files are project scoped`() = runTest {
        val fixture = fixture()

        val filesA = fixture.server.githubListRepositoryFiles("project-a").getOrThrow()
        val filesB = fixture.server.githubListRepositoryFiles("project-b").getOrThrow()
        val fileA = fixture.server.githubGetFileContent("project-a", "README.md").getOrThrow()
        val searchA = fixture.server.githubSearchInFiles("project-a", "Support", listOf("md")).getOrThrow()

        assertTrue(filesA.any { it.path == "README.md" })
        assertTrue(filesB.any { it.path == "README.md" })
        assertTrue(fileA.content.contains("repoA"))
        assertTrue(searchA.any { it.path == "README.md" })
        assertEquals("repoA", fixture.api.lastOwnerForSearch)
    }

    @Test
    fun `upsert mapping works`() = runTest {
        val fixture = fixture()

        val result = fixture.server.githubUpsertFileContent(
            projectId = "project-a",
            branch = "ai/fileops-1",
            path = "README.md",
            content = "new content",
            message = "docs: update readme"
        ).getOrThrow()

        assertEquals("README.md", result.path)
        assertEquals("repoA", fixture.api.lastUpsert?.repo)
        assertEquals("ai/fileops-1", fixture.api.lastUpsert?.branch)
    }

    @Test
    fun `create branch and pr mapping works`() = runTest {
        val fixture = fixture()

        val branch = fixture.server.githubCreateBranch("project-a", "main", "ai/fileops-2").getOrThrow()
        val pr = fixture.server.githubCreatePullRequest(
            projectId = "project-a",
            title = "AI FileOps",
            body = "body",
            head = "ai/fileops-2",
            base = "main"
        ).getOrThrow()

        assertEquals("ai/fileops-2", branch.name)
        assertTrue(pr.htmlUrl.contains("/pull/"))
        assertEquals("repoA", fixture.api.lastPr?.repo)
    }

    @Test
    fun `cross project isolation`() = runTest {
        val fixture = fixture()

        fixture.server.githubUpsertFileContent("project-a", "a-branch", "README.md", "A", "msg").getOrThrow()
        fixture.server.githubUpsertFileContent("project-b", "b-branch", "README.md", "B", "msg").getOrThrow()

        assertEquals("repoB", fixture.api.lastUpsert?.repo)
    }

    private fun fixture(): Fixture {
        val api = FakeGithubApiGateway()
        val bindings = FakeProjectGithubBindingRepository(
            mapOf(
                "project-a" to ProjectGithubBinding(
                    projectId = "project-a",
                    owner = "orgA",
                    repo = "repoA",
                    repoUrl = "https://github.com/orgA/repoA",
                    defaultBranch = "main",
                    readmeImportedAt = null,
                    ragIndexId = null,
                    createdAt = 1L
                ),
                "project-b" to ProjectGithubBinding(
                    projectId = "project-b",
                    owner = "orgB",
                    repo = "repoB",
                    repoUrl = "https://github.com/orgB/repoB",
                    defaultBranch = "main",
                    readmeImportedAt = null,
                    ragIndexId = null,
                    createdAt = 1L
                )
            )
        )
        val registry = GithubToolRegistry(api, bindings, FakeRagRepository())
        val server = GithubMcpServer(GithubMcpToolExecutorImpl(registry))
        return Fixture(server, api)
    }

    private data class Fixture(
        val server: GithubMcpServer,
        val api: FakeGithubApiGateway
    )

    private class FakeGithubApiGateway : GithubApiGateway {
        data class UpsertCall(val owner: String, val repo: String, val branch: String, val path: String)
        data class PrCall(val owner: String, val repo: String, val head: String, val base: String)

        var lastOwnerForSearch: String? = null
        var lastUpsert: UpsertCall? = null
        var lastPr: PrCall? = null

        override suspend fun listUserRepos(owner: String): Result<List<GithubRepo>> = Result.success(emptyList())

        override suspend fun getRepo(owner: String, repo: String): Result<GithubRepo> =
            Result.success(
                GithubRepo(
                    owner = owner,
                    name = repo,
                    fullName = "$owner/$repo",
                    htmlUrl = "https://github.com/$owner/$repo",
                    defaultBranch = "main"
                )
            )

        override suspend fun fetchReadme(owner: String, repo: String): Result<GithubReadme> =
            Result.success(GithubReadme(owner, repo, "README.md", "main", "# README for $repo"))

        override suspend fun listOpenPullRequests(owner: String, repo: String): Result<List<GithubPullRequestSummary>> =
            Result.success(emptyList())

        override suspend fun getPullRequest(owner: String, repo: String, prNumber: Int): Result<GithubPullRequestDetails> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun listPullRequestFiles(owner: String, repo: String, prNumber: Int): Result<List<GithubPullRequestFile>> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): Result<GithubPullRequestDiff> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun submitPullRequestReview(
            owner: String,
            repo: String,
            prNumber: Int,
            request: GithubPullRequestReviewRequest
        ): Result<GithubPullRequestReviewResult> = Result.failure(IllegalStateException("unused"))

        override suspend fun listRepositoryFiles(
            owner: String,
            repo: String,
            path: String,
            recursive: Boolean
        ): Result<List<GithubRepoFileEntry>> {
            return Result.success(
                listOf(
                    GithubRepoFileEntry(path = "README.md", type = "blob", size = 100, sha = "sha-readme"),
                    GithubRepoFileEntry(path = "app/src/main/Main.kt", type = "blob", size = 200, sha = "sha-main")
                )
            )
        }

        override suspend fun getFileContent(owner: String, repo: String, path: String, ref: String?): Result<GithubFileContent> {
            return Result.success(
                GithubFileContent(
                    path = path,
                    sha = "sha-content",
                    ref = ref,
                    content = "# README for $repo"
                )
            )
        }

        override suspend fun searchInFiles(
            owner: String,
            repo: String,
            query: String,
            extensions: List<String>
        ): Result<List<GithubFileSearchMatch>> {
            lastOwnerForSearch = repo
            return Result.success(
                listOf(
                    GithubFileSearchMatch(path = "README.md", line = 12, snippet = "Support MCP info")
                )
            )
        }

        override suspend fun createBranch(owner: String, repo: String, base: String, branch: String): Result<GithubBranchInfo> {
            return Result.success(
                GithubBranchInfo(name = branch, ref = "refs/heads/$branch", sha = "sha-branch")
            )
        }

        override suspend fun upsertFileContent(
            owner: String,
            repo: String,
            branch: String,
            path: String,
            content: String,
            message: String
        ): Result<GithubFileUpsertResult> {
            lastUpsert = UpsertCall(owner, repo, branch, path)
            return Result.success(
                GithubFileUpsertResult(path = path, fileSha = "file-sha", commitSha = "commit-sha", commitUrl = null)
            )
        }

        override suspend fun createPullRequest(
            owner: String,
            repo: String,
            title: String,
            body: String,
            head: String,
            base: String
        ): Result<GithubCreatedPullRequest> {
            lastPr = PrCall(owner, repo, head, base)
            return Result.success(
                GithubCreatedPullRequest(number = 10, title = title, htmlUrl = "https://github.com/$owner/$repo/pull/10")
            )
        }
    }

    private class FakeProjectGithubBindingRepository(
        private val bindings: Map<String, ProjectGithubBinding>
    ) : ProjectGithubBindingRepository {
        override suspend fun getBinding(projectId: String): ProjectGithubBinding? = bindings[projectId]
        override suspend fun upsertBinding(binding: ProjectGithubBinding) {}
        override suspend fun updateReadmeImport(projectId: String, readmeImportedAt: Long, ragIndexId: String?) {}
    }

    private class FakeRagRepository : RagRepository {
        override fun observeIndexes(projectId: String): Flow<List<RagIndex>> = emptyFlow()
        override suspend fun listActiveIndexes(projectId: String): List<RagIndex> = emptyList()
        override suspend fun upsertIndex(index: RagIndex) {}
        override suspend fun addDocuments(index: RagIndex, chunks: List<RagDocumentChunk>) {}
        override suspend fun retrieve(projectId: String, query: String, topK: Int): List<RagChunk> = emptyList()
    }
}
