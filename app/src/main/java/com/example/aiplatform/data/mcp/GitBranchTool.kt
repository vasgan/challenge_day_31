package com.example.aiplatform.data.mcp

import com.example.aiplatform.domain.model.McpConnection
import com.example.aiplatform.domain.model.McpConnectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface McpTool {
    suspend fun execute(connection: McpConnection): String
}

class GitMcpServer {
    suspend fun gitGetCurrentBranch(projectPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("git", "-C", projectPath, "rev-parse", "--abbrev-ref", "HEAD")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            val code = process.waitFor()
            if (code != 0 || output.isBlank()) {
                error("git_get_current_branch failed for path=$projectPath; output=$output")
            }
            output
        }
    }
}

class GitBranchTool(
    private val gitMcpServer: GitMcpServer = GitMcpServer()
) : McpTool {
    override suspend fun execute(connection: McpConnection): String {
        if (connection.connectionType != McpConnectionType.GIT) {
            return "{\"tool\":\"unsupported\",\"connectionType\":\"${connection.connectionType.name}\"}"
        }

        val branch = gitMcpServer.gitGetCurrentBranch(connection.projectPath).getOrElse { throwable ->
            "unknown (${throwable.message})"
        }

        return "{\"tool\":\"git_get_current_branch\",\"branch\":\"$branch\",\"projectPath\":\"${connection.projectPath}\"}"
    }
}
