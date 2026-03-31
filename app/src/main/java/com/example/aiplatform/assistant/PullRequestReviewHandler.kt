package com.example.aiplatform.assistant

data class PullRequestListResult(
    val answer: String,
    val success: Boolean
)

data class PullRequestReviewExecutionResult(
    val answer: String,
    val usedRag: Boolean,
    val usedMcp: Boolean,
    val postedToGithub: Boolean
)

interface PullRequestReviewHandler {
    suspend fun listOpenPrs(projectId: String): PullRequestListResult
    suspend fun reviewPr(projectId: String, chatId: String, prNumber: Int): PullRequestReviewExecutionResult
}
