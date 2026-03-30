package com.example.aiplatform.domain.repository

import com.example.aiplatform.domain.model.McpConnection
import kotlinx.coroutines.flow.Flow

interface McpRepository {
    fun observeConnections(projectId: String): Flow<List<McpConnection>>
    suspend fun listConnections(projectId: String): List<McpConnection>
    suspend fun upsertConnection(connection: McpConnection)
}
