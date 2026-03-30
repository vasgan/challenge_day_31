package com.example.aiplatform.data.repository

import com.example.aiplatform.data.local.dao.ProjectGithubBindingDao
import com.example.aiplatform.data.mapper.toDomain
import com.example.aiplatform.data.mapper.toEntity
import com.example.aiplatform.domain.model.ProjectGithubBinding
import com.example.aiplatform.domain.repository.ProjectGithubBindingRepository

class ProjectGithubBindingRepositoryImpl(
    private val dao: ProjectGithubBindingDao
) : ProjectGithubBindingRepository {
    override suspend fun getBinding(projectId: String): ProjectGithubBinding? = dao.getBinding(projectId)?.toDomain()

    override suspend fun upsertBinding(binding: ProjectGithubBinding) {
        dao.upsert(binding.toEntity())
    }

    override suspend fun updateReadmeImport(projectId: String, readmeImportedAt: Long, ragIndexId: String?) {
        dao.updateReadmeImport(projectId, readmeImportedAt, ragIndexId)
    }
}
