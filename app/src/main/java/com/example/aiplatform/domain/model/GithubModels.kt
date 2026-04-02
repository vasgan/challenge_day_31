package com.example.aiplatform.domain.model

data class GithubRepo(
    val owner: String,
    val name: String,
    val fullName: String,
    val htmlUrl: String,
    val defaultBranch: String
)

data class GithubReadme(
    val owner: String,
    val repo: String,
    val path: String,
    val branch: String,
    val text: String
)

data class ProjectGithubBinding(
    val projectId: String,
    val owner: String,
    val repo: String,
    val repoUrl: String,
    val defaultBranch: String,
    val readmeImportedAt: Long?,
    val ragIndexId: String?,
    val createdAt: Long
)

data class GithubPullRequestSummary(
    val number: Int,
    val title: String,
    val author: String,
    val updatedAt: String,
    val htmlUrl: String
)

data class GithubPullRequestDetails(
    val number: Int,
    val title: String,
    val body: String,
    val baseBranch: String,
    val headBranch: String,
    val author: String,
    val htmlUrl: String
)

data class GithubPullRequestFile(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val patch: String?
)

data class GithubPullRequestDiff(
    val diff: String
)

data class GithubPullRequestInlineComment(
    val path: String,
    val line: Int,
    val body: String,
    val side: String = "RIGHT"
)

data class GithubPullRequestReviewRequest(
    val body: String,
    val comments: List<GithubPullRequestInlineComment>
)

data class GithubPullRequestReviewResult(
    val reviewId: String,
    val htmlUrl: String,
    val submitted: Boolean
)

data class GithubRepoFileEntry(
    val path: String,
    val type: String,
    val size: Int?,
    val sha: String
)

data class GithubFileContent(
    val path: String,
    val sha: String,
    val ref: String?,
    val content: String
)

data class GithubFileSearchMatch(
    val path: String,
    val line: Int,
    val snippet: String
)

data class GithubBranchInfo(
    val name: String,
    val ref: String,
    val sha: String
)

data class GithubFileUpsertResult(
    val path: String,
    val fileSha: String,
    val commitSha: String,
    val commitUrl: String?
)

data class GithubCreatedPullRequest(
    val number: Int,
    val title: String,
    val htmlUrl: String
)
