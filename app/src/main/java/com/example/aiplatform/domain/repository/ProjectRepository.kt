package com.example.aiplatform.domain.repository

import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectTextModel
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    suspend fun getProject(projectId: String): Project?
    suspend fun createProject(project: Project)
    suspend fun updateProjectModel(projectId: String, model: ProjectTextModel)
    suspend fun updateProjectRootPath(projectId: String, rootPath: String)
}
