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
            GithubMcpTools.LIST_REPOSITORY_FILES -> listRepositoryFiles(call.arguments)
            GithubMcpTools.GET_FILE_CONTENT -> getFileContent(call.arguments)
            GithubMcpTools.SEARCH_IN_FILES -> searchInFiles(call.arguments)
            GithubMcpTools.CREATE_BRANCH -> createBranch(call.arguments)
            GithubMcpTools.UPSERT_FILE_CONTENT -> upsertFileContent(call.arguments)
            GithubMcpTools.CREATE_PULL_REQUEST -> createPullRequest(call.arguments)
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

    private suspend fun listRepositoryFiles(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) return GithubMcpToolResult(success = false, error = "projectId is required")
        val path = arguments["path"].orEmpty()
        val recursive = arguments["recursive"].orEmpty().ifBlank { "true" }.toBooleanStrictOrNull() ?: true

        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")
        val files = githubApiClient.listRepositoryFiles(binding.owner, binding.repo, path, recursive)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to list repository files")
            }

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.RepositoryFilesPayload(projectId = projectId, files = files)
        )
    }

    private suspend fun getFileContent(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        val path = arguments["path"].orEmpty().trim()
        if (projectId.isBlank() || path.isBlank()) {
            return GithubMcpToolResult(success = false, error = "projectId and path are required")
        }
        val ref = arguments["ref"]?.trim()?.ifBlank { null }

        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")
        val file = githubApiClient.getFileContent(binding.owner, binding.repo, path, ref)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to get file content")
            }

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.FileContentPayload(file = file)
        )
    }

    private suspend fun searchInFiles(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        val query = arguments["query"].orEmpty().trim()
        if (projectId.isBlank() || query.isBlank()) {
            return GithubMcpToolResult(success = false, error = "projectId and query are required")
        }
        val extensions = arguments["extensions"].orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")
        val matches = githubApiClient.searchInFiles(binding.owner, binding.repo, query, extensions)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to search in files")
            }

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.FileSearchPayload(query = query, matches = matches)
        )
    }

    private suspend fun createBranch(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        val base = arguments["base"].orEmpty().trim()
        val branch = arguments["branch"].orEmpty().trim()
        if (projectId.isBlank() || base.isBlank() || branch.isBlank()) {
            return GithubMcpToolResult(success = false, error = "projectId, base, branch are required")
        }
        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")
        val branchInfo = githubApiClient.createBranch(binding.owner, binding.repo, base, branch)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to create branch")
            }
        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.BranchPayload(branch = branchInfo)
        )
    }

    private suspend fun upsertFileContent(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        val branch = arguments["branch"].orEmpty().trim()
        val path = arguments["path"].orEmpty().trim()
        val content = arguments["content"].orEmpty()
        val message = arguments["message"].orEmpty().trim()
        if (projectId.isBlank() || branch.isBlank() || path.isBlank() || content.isBlank() || message.isBlank()) {
            return GithubMcpToolResult(success = false, error = "projectId, branch, path, content, message are required")
        }

        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")
        val upsert = githubApiClient.upsertFileContent(
            owner = binding.owner,
            repo = binding.repo,
            branch = branch,
            path = path,
            content = content,
            message = message
        ).getOrElse { throwable ->
            return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to upsert file content")
        }

        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.FileUpsertPayload(upsert = upsert)
        )
    }

    private suspend fun createPullRequest(arguments: Map<String, String>): GithubMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        val title = arguments["title"].orEmpty().trim()
        val body = arguments["body"].orEmpty()
        val head = arguments["head"].orEmpty().trim()
        val base = arguments["base"].orEmpty().trim()
        if (projectId.isBlank() || title.isBlank() || head.isBlank() || base.isBlank()) {
            return GithubMcpToolResult(success = false, error = "projectId, title, head, base are required")
        }
        val binding = requireProjectBinding(projectId)
            ?: return GithubMcpToolResult(success = false, error = "No GitHub repo bound to project")
        val pr = githubApiClient.createPullRequest(binding.owner, binding.repo, title, body, head, base)
            .getOrElse { throwable ->
                return GithubMcpToolResult(success = false, error = throwable.message ?: "Failed to create pull request")
            }
        return GithubMcpToolResult(
            success = true,
            data = GithubMcpToolData.CreatedPullRequestPayload(pullRequest = pr)
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
