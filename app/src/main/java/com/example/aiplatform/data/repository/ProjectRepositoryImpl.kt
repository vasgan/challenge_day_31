package com.example.aiplatform.data.repository

import com.example.aiplatform.data.local.dao.ProjectDao
import com.example.aiplatform.data.mapper.toDomain
import com.example.aiplatform.data.mapper.toEntity
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepositoryImpl(
    private val projectDao: ProjectDao
) : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> = projectDao.observeProjects().map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun getProject(projectId: String): Project? = projectDao.getProject(projectId)?.toDomain()

    override suspend fun createProject(project: Project) {
        projectDao.upsert(project.toEntity())
    }

    override suspend fun updateProjectModel(projectId: String, model: ProjectTextModel) {
        projectDao.updateModel(projectId, model.apiName)
    }

    override suspend fun updateProjectRootPath(projectId: String, rootPath: String) {
        projectDao.updateRootPath(projectId, rootPath)
    }
}
