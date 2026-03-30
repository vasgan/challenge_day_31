package com.example.aiplatform.data.repository

import com.example.aiplatform.data.local.dao.ProjectMemoryDao
import com.example.aiplatform.data.mapper.toDomain
import com.example.aiplatform.data.mapper.toEntity
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.repository.MemoryRepository

class MemoryRepositoryImpl(
    private val memoryDao: ProjectMemoryDao
) : MemoryRepository {
    override suspend fun getMemory(projectId: String): ProjectMemory? = memoryDao.get(projectId)?.toDomain()

    override suspend fun upsertMemory(memory: ProjectMemory) {
        memoryDao.upsert(memory.toEntity())
    }
}
