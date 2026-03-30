package com.example.aiplatform.data.mcp.github

import com.example.aiplatform.domain.model.GithubReadme
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

    private suspend fun executeTool(tool: String, arguments: Map<String, String>): GithubMcpToolResult {
        val result = executor.execute(GithubMcpToolCall(tool = tool, arguments = arguments))
        return if (result.success) {
            result
        } else {
            GithubMcpToolResult(success = false, error = result.error ?: "Tool execution failed")
        }
    }
}
