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
