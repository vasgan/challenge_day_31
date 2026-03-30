package com.example.aiplatform.domain.model

data class Project(
    val id: String,
    val title: String,
    val description: String,
    val selectedModel: ProjectTextModel,
    val createdAt: Long
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
    val serverUrl: String
)

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
    val embedding: List<Double>
)

object MemoryPolicy {
    const val SHORT_WINDOW_SIZE = 10
    const val ARCHIVED_LIMIT = 30
}
