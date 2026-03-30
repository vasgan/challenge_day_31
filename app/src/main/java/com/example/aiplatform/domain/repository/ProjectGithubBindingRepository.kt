package com.example.aiplatform.domain.repository

import com.example.aiplatform.domain.model.ProjectGithubBinding

interface ProjectGithubBindingRepository {
    suspend fun getBinding(projectId: String): ProjectGithubBinding?
    suspend fun upsertBinding(binding: ProjectGithubBinding)
    suspend fun updateReadmeImport(projectId: String, readmeImportedAt: Long, ragIndexId: String?)
}
