package com.example.aiplatform.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubOwnerDto(
    val login: String
)

@Serializable
data class GithubRepoDto(
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("default_branch") val defaultBranch: String,
    val owner: GithubOwnerDto
)

@Serializable
data class GithubContentDto(
    val name: String,
    val path: String,
    val content: String? = null,
    val encoding: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
data class GithubApiErrorDto(
    val message: String? = null,
    @SerialName("documentation_url") val documentationUrl: String? = null
)

@Serializable
data class GithubPullRequestSummaryDto(
    val number: Int,
    val title: String,
    val user: GithubOwnerDto,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String
)

@Serializable
data class GithubPullRequestBranchRefDto(
    val label: String? = null,
    val ref: String
)

@Serializable
data class GithubPullRequestDetailsDto(
    val number: Int,
    val title: String,
    val body: String? = null,
    val user: GithubOwnerDto,
    val base: GithubPullRequestBranchRefDto,
    val head: GithubPullRequestBranchRefDto,
    @SerialName("html_url") val htmlUrl: String
)

@Serializable
data class GithubPullRequestFileDto(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val patch: String? = null
)

@Serializable
data class GithubSubmitReviewCommentDto(
    val path: String,
    val line: Int,
    val side: String,
    val body: String
)

@Serializable
data class GithubSubmitReviewRequestDto(
    val body: String,
    val event: String = "COMMENT",
    val comments: List<GithubSubmitReviewCommentDto> = emptyList()
)

@Serializable
data class GithubPullRequestReviewResponseDto(
    val id: Long,
    @SerialName("html_url") val htmlUrl: String,
    val state: String? = null
)
