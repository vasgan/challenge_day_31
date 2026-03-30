package com.example.aiplatform.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_github_bindings")
data class ProjectGithubBindingEntity(
    @PrimaryKey val projectId: String,
    val owner: String,
    val repo: String,
    val repoUrl: String,
    val defaultBranch: String,
    val readmeImportedAt: Long?,
    val ragIndexId: String?,
    val createdAt: Long
)
