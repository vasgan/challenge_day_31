package com.example.aiplatform.assistant

import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.model.SupportContext

class SupportAssistantPromptBuilder {
    fun systemPrompt(): String =
        "You are a support assistant for this product. " +
            "Answer using provided support documentation and user/ticket context. " +
            "If information is missing, say so explicitly."

    fun buildContext(
        projectTitle: String,
        memorySummary: String,
        chatWindow: List<Message>,
        supportContext: SupportContext?,
        docs: List<RagChunk>,
        userQuestion: String
    ): String {
        val userBlock = supportContext?.user?.let { user ->
            buildString {
                appendLine("- id: ${user.id}")
                appendLine("- name: ${user.name}")
                appendLine("- email: ${user.email ?: "unknown"}")
                appendLine("- segment: ${user.segment ?: "unknown"}")
                if (user.metadata.isNotEmpty()) {
                    appendLine("- metadata: ${user.metadata}")
                }
            }.trim()
        } ?: "No active user context."

        val ticketBlock = supportContext?.ticket?.let { ticket ->
            buildString {
                appendLine("- id: ${ticket.id}")
                appendLine("- subject: ${ticket.subject}")
                appendLine("- status: ${ticket.status}")
                appendLine("- priority: ${ticket.priority ?: "unknown"}")
                appendLine("- userId: ${ticket.userId ?: "unknown"}")
                appendLine("- description: ${ticket.description ?: "(empty)"}")
                if (ticket.metadata.isNotEmpty()) {
                    appendLine("- metadata: ${ticket.metadata}")
                }
            }.trim()
        } ?: "No active ticket context."

        val relatedTicketsBlock = supportContext?.relatedTickets.orEmpty().let { tickets ->
            if (tickets.isEmpty()) {
                "No related tickets."
            } else {
                tickets.joinToString("\n") { t -> "- ${t.id}: ${t.subject} [${t.status}]" }
            }
        }

        val memoryBlock = memorySummary.ifBlank { "No project memory summary." }
        val chatBlock = if (chatWindow.isEmpty()) {
            "No recent chat history."
        } else {
            chatWindow.joinToString("\n") { "${it.role}: ${it.content}" }
        }
        val docsBlock = if (docs.isEmpty()) {
            "No relevant support docs were retrieved."
        } else {
            docs.joinToString("\n\n") { chunk ->
                "[Source: ${chunk.source} | Section: ${chunk.section}]\n${chunk.content}"
            }
        }

        return buildString {
            appendLine("Project context:")
            appendLine("- Project title: $projectTitle")
            appendLine()
            appendLine("Active user context:")
            appendLine(userBlock)
            appendLine()
            appendLine("Active ticket context:")
            appendLine(ticketBlock)
            appendLine()
            appendLine("Related tickets:")
            appendLine(relatedTicketsBlock)
            appendLine()
            appendLine("Project memory:")
            appendLine(memoryBlock)
            appendLine()
            appendLine("Recent chat (last 10):")
            appendLine(chatBlock)
            appendLine()
            appendLine("Relevant support documentation:")
            appendLine(docsBlock)
            appendLine()
            appendLine("User question:")
            appendLine(userQuestion)
            appendLine()
            appendLine("Response format:")
            appendLine("1) Probable cause")
            appendLine("2) Checks/steps")
            appendLine("3) Suggested resolution")
            appendLine("4) Escalation needed? (if yes, why)")
        }
    }
}
