package com.example.aiplatform.assistant

data class FileOpsResult(
    val answer: String,
    val success: Boolean,
    val changedFiles: List<String>,
    val openedPr: Boolean,
    val prUrl: String?
)

interface FileOpsAssistantHandler {
    suspend fun runTask(projectId: String, chatId: String, goal: String): FileOpsResult
}
