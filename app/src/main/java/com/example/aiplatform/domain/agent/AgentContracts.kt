package com.example.aiplatform.domain.agent

import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.McpConnection
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.model.RagChunk

data class AgentContext(
    val project: Project,
    val chat: Chat,
    val userInput: String,
    val memory: ProjectMemory?,
    val chatWindow: List<Message>,
    val ragChunks: List<RagChunk>,
    val mcpData: List<McpResult>
)

data class McpResult(
    val connection: McpConnection,
    val payload: String
)

data class AgentResult(
    val answer: String,
    val usedRag: Boolean,
    val usedMcp: Boolean
)

interface SubAgent {
    suspend fun handle(context: AgentContext): AgentResult
}
