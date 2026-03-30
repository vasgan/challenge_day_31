package com.example.aiplatform.agent

import com.example.aiplatform.data.mcp.McpTool
import com.example.aiplatform.domain.agent.AgentContext
import com.example.aiplatform.domain.agent.AgentResult
import com.example.aiplatform.domain.agent.McpResult
import com.example.aiplatform.domain.agent.SubAgent
import com.example.aiplatform.domain.repository.McpRepository

class McpAgent(
    private val mcpRepository: McpRepository,
    private val tool: McpTool
) : SubAgent {
    suspend fun collect(projectId: String): List<McpResult> {
        return mcpRepository.listConnections(projectId).map { connection ->
            McpResult(connection = connection, payload = tool.execute(connection))
        }
    }

    override suspend fun handle(context: AgentContext): AgentResult {
        return AgentResult(answer = "", usedRag = false, usedMcp = context.mcpData.isNotEmpty())
    }
}
