package com.example.aiplatform.data.mcp.github

import com.example.aiplatform.domain.model.GithubReadme
import com.example.aiplatform.domain.model.GithubPullRequestDetails
import com.example.aiplatform.domain.model.GithubPullRequestDiff
import com.example.aiplatform.domain.model.GithubPullRequestFile
import com.example.aiplatform.domain.model.GithubPullRequestReviewResult
import com.example.aiplatform.domain.model.GithubPullRequestSummary
import com.example.aiplatform.domain.model.GithubRepo
import com.example.aiplatform.domain.model.ProjectGithubBinding

data class GithubMcpToolCall(
    val tool: String,
    val arguments: Map<String, String>
)

sealed interface GithubMcpToolData {
    data class RepoList(val owner: String, val repos: List<GithubRepo>) : GithubMcpToolData
    data class BoundRepo(val binding: ProjectGithubBinding) : GithubMcpToolData
    data class Readme(val readme: GithubReadme) : GithubMcpToolData
    data class RagBuilt(val projectId: String, val ragIndexId: String, val chunkCount: Int) : GithubMcpToolData
    data class PullRequestList(val projectId: String, val pullRequests: List<GithubPullRequestSummary>) : GithubMcpToolData
    data class PullRequestDetailsPayload(val details: GithubPullRequestDetails) : GithubMcpToolData
    data class PullRequestFilesPayload(val files: List<GithubPullRequestFile>) : GithubMcpToolData
    data class PullRequestDiffPayload(val diff: GithubPullRequestDiff) : GithubMcpToolData
    data class PullRequestReviewPayload(val result: GithubPullRequestReviewResult) : GithubMcpToolData
}

data class GithubMcpToolResult(
    val success: Boolean,
    val data: GithubMcpToolData? = null,
    val error: String? = null
)

interface GithubMcpToolExecutor {
    suspend fun execute(call: GithubMcpToolCall): GithubMcpToolResult
}

object GithubMcpTools {
    const val LIST_USER_REPOS = "github_list_user_repos"
    const val BIND_REPO_TO_PROJECT = "github_bind_repo_to_project"
    const val GET_BOUND_REPO = "github_get_bound_repo"
    const val FETCH_README = "github_fetch_readme"
    const val BUILD_RAG_FROM_README = "github_build_rag_from_readme"
    const val LIST_OPEN_PULL_REQUESTS = "github_list_open_pull_requests"
    const val GET_PULL_REQUEST_DETAILS = "github_get_pull_request_details"
    const val GET_PULL_REQUEST_FILES = "github_get_pull_request_files"
    const val GET_PULL_REQUEST_DIFF = "github_get_pull_request_diff"
    const val SUBMIT_PULL_REQUEST_REVIEW = "github_submit_pull_request_review"
}
