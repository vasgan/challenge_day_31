package com.example.aiplatform.data.mcp.github

import com.example.aiplatform.domain.model.GithubReadme
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
import com.example.aiplatform.domain.model.GithubRepoFileEntry
import com.example.aiplatform.domain.model.GithubRepo
import com.example.aiplatform.domain.model.ProjectGithubBinding

class GithubMcpServer(
    private val executor: GithubMcpToolExecutor
) {
    suspend fun githubListUserRepos(owner: String): Result<List<GithubRepo>> {
        val result = executeTool(GithubMcpTools.LIST_USER_REPOS, mapOf("owner" to owner))
        val payload = result.data as? GithubMcpToolData.RepoList
            ?: return Result.failure(IllegalStateException(result.error ?: "Repo list payload missing"))
        return Result.success(payload.repos)
    }

    suspend fun githubBindRepoToProject(projectId: String, owner: String, repo: String): Result<ProjectGithubBinding> {
        val result = executeTool(
            GithubMcpTools.BIND_REPO_TO_PROJECT,
            mapOf("projectId" to projectId, "owner" to owner, "repo" to repo)
        )
        val payload = result.data as? GithubMcpToolData.BoundRepo
            ?: return Result.failure(IllegalStateException(result.error ?: "Binding payload missing"))
        return Result.success(payload.binding)
    }

    suspend fun githubGetBoundRepo(projectId: String): Result<ProjectGithubBinding> {
        val result = executeTool(GithubMcpTools.GET_BOUND_REPO, mapOf("projectId" to projectId))
        val payload = result.data as? GithubMcpToolData.BoundRepo
            ?: return Result.failure(IllegalStateException(result.error ?: "Bound repo payload missing"))
        return Result.success(payload.binding)
    }

    suspend fun githubFetchReadme(projectId: String): Result<GithubReadme> {
        val result = executeTool(GithubMcpTools.FETCH_README, mapOf("projectId" to projectId))
        val payload = result.data as? GithubMcpToolData.Readme
            ?: return Result.failure(IllegalStateException(result.error ?: "README payload missing"))
        return Result.success(payload.readme)
    }

    suspend fun githubBuildRagFromReadme(projectId: String): Result<GithubMcpToolData.RagBuilt> {
        val result = executeTool(GithubMcpTools.BUILD_RAG_FROM_README, mapOf("projectId" to projectId))
        val payload = result.data as? GithubMcpToolData.RagBuilt
            ?: return Result.failure(IllegalStateException(result.error ?: "RAG build payload missing"))
        return Result.success(payload)
    }

    suspend fun githubListOpenPullRequests(projectId: String): Result<List<GithubPullRequestSummary>> {
        val result = executeTool(GithubMcpTools.LIST_OPEN_PULL_REQUESTS, mapOf("projectId" to projectId))
        val payload = result.data as? GithubMcpToolData.PullRequestList
            ?: return Result.failure(IllegalStateException(result.error ?: "Pull request list payload missing"))
        return Result.success(payload.pullRequests)
    }

    suspend fun githubGetPullRequestDetails(projectId: String, prNumber: Int): Result<GithubPullRequestDetails> {
        val result = executeTool(
            GithubMcpTools.GET_PULL_REQUEST_DETAILS,
            mapOf("projectId" to projectId, "prNumber" to prNumber.toString())
        )
        val payload = result.data as? GithubMcpToolData.PullRequestDetailsPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "Pull request details payload missing"))
        return Result.success(payload.details)
    }

    suspend fun githubGetPullRequestFiles(projectId: String, prNumber: Int): Result<List<GithubPullRequestFile>> {
        val result = executeTool(
            GithubMcpTools.GET_PULL_REQUEST_FILES,
            mapOf("projectId" to projectId, "prNumber" to prNumber.toString())
        )
        val payload = result.data as? GithubMcpToolData.PullRequestFilesPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "Pull request files payload missing"))
        return Result.success(payload.files)
    }

    suspend fun githubGetPullRequestDiff(projectId: String, prNumber: Int): Result<GithubPullRequestDiff> {
        val result = executeTool(
            GithubMcpTools.GET_PULL_REQUEST_DIFF,
            mapOf("projectId" to projectId, "prNumber" to prNumber.toString())
        )
        val payload = result.data as? GithubMcpToolData.PullRequestDiffPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "Pull request diff payload missing"))
        return Result.success(payload.diff)
    }

    suspend fun githubSubmitPullRequestReview(
        projectId: String,
        prNumber: Int,
        review: GithubPullRequestReviewRequest
    ): Result<GithubPullRequestReviewResult> {
        val args = mutableMapOf(
            "projectId" to projectId,
            "prNumber" to prNumber.toString(),
            "body" to review.body,
            "commentsCount" to review.comments.size.toString()
        )
        review.comments.forEachIndexed { index, comment ->
            args["comment${index}_path"] = comment.path
            args["comment${index}_line"] = comment.line.toString()
            args["comment${index}_side"] = comment.side
            args["comment${index}_body"] = comment.body
        }

        val result = executeTool(GithubMcpTools.SUBMIT_PULL_REQUEST_REVIEW, args)
        val payload = result.data as? GithubMcpToolData.PullRequestReviewPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "Pull request review payload missing"))
        return Result.success(payload.result)
    }

    suspend fun githubListRepositoryFiles(
        projectId: String,
        path: String = "",
        recursive: Boolean = true
    ): Result<List<GithubRepoFileEntry>> {
        val result = executeTool(
            GithubMcpTools.LIST_REPOSITORY_FILES,
            mapOf(
                "projectId" to projectId,
                "path" to path,
                "recursive" to recursive.toString()
            )
        )
        val payload = result.data as? GithubMcpToolData.RepositoryFilesPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "Repository files payload missing"))
        return Result.success(payload.files)
    }

    suspend fun githubGetFileContent(
        projectId: String,
        path: String,
        ref: String? = null
    ): Result<GithubFileContent> {
        val args = mutableMapOf(
            "projectId" to projectId,
            "path" to path
        )
        ref?.takeIf { it.isNotBlank() }?.let { args["ref"] = it }
        val result = executeTool(GithubMcpTools.GET_FILE_CONTENT, args)
        val payload = result.data as? GithubMcpToolData.FileContentPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "File content payload missing"))
        return Result.success(payload.file)
    }

    suspend fun githubSearchInFiles(
        projectId: String,
        query: String,
        extensions: List<String>
    ): Result<List<GithubFileSearchMatch>> {
        val result = executeTool(
            GithubMcpTools.SEARCH_IN_FILES,
            mapOf(
                "projectId" to projectId,
                "query" to query,
                "extensions" to extensions.joinToString(",")
            )
        )
        val payload = result.data as? GithubMcpToolData.FileSearchPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "File search payload missing"))
        return Result.success(payload.matches)
    }

    suspend fun githubCreateBranch(
        projectId: String,
        base: String,
        branch: String
    ): Result<GithubBranchInfo> {
        val result = executeTool(
            GithubMcpTools.CREATE_BRANCH,
            mapOf(
                "projectId" to projectId,
                "base" to base,
                "branch" to branch
            )
        )
        val payload = result.data as? GithubMcpToolData.BranchPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "Branch payload missing"))
        return Result.success(payload.branch)
    }

    suspend fun githubUpsertFileContent(
        projectId: String,
        branch: String,
        path: String,
        content: String,
        message: String
    ): Result<GithubFileUpsertResult> {
        val result = executeTool(
            GithubMcpTools.UPSERT_FILE_CONTENT,
            mapOf(
                "projectId" to projectId,
                "branch" to branch,
                "path" to path,
                "content" to content,
                "message" to message
            )
        )
        val payload = result.data as? GithubMcpToolData.FileUpsertPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "File upsert payload missing"))
        return Result.success(payload.upsert)
    }

    suspend fun githubCreatePullRequest(
        projectId: String,
        title: String,
        body: String,
        head: String,
        base: String
    ): Result<GithubCreatedPullRequest> {
        val result = executeTool(
            GithubMcpTools.CREATE_PULL_REQUEST,
            mapOf(
                "projectId" to projectId,
                "title" to title,
                "body" to body,
                "head" to head,
                "base" to base
            )
        )
        val payload = result.data as? GithubMcpToolData.CreatedPullRequestPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "Create pull request payload missing"))
        return Result.success(payload.pullRequest)
    }

    private suspend fun executeTool(tool: String, arguments: Map<String, String>): GithubMcpToolResult {
        val result = executor.execute(GithubMcpToolCall(tool = tool, arguments = arguments))
        return if (result.success) {
            result
        } else {
            GithubMcpToolResult(success = false, error = result.error ?: "Tool execution failed")
        }
    }
}
