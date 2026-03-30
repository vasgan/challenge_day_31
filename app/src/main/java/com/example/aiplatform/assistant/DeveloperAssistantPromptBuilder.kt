package com.example.aiplatform.assistant

import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.RagChunk

class DeveloperAssistantPromptBuilder {
    fun buildContext(
        projectTitle: String,
        projectMemory: String,
        branch: String?,
        docs: List<RagChunk>,
        chatWindow: List<Message>,
        userQuestion: String
    ): String {
        val memoryBlock = projectMemory.ifBlank { "No project memory summary." }
        val branchBlock = branch ?: "unknown (Git MCP not configured)"
        val docsBlock = if (docs.isEmpty()) {
            "No relevant documentation retrieved."
        } else {
            docs.joinToString("\n\n") { chunk ->
                "[Source: ${chunk.source} | Section: ${chunk.section}]\n${chunk.content}"
            }
        }
        val chatBlock = if (chatWindow.isEmpty()) {
            "No recent chat history."
        } else {
            chatWindow.joinToString("\n") { "${it.role}: ${it.content}" }
        }

        return buildString {
            appendLine("Project context:")
            appendLine("- Project title: $projectTitle")
            appendLine("- Current git branch: $branchBlock")
            appendLine()
            appendLine("Project memory:")
            appendLine(memoryBlock)
            appendLine()
            appendLine("Recent chat (last 10):")
            appendLine(chatBlock)
            appendLine()
            appendLine("Relevant documentation:")
            appendLine(docsBlock)
            appendLine()
            appendLine("User question:")
            appendLine(userQuestion)
        }
    }

    fun systemPrompt(): String =
        "You are a developer assistant for the current software project. " +
            "Answer using the provided project documentation and project context. " +
            "If the answer is not supported by documentation or context, say that you do not know."
}
