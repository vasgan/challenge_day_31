package com.example.aiplatform

import com.example.aiplatform.agent.McpAgent
import com.example.aiplatform.data.mcp.GitBranchTool
import com.example.aiplatform.domain.model.McpConnection
import com.example.aiplatform.domain.repository.McpRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class McpIsolationTest {
    @Test
    fun `mcp agent fetches connections only for current project`() = runTest {
        val repository = FakeMcpRepository(
            listOf(
                McpConnection("1", "project-a", "https://mcp/a/main"),
                McpConnection("2", "project-b", "https://mcp/b/dev")
            )
        )
        val agent = McpAgent(repository, GitBranchTool())

        val result = agent.collect("project-a")

        assertEquals("project-a", repository.lastRequestedProject)
        assertEquals(1, result.size)
        assertEquals("project-a", result.first().connection.projectId)
    }

    private class FakeMcpRepository(
        private val all: List<McpConnection>
    ) : McpRepository {
        var lastRequestedProject: String? = null

        override fun observeConnections(projectId: String): Flow<List<McpConnection>> = emptyFlow()

        override suspend fun listConnections(projectId: String): List<McpConnection> {
            lastRequestedProject = projectId
            return all.filter { it.projectId == projectId }
        }

        override suspend fun upsertConnection(connection: McpConnection) {}
    }
}
