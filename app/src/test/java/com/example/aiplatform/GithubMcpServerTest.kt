package com.example.aiplatform

import com.example.aiplatform.data.github.GithubApiGateway
import com.example.aiplatform.data.mcp.github.GithubMcpServer
import com.example.aiplatform.data.mcp.github.GithubMcpToolExecutorImpl
import com.example.aiplatform.data.mcp.github.GithubToolRegistry
import com.example.aiplatform.domain.model.GithubReadme
import com.example.aiplatform.domain.model.GithubPullRequestDetails
import com.example.aiplatform.domain.model.GithubPullRequestDiff
import com.example.aiplatform.domain.model.GithubPullRequestFile
import com.example.aiplatform.domain.model.GithubPullRequestReviewRequest
import com.example.aiplatform.domain.model.GithubPullRequestReviewResult
import com.example.aiplatform.domain.model.GithubPullRequestSummary
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

class GithubMcpServerTest {

    @Test
    fun `list repos by owner`() = runTest {
        val fixture = fixture()

        val repos = fixture.server.githubListUserRepos("octocat").getOrThrow()

        assertTrue(repos.isNotEmpty())
        assertEquals("Hello-World", repos.first().name)
    }

    @Test
    fun `empty repo list handled safely`() = runTest {
        val fixture = fixture()
        fixture.githubApi.reposByOwner["empty-owner"] = emptyList()

        val error = fixture.server.githubListUserRepos("empty-owner").exceptionOrNull()

        assertNotNull(error)
        assertTrue(error?.message?.contains("no repositories", ignoreCase = true) == true)
    }

    @Test
    fun `repo selection from list and binding to project`() = runTest {
        val fixture = fixture()

        val repos = fixture.server.githubListUserRepos("octocat").getOrThrow()
        val selected = repos.first { it.name == "Platform" }
        val binding = fixture.server.githubBindRepoToProject("project-1", "octocat", selected.name).getOrThrow()

        assertEquals("project-1", binding.projectId)
        assertEquals("Platform", binding.repo)
    }

    @Test
    fun `repo binding belongs to project`() = runTest {
        val fixture = fixture()

        fixture.server.githubBindRepoToProject("project-1", "octocat", "Hello-World").getOrThrow()
        val bound = fixture.server.githubGetBoundRepo("project-1").getOrThrow()

        assertEquals("project-1", bound.projectId)
        assertEquals("Hello-World", bound.repo)
    }

    @Test
    fun `different projects keep different repo bindings`() = runTest {
        val fixture = fixture()

        fixture.server.githubBindRepoToProject("project-a", "octocat", "Hello-World").getOrThrow()
        fixture.server.githubBindRepoToProject("project-b", "octocat", "Platform").getOrThrow()

        val a = fixture.server.githubGetBoundRepo("project-a").getOrThrow()
        val b = fixture.server.githubGetBoundRepo("project-b").getOrThrow()

        assertEquals("Hello-World", a.repo)
        assertEquals("Platform", b.repo)
    }

    @Test
    fun `fetch readme for bound repo`() = runTest {
        val fixture = fixture()
        fixture.server.githubBindRepoToProject("project-1", "octocat", "Hello-World").getOrThrow()

        val readme = fixture.server.githubFetchReadme("project-1").getOrThrow()

        assertTrue(readme.text.contains("Hello World README"))
        assertEquals("README.md", readme.path)
    }

    @Test
    fun `missing readme handled safely`() = runTest {
        val fixture = fixture()
        fixture.server.githubBindRepoToProject("project-1", "octocat", "Platform").getOrThrow()
        fixture.githubApi.readmeByKey.remove("octocat/Platform")

        val error = fixture.server.githubFetchReadme("project-1").exceptionOrNull()

        assertNotNull(error)
        assertTrue(error?.message?.contains("README", ignoreCase = true) == true)
    }

    @Test
    fun `build rag from readme`() = runTest {
        val fixture = fixture()
        fixture.server.githubBindRepoToProject("project-1", "octocat", "Hello-World").getOrThrow()

        val result = fixture.server.githubBuildRagFromReadme("project-1").getOrThrow()
        val binding = fixture.server.githubGetBoundRepo("project-1").getOrThrow()

        assertTrue(result.chunkCount > 0)
        assertEquals(result.ragIndexId, binding.ragIndexId)
        assertTrue(fixture.ragRepository.savedChunks.any { it.source.contains("github_readme|octocat/Hello-World") })
    }

    private fun fixture(): Fixture {
        val githubApi = FakeGithubApiGateway().apply {
            reposByOwner["octocat"] = listOf(
                GithubRepo(
                    owner = "octocat",
                    name = "Hello-World",
                    fullName = "octocat/Hello-World",
                    htmlUrl = "https://github.com/octocat/Hello-World",
                    defaultBranch = "main"
                ),
                GithubRepo(
                    owner = "octocat",
                    name = "Platform",
                    fullName = "octocat/Platform",
                    htmlUrl = "https://github.com/octocat/Platform",
                    defaultBranch = "develop"
                )
            )
            readmeByKey["octocat/Hello-World"] = GithubReadme(
                owner = "octocat",
                repo = "Hello-World",
                path = "README.md",
                branch = "main",
                text = "# Hello\n\nHello World README docs"
            )
            readmeByKey["octocat/Platform"] = GithubReadme(
                owner = "octocat",
                repo = "Platform",
                path = "README.md",
                branch = "develop",
                text = "# Platform\n\nPlatform README docs"
            )
        }
        val bindingRepository = FakeProjectGithubBindingRepository()
        val ragRepository = FakeRagRepository()
        val registry = GithubToolRegistry(githubApi, bindingRepository, ragRepository)
        val executor = GithubMcpToolExecutorImpl(registry)
        val server = GithubMcpServer(executor)

        return Fixture(server, githubApi, bindingRepository, ragRepository)
    }

    private data class Fixture(
        val server: GithubMcpServer,
        val githubApi: FakeGithubApiGateway,
        val bindingRepository: FakeProjectGithubBindingRepository,
        val ragRepository: FakeRagRepository
    )

    private class FakeGithubApiGateway : GithubApiGateway {
        val reposByOwner = mutableMapOf<String, List<GithubRepo>>()
        val readmeByKey = mutableMapOf<String, GithubReadme>()

        override suspend fun listUserRepos(owner: String): Result<List<GithubRepo>> {
            return Result.success(reposByOwner[owner].orEmpty())
        }

        override suspend fun getRepo(owner: String, repo: String): Result<GithubRepo> {
            val found = reposByOwner[owner].orEmpty().firstOrNull { it.name == repo }
                ?: return Result.failure(IllegalStateException("repo not found"))
            return Result.success(found)
        }

        override suspend fun fetchReadme(owner: String, repo: String): Result<GithubReadme> {
            return readmeByKey["$owner/$repo"]?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("README not found"))
        }

        override suspend fun listOpenPullRequests(owner: String, repo: String): Result<List<GithubPullRequestSummary>> =
            Result.success(emptyList())

        override suspend fun getPullRequest(owner: String, repo: String, prNumber: Int): Result<GithubPullRequestDetails> =
            Result.failure(IllegalStateException("not implemented"))

        override suspend fun listPullRequestFiles(owner: String, repo: String, prNumber: Int): Result<List<GithubPullRequestFile>> =
            Result.failure(IllegalStateException("not implemented"))

        override suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): Result<GithubPullRequestDiff> =
            Result.failure(IllegalStateException("not implemented"))

        override suspend fun submitPullRequestReview(
            owner: String,
            repo: String,
            prNumber: Int,
            request: GithubPullRequestReviewRequest
        ): Result<GithubPullRequestReviewResult> = Result.failure(IllegalStateException("not implemented"))
    }

    private class FakeProjectGithubBindingRepository : ProjectGithubBindingRepository {
        private val map = mutableMapOf<String, ProjectGithubBinding>()

        override suspend fun getBinding(projectId: String): ProjectGithubBinding? = map[projectId]

        override suspend fun upsertBinding(binding: ProjectGithubBinding) {
            map[binding.projectId] = binding
        }

        override suspend fun updateReadmeImport(projectId: String, readmeImportedAt: Long, ragIndexId: String?) {
            val current = map[projectId] ?: return
            map[projectId] = current.copy(readmeImportedAt = readmeImportedAt, ragIndexId = ragIndexId)
        }
    }

    private class FakeRagRepository : RagRepository {
        val savedIndexes = mutableListOf<RagIndex>()
        val savedChunks = mutableListOf<RagDocumentChunk>()

        override fun observeIndexes(projectId: String): Flow<List<RagIndex>> = emptyFlow()

        override suspend fun listActiveIndexes(projectId: String): List<RagIndex> =
            savedIndexes.filter { it.projectId == projectId && it.isActive }

        override suspend fun upsertIndex(index: RagIndex) {
            savedIndexes.removeAll { it.id == index.id }
            savedIndexes += index
        }

        override suspend fun addDocuments(index: RagIndex, chunks: List<RagDocumentChunk>) {
            savedChunks += chunks
        }

        override suspend fun retrieve(projectId: String, query: String, topK: Int): List<RagChunk> = emptyList()
    }
}
