package com.example.aiplatform.data.mcp.github

import com.example.aiplatform.data.github.GithubApiGateway
import com.example.aiplatform.domain.model.GithubPullRequestInlineComment
import com.example.aiplatform.domain.model.GithubPullRequestReviewRequest
import com.example.aiplatform.domain.model.ProjectGithubBinding
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.ProjectGithubBindingRepository
import com.example.aiplatform.domain.repository.RagRepository

class GithubToolRegistry(
    private val githubApiClient: GithubApiGateway,
    private val projectGithubBindingRepository: ProjectGithubBindingRepository,
    private val ragRepository: RagRepository
) {
    suspend fun execute(call: GithubMcpToolCall): GithubMcpToolResult {
        return when (call.tool) {
            GithubMcpTools.LIST_USER_REPOS -> listUserRepos(call.arguments)
            GithubMcpTools.BIND_REPO_TO_PROJECT -> bindRepoToProject(call.arguments)
            GithubMcpTools.GET_BOUND_REPO -> getBoundRepo(call.arguments)
            GithubMcpTools.FETCH_README -> fetchReadme(call.arguments)
            GithubMcpTools.BUILD_RAG_FROM_README -> buildRagFromReadme(call.arguments)
            GithubMcpTools.LIST_OPEN_PULL_REQUESTS -> listOpenPullRequests(call.arguments)
            GithubMcpTools.GET_PULL_REQUEST_DETAILS -> getPullRequestDetails(call.arguments)
            GithubMcpTools.GET_PULL_REQUEST_FILES -> getPullRequestFiles(call.arguments)
            GithubMcpTools.GET_PULL_REQUEST_DIFF -> getPullRequestDiff(call.arguments)
            GithubMcpTools.SUBMIT_PULL_REQUEST_REVIEW -> submitPullRequestReview(call.arguments)
            else -> GithubMcpToolResult(success = false, error = "Unknown tool: ${call.tool}")
        }
    }

    private suspend fun listUserRepos(arguments: Map<String, String>): GithubMcpToolResult {
        val owner = arguments["owner"].orEmpty().trim()
        if (owner.isBlank()) return GithubMcpToolResult(success = false, error = "owner is required")

        val repos = githubApiClient.listUserRepos(owner)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to list repos")
            }

        if (repos.isEmpty()) {
            return GithubMcpToolResult(success = false, error = "Owner has no repositories")
        }

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.RepoList(owner = owner, repos = repos)
        )
    }

    private suspend fun bindRepoToProject(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        val owner = arguments["owner"].orEmpty().trim()
        val repo = arguments["repo"].orEmpty().trim()

        if (projectId.isBlank() || owner.isBlank() || repo.isBlank()) {
            return GithubMcpToolResult(success = false, error = "projectId, owner, repo are required")
        }

        val repoInfo = githubApiClient.getRepo(owner, repo)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to get repo")
            }

        val existing = projectGithubBindingRepository.getBinding(projectId)
        val binding = ProjectGithubBinding(
            projectId = projectId,
            owner = repoInfo.owner,
            repo = repoInfo.name,
            repoUrl = repoInfo.htmlUrl,
            defaultBranch = repoInfo.defaultBranch,
            readmeImportedAt = existing?.readmeImportedAt,
            ragIndexId = existing?.ragIndexId,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )

        projectGithubBindingRepository.upsertBinding(binding)

        return GithubMcpToolResult(success = true, data = GithubMcpToolData.BoundRepo(binding))
    }

    private suspend fun getBoundRepo(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) return GithubMcpToolResult(success = false, error = "projectId is required")

        val binding = projectGithubBindingRepository.getBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")

        return GithubMcpToolResult(success = true, data = GithubMcpToolData.BoundRepo(binding))
    }

    private suspend fun fetchReadme(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) return GithubMcpToolResult(success = false, error = "projectId is required")

        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")

        val readme = githubApiClient.fetchReadme(binding.owner, binding.repo)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "README fetch failed")
            }

        return GithubMcpToolResult(success = true, data = GithubMcpToolData.Readme(readme))
    }

    private suspend fun buildRagFromReadme(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) return GithubMcpToolResult(success = false, error = "projectId is required")

        val readmeResult = fetchReadme(mapOf("projectId" to projectId))
        if (!readmeResult.success) {
            return readmeResult
        }

        val readme = (readmeResult.data as? GithubMcpToolData.Readme)?.readme
            ?: return GithubMcpToolResult(success = false, error = "README payload missing")

        val indexId = "github-readme-index-$projectId"
        val index = RagIndex(
            id = indexId,
            projectId = projectId,
            title = "GitHub README",
            isActive = true
        )

        val chunks = chunkReadme(readme)
        if (chunks.isEmpty()) {
            return GithubMcpToolResult(success = false, error = "README is empty after chunking")
        }

        ragRepository.upsertIndex(index)
        ragRepository.addDocuments(index, chunks)

        projectGithubBindingRepository.updateReadmeImport(
            projectId = projectId,
            readmeImportedAt = System.currentTimeMillis(),
            ragIndexId = indexId
        )

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.RagBuilt(
                projectId = projectId,
                ragIndexId = indexId,
                chunkCount = chunks.size
            )
        )
    }

    private suspend fun listOpenPullRequests(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) return GithubMcpToolResult(success = false, error = "projectId is required")
        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")

        val prs = githubApiClient.listOpenPullRequests(binding.owner, binding.repo)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to list pull requests")
            }

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.PullRequestList(projectId = projectId, pullRequests = prs)
        )
    }

    private suspend fun getPullRequestDetails(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) return GithubMcpToolResult(success = false, error = "projectId is required")
        val prNumber = arguments["prNumber"].orEmpty().trim().toIntOrNull()
            ?: return GithubMcpToolResult(success = false, error = "Valid prNumber is required")
        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")

        val details = githubApiClient.getPullRequest(binding.owner, binding.repo, prNumber)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to get pull request")
            }

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.PullRequestDetailsPayload(details = details)
        )
    }

    private suspend fun getPullRequestFiles(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) return GithubMcpToolResult(success = false, error = "projectId is required")
        val prNumber = arguments["prNumber"].orEmpty().trim().toIntOrNull()
            ?: return GithubMcpToolResult(success = false, error = "Valid prNumber is required")
        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")

        val files = githubApiClient.listPullRequestFiles(binding.owner, binding.repo, prNumber)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to list pull request files")
            }

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.PullRequestFilesPayload(files = files)
        )
    }

    private suspend fun getPullRequestDiff(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) return GithubMcpToolResult(success = false, error = "projectId is required")
        val prNumber = arguments["prNumber"].orEmpty().trim().toIntOrNull()
            ?: return GithubMcpToolResult(success = false, error = "Valid prNumber is required")
        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")

        val diff = githubApiClient.getPullRequestDiff(binding.owner, binding.repo, prNumber)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to fetch pull request diff")
            }

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.PullRequestDiffPayload(diff = diff)
        )
    }

    private suspend fun submitPullRequestReview(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        val prNumber = arguments["prNumber"].orEmpty().trim().toIntOrNull()
            ?: return GithubMcpToolResult(success = false, error = "Valid prNumber is required")
        val body = arguments["body"].orEmpty().trim()
        if (projectId.isBlank() || body.isBlank()) {
            return GithubMcpToolResult(success = false, error = "projectId and body are required")
        }
        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")

        val commentsCount = arguments["commentsCount"].orEmpty().toIntOrNull() ?: 0
        val comments = (0 until commentsCount).mapNotNull { index ->
            val path = arguments["comment${index}_path"].orEmpty().trim()
            val line = arguments["comment${index}_line"].orEmpty().toIntOrNull()
            val side = arguments["comment${index}_side"].orEmpty().trim().ifBlank { "RIGHT" }
            val commentBody = arguments["comment${index}_body"].orEmpty().trim()
            if (path.isBlank() || line == null || line <= 0 || commentBody.isBlank()) {
                null
            } else {
                GithubPullRequestInlineComment(path = path, line = line, side = side, body = commentBody)
            }
        }

        val reviewResult = githubApiClient.submitPullRequestReview(
            owner = binding.owner,
            repo = binding.repo,
            prNumber = prNumber,
            request = GithubPullRequestReviewRequest(body = body, comments = comments)
        ).getOrElse { throwable ->
            return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to submit pull request review")
        }

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.PullRequestReviewPayload(result = reviewResult)
        )
    }

    private suspend fun requireProjectBinding(projectId: String): ProjectGithubBinding? {
        if (projectId.isBlank()) return null
        return projectGithubBindingRepository.getBinding(projectId)
    }

    private fun chunkReadme(readme: com.example.aiplatform.domain.model.GithubReadme): List<RagDocumentChunk> {
        val paragraphs = readme.text.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) return emptyList()

        val source = "github_readme|${readme.owner}/${readme.repo}|${readme.path}|${readme.branch}"
        val chunks = mutableListOf<RagDocumentChunk>()
        var buffer = StringBuilder()
        var part = 1

        paragraphs.forEach { paragraph ->
            if (buffer.length + paragraph.length > 900 && buffer.isNotBlank()) {
                chunks += RagDocumentChunk(
                    content = buffer.toString().trim(),
                    source = source,
                    section = "part-$part"
                )
                part += 1
                buffer = StringBuilder()
            }
            buffer.appendLine(paragraph)
        }

        if (buffer.isNotBlank()) {
            chunks += RagDocumentChunk(
                content = buffer.toString().trim(),
                source = source,
                section = "part-$part"
            )
        }

        return chunks
    }
}
