package com.example.aiplatform.assistant

data class SupportAssistantResult(
    val answer: String,
    val usedRag: Boolean,
    val usedMcp: Boolean,
    val activeUserId: String?,
    val activeTicketId: String?
)

interface SupportAssistantHandler {
    suspend fun setActiveUser(projectId: String, chatId: String, userId: String): SupportAssistantResult
    suspend fun setActiveTicket(projectId: String, chatId: String, ticketId: String): SupportAssistantResult
    suspend fun answer(projectId: String, chatId: String, question: String): SupportAssistantResult
}
