package com.example.aiplatform.domain.repository

import com.example.aiplatform.domain.model.ProjectMemory

interface MemoryRepository {
    suspend fun getMemory(projectId: String): ProjectMemory?
    suspend fun upsertMemory(memory: ProjectMemory)
}
