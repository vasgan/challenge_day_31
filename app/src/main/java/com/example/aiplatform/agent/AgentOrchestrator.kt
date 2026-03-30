package com.example.aiplatform.agent

import com.example.aiplatform.domain.agent.AgentContext
import com.example.aiplatform.domain.agent.AgentResult
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.MessageRole
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import java.util.UUID

class ProjectNotFoundException(projectId: String) : IllegalArgumentException("Project not found: $projectId")
class ChatNotFoundException(chatId: String) : IllegalArgumentException("Chat not found: $chatId")
class ModelNotSelectedException(projectId: String) : IllegalStateException("Model not selected for project: $projectId")

class AgentOrchestrator(
    private val projectRepository: ProjectRepository,
    private val chatRepository: ChatRepository,
    private val openAiRepository: OpenAiRepository,
    private val chatAgent: ChatAgent,
    private val ragAgent: RagAgent,
    private val mcpAgent: McpAgent,
    private val memoryAgent: MemoryAgent
) {

    suspend fun sendMessage(projectId: String, chatId: String, userInput: String): AgentResult {
        val project = projectRepository.getProject(projectId) ?: throw ProjectNotFoundException(projectId)
        chatRepository.getChat(chatId) ?: throw ChatNotFoundException(chatId)

        ensureModel(project)

        chatRepository.addMessage(
            Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.USER,
                content = userInput,
                metadata = "{}",
                createdAt = System.currentTimeMillis()
            )
        )

        val memoryWindow = memoryAgent.collect(project, chatId)
        val chatWindow = chatAgent.chatWindow(chatId)
        val ragChunks = ragAgent.retrieve(projectId, userInput)
        val mcpData = mcpAgent.collect(projectId)

        val agentContext = AgentContext(
            project = project,
            chat = chatRepository.getChat(chatId)!!,
            userInput = userInput,
            memory = memoryWindow.memory,
            chatWindow = chatWindow,
            ragChunks = ragChunks,
            mcpData = mcpData
        )

        val answer = openAiRepository.responses(
            model = project.selectedModel,
            systemPrompt = "You are an assistant for a software project.",
            context = buildContext(agentContext),
            userInput = userInput
        )

        chatRepository.addMessage(
            Message(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                content = answer,
                metadata = "{\"usedRag\":${ragChunks.isNotEmpty()},\"usedMcp\":${mcpData.isNotEmpty()}}",
                createdAt = System.currentTimeMillis()
            )
        )

        return AgentResult(
            answer = answer,
            usedRag = ragChunks.isNotEmpty(),
            usedMcp = mcpData.isNotEmpty()
        )
    }

    private fun ensureModel(project: Project) {
        if (project.selectedModel !in ProjectTextModel.entries) {
            throw ModelNotSelectedException(project.id)
        }
    }

    private fun buildContext(context: AgentContext): String {
        val memorySection = context.memory?.summary.orEmpty().ifBlank { "No project memory yet." }
        val chatSection = context.chatWindow.joinToString("\n") { "${it.role}: ${it.content}" }
        val ragSection = if (context.ragChunks.isEmpty()) ""
        else context.ragChunks.joinToString("\n") { "RAG: ${it.content}" }
        val mcpSection = if (context.mcpData.isEmpty()) ""
        else context.mcpData.joinToString("\n") { "MCP(${it.connection.serverUrl}): ${it.payload}" }

        return buildString {
            appendLine("Project memory:")
            appendLine(memorySection)
            appendLine()
            appendLine("Chat history (last 10):")
            appendLine(chatSection)
            if (ragSection.isNotBlank()) {
                appendLine()
                appendLine("RAG chunks:")
                appendLine(ragSection)
            }
            if (mcpSection.isNotBlank()) {
                appendLine()
                appendLine("MCP data:")
                appendLine(mcpSection)
            }
        }
    }
}
