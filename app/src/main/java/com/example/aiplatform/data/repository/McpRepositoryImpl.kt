package com.example.aiplatform.data.repository

import com.example.aiplatform.data.local.dao.McpDao
import com.example.aiplatform.data.mapper.toDomain
import com.example.aiplatform.data.mapper.toEntity
import com.example.aiplatform.domain.model.McpConnection
import com.example.aiplatform.domain.repository.McpRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class McpRepositoryImpl(
    private val mcpDao: McpDao
) : McpRepository {
    override fun observeConnections(projectId: String): Flow<List<McpConnection>> =
        mcpDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    override suspend fun listConnections(projectId: String): List<McpConnection> =
        mcpDao.listByProject(projectId).map { it.toDomain() }

    override suspend fun upsertConnection(connection: McpConnection) {
        mcpDao.upsert(connection.toEntity())
    }
}
