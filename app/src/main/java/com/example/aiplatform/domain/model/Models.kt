package com.example.aiplatform.domain.model

data class Project(
    val id: String,
    val title: String,
    val description: String,
    val selectedModel: ProjectTextModel,
    val createdAt: Long,
    val rootPath: String
)

data class Chat(
    val id: String,
    val projectId: String,
    val title: String
)

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}

data class Message(
    val id: String,
    val chatId: String,
    val role: MessageRole,
    val content: String,
    val metadata: String,
    val createdAt: Long
)

data class ProjectMemory(
    val projectId: String,
    val summary: String,
    val updatedAt: Long
)

data class McpConnection(
    val id: String,
    val projectId: String,
    val name: String,
    val serverUrl: String,
    val projectPath: String,
    val connectionType: McpConnectionType
)

enum class McpConnectionType {
    GENERIC,
    GIT,
    GITHUB,
    SUPPORT
}

data class RagIndex(
    val id: String,
    val projectId: String,
    val title: String,
    val isActive: Boolean
)

data class RagChunk(
    val id: String,
    val indexId: String,
    val projectId: String,
    val content: String,
    val embedding: List<Double>,
    val source: String,
    val section: String
)

data class RagDocumentChunk(
    val content: String,
    val source: String,
    val section: String
)

object MemoryPolicy {
    const val SHORT_WINDOW_SIZE = 10
    const val ARCHIVED_LIMIT = 30
}
