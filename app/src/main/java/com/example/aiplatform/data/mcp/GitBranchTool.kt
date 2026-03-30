package com.example.aiplatform.data.mcp

import com.example.aiplatform.domain.model.McpConnection

interface McpTool {
    suspend fun execute(connection: McpConnection): String
}

/**
 * Minimal MCP tool that represents git branch context.
 * In production this can call remote MCP server capabilities.
 */
class GitBranchTool : McpTool {
    override suspend fun execute(connection: McpConnection): String {
        val pseudoBranch = connection.serverUrl.substringAfterLast('/').ifBlank { "main" }
        return "tool=git_branch; branch=$pseudoBranch; source=${connection.serverUrl}"
    }
}
