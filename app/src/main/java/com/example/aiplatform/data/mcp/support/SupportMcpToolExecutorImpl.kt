package com.example.aiplatform.data.mcp.support

class SupportMcpToolExecutorImpl(
    private val registry: SupportToolRegistry
) : SupportMcpToolExecutor {
    override suspend fun execute(call: SupportMcpToolCall): SupportMcpToolResult = registry.execute(call)
}
