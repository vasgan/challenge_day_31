package com.example.aiplatform.agent

import com.example.aiplatform.assistant.DeveloperAssistantHandler
import com.example.aiplatform.assistant.PullRequestListResult
import com.example.aiplatform.assistant.PullRequestReviewExecutionResult
import com.example.aiplatform.assistant.PullRequestReviewHandler
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
    private val memoryAgent: MemoryAgent,
    private val developerAssistantHandler: DeveloperAssistantHandler,
    private val pullRequestReviewHandler: PullRequestReviewHandler
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

        val trimmedInput = userInput.trim()

        if (trimmedInput == "/review_pr") {
            val result = runCatching {
                pullRequestReviewHandler.listOpenPrs(projectId)
            }.getOrElse { throwable ->
                PullRequestListResult(
                    answer = "Ошибка PR review: ${throwable.message ?: "unknown"}",
                    success = false
                )
            }
            chatRepository.addMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    role = MessageRole.ASSISTANT,
                    content = result.answer,
                    metadata = "{\"reviewCommand\":true,\"mode\":\"list\",\"success\":${result.success}}",
                    createdAt = System.currentTimeMillis()
                )
            )
            return AgentResult(
                answer = result.answer,
                usedRag = false,
                usedMcp = true
            )
        }

        if (trimmedInput.startsWith("/review_pr ")) {
            val prNumber = trimmedInput.removePrefix("/review_pr").trim().toIntOrNull()
            if (prNumber == null) {
                val answer = "Неверный формат. Используйте /review_pr <number>"
                chatRepository.addMessage(
                    Message(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        role = MessageRole.ASSISTANT,
                        content = answer,
                        metadata = "{\"reviewCommand\":true,\"mode\":\"run\",\"prNumber\":-1,\"postedToGithub\":false,\"usedRag\":false,\"usedMcp\":false}",
                        createdAt = System.currentTimeMillis()
                    )
                )
                return AgentResult(answer = answer, usedRag = false, usedMcp = false)
            }

            val safeResult = runCatching {
                pullRequestReviewHandler.reviewPr(projectId, chatId, prNumber)
            }.getOrElse { throwable ->
                PullRequestReviewExecutionResult(
                    answer = "Ошибка PR review: ${throwable.message ?: "unknown"}",
                    usedRag = false,
                    usedMcp = false,
                    postedToGithub = false
                )
            }
            chatRepository.addMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    role = MessageRole.ASSISTANT,
                    content = safeResult.answer,
                    metadata = "{\"reviewCommand\":true,\"mode\":\"run\",\"prNumber\":$prNumber,\"postedToGithub\":${safeResult.postedToGithub},\"usedRag\":${safeResult.usedRag},\"usedMcp\":${safeResult.usedMcp}}",
                    createdAt = System.currentTimeMillis()
                )
            )
            return AgentResult(
                answer = safeResult.answer,
                usedRag = safeResult.usedRag,
                usedMcp = safeResult.usedMcp
            )
        }

        if (trimmedInput.startsWith("/help")) {
            val helpQuestion = trimmedInput.removePrefix("/help").trim()
            val helpResult = developerAssistantHandler.handleHelp(projectId, chatId, helpQuestion)
            chatRepository.addMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    role = MessageRole.ASSISTANT,
                    content = helpResult.answer,
                    metadata = "{\"helpCommand\":true,\"usedRag\":${helpResult.usedRag},\"usedMcp\":${helpResult.usedMcp}}",
                    createdAt = System.currentTimeMillis()
                )
            )
            return AgentResult(
                answer = helpResult.answer,
                usedRag = helpResult.usedRag,
                usedMcp = helpResult.usedMcp
            )
        }

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
