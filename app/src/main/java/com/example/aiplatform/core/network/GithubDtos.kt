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
