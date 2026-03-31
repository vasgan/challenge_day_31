package com.example.aiplatform

import com.example.aiplatform.data.github.GithubApiGateway
import com.example.aiplatform.data.mcp.github.GithubMcpServer
import com.example.aiplatform.data.mcp.github.GithubMcpToolExecutorImpl
import com.example.aiplatform.data.mcp.github.GithubToolRegistry
import com.example.aiplatform.domain.model.GithubPullRequestDetails
import com.example.aiplatform.domain.model.GithubPullRequestDiff
import com.example.aiplatform.domain.model.GithubPullRequestFile
import com.example.aiplatform.domain.model.GithubPullRequestInlineComment
import com.example.aiplatform.domain.model.GithubPullRequestReviewRequest
import com.example.aiplatform.domain.model.GithubPullRequestReviewResult
import com.example.aiplatform.domain.model.GithubPullRequestSummary
import com.example.aiplatform.domain.model.GithubReadme
import com.example.aiplatform.domain.model.GithubRepo
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubPrMcpToolsTest {

    @Test
    fun `list open pr by project scoped binding`() = runTest {
        val fixture = fixture()

        val prs = fixture.server.githubListOpenPullRequests("project-a").getOrThrow()

        assertEquals(1, prs.size)
        assertEquals("A repo PR", prs.first().title)
    }

    @Test
    fun `get details files and diff`() = runTest {
        val fixture = fixture()

        val details = fixture.server.githubGetPullRequestDetails("project-a", 10).getOrThrow()
        val files = fixture.server.githubGetPullRequestFiles("project-a", 10).getOrThrow()
        val diff = fixture.server.githubGetPullRequestDiff("project-a", 10).getOrThrow()

        assertEquals("A repo PR", details.title)
        assertEquals(1, files.size)
        assertTrue(diff.diff.contains("diff --git"))
    }

    @Test
    fun `submit review payload mapping`() = runTest {
        val fixture = fixture()

        val result = fixture.server.githubSubmitPullRequestReview(
            projectId = "project-a",
            prNumber = 10,
            review = GithubPullRequestReviewRequest(
                body = "review body",
                comments = listOf(
                    GithubPullRequestInlineComment(
                        path = "app/src/main/java/com/example/aiplatform/Foo.kt",
                        line = 22,
                        body = "Fix this"
                    )
                )
            )
        ).getOrThrow()

        assertTrue(result.submitted)
        val captured = fixture.api.lastSubmit
        assertNotNull(captured)
        assertEquals("orgA", captured?.owner)
        assertEquals("repoA", captured?.repo)
        assertEquals(10, captured?.prNumber)
        assertEquals(1, captured?.request?.comments?.size)
        assertEquals("review body", captured?.request?.body)
    }

    @Test
    fun `cross project isolation for bindings`() = runTest {
        val fixture = fixture()

        val a = fixture.server.githubListOpenPullRequests("project-a").getOrThrow().first()
        val b = fixture.server.githubListOpenPullRequests("project-b").getOrThrow().first()

        assertEquals("A repo PR", a.title)
        assertEquals("B repo PR", b.title)
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
        val rag = FakeRagRepository()
        val registry = GithubToolRegistry(api, bindings, rag)
        val server = GithubMcpServer(GithubMcpToolExecutorImpl(registry))
        return Fixture(server, api)
    }

    private data class Fixture(
        val server: GithubMcpServer,
        val api: FakeGithubApiGateway
    )

    private class FakeGithubApiGateway : GithubApiGateway {
        data class SubmitCapture(
            val owner: String,
            val repo: String,
            val prNumber: Int,
            val request: GithubPullRequestReviewRequest
        )

        var lastSubmit: SubmitCapture? = null

        override suspend fun listUserRepos(owner: String): Result<List<GithubRepo>> = Result.success(emptyList())

        override suspend fun getRepo(owner: String, repo: String): Result<GithubRepo> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun fetchReadme(owner: String, repo: String): Result<GithubReadme> =
            Result.failure(IllegalStateException("unused"))

        override suspend fun listOpenPullRequests(owner: String, repo: String): Result<List<GithubPullRequestSummary>> {
            val title = if (owner == "orgA") "A repo PR" else "B repo PR"
            return Result.success(
                listOf(
                    GithubPullRequestSummary(
                        number = 10,
                        title = title,
                        author = "octocat",
                        updatedAt = "2026-03-31T00:00:00Z",
                        htmlUrl = "https://github.com/$owner/$repo/pull/10"
                    )
                )
            )
        }

        override suspend fun getPullRequest(owner: String, repo: String, prNumber: Int): Result<GithubPullRequestDetails> {
            val title = if (owner == "orgA") "A repo PR" else "B repo PR"
            return Result.success(
                GithubPullRequestDetails(
                    number = prNumber,
                    title = title,
                    body = "body",
                    baseBranch = "main",
                    headBranch = "feature",
                    author = "octocat",
                    htmlUrl = "https://github.com/$owner/$repo/pull/$prNumber"
                )
            )
        }

        override suspend fun listPullRequestFiles(owner: String, repo: String, prNumber: Int): Result<List<GithubPullRequestFile>> =
            Result.success(
                listOf(
                    GithubPullRequestFile(
                        filename = "app/src/main/java/com/example/aiplatform/Foo.kt",
                        status = "modified",
                        additions = 3,
                        deletions = 1,
                        patch = "@@ -1 +1 @@"
                    )
                )
            )

        override suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): Result<GithubPullRequestDiff> =
            Result.success(GithubPullRequestDiff("diff --git a/Foo.kt b/Foo.kt"))

        override suspend fun submitPullRequestReview(
            owner: String,
            repo: String,
            prNumber: Int,
            request: GithubPullRequestReviewRequest
        ): Result<GithubPullRequestReviewResult> {
            lastSubmit = SubmitCapture(owner, repo, prNumber, request)
            return Result.success(
                GithubPullRequestReviewResult(
                    reviewId = "1",
                    htmlUrl = "https://github.com/$owner/$repo/pull/$prNumber#pullrequestreview-1",
                    submitted = true
                )
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
