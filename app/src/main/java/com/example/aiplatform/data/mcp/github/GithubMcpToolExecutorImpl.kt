package com.example.aiplatform.data.mcp.github

class GithubMcpToolExecutorImpl(
    private val registry: GithubToolRegistry
) : GithubMcpToolExecutor {
    override suspend fun execute(call: GithubMcpToolCall): GithubMcpToolResult {
        return registry.execute(call)
    }
}
