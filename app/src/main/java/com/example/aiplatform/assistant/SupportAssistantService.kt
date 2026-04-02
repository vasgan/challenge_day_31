package com.example.aiplatform.assistant

import com.example.aiplatform.data.mcp.support.SupportMcpServer
import com.example.aiplatform.domain.model.MessageRole
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository

class SupportAssistantService(
    private val projectRepository: ProjectRepository,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val ragRepository: RagRepository,
    private val openAiRepository: OpenAiRepository,
    private val supportMcpServer: SupportMcpServer,
    private val promptBuilder: SupportAssistantPromptBuilder
) : SupportAssistantHandler {

    override suspend fun setActiveUser(projectId: String, chatId: String, userId: String): SupportAssistantResult {
        val normalized = userId.trim()
        if (normalized.isBlank()) {
            return SupportAssistantResult(
                answer = "Используйте формат: /support_user <userId>",
                usedRag = false,
                usedMcp = false,
                activeUserId = null,
                activeTicketId = resolveActiveTicketId(chatId)
            )
        }

        val user = supportMcpServer.supportGetUser(projectId, normalized).getOrElse { throwable ->
            return SupportAssistantResult(
                answer = throwable.message ?: "Пользователь не найден",
                usedRag = false,
                usedMcp = true,
                activeUserId = null,
                activeTicketId = resolveActiveTicketId(chatId)
            )
        }

        return SupportAssistantResult(
            answer = "Активный пользователь установлен: ${user.id} (${user.name})",
            usedRag = false,
            usedMcp = true,
            activeUserId = user.id,
            activeTicketId = resolveActiveTicketId(chatId)
        )
    }

    override suspend fun setActiveTicket(projectId: String, chatId: String, ticketId: String): SupportAssistantResult {
        val normalized = ticketId.trim()
        if (normalized.isBlank()) {
            return SupportAssistantResult(
                answer = "Используйте формат: /support_ticket <ticketId>",
                usedRag = false,
                usedMcp = false,
                activeUserId = resolveActiveUserId(chatId),
                activeTicketId = null
            )
        }

        val ticket = supportMcpServer.supportGetTicket(projectId, normalized).getOrElse { throwable ->
            return SupportAssistantResult(
                answer = throwable.message ?: "Тикет не найден",
                usedRag = false,
                usedMcp = true,
                activeUserId = resolveActiveUserId(chatId),
                activeTicketId = null
            )
        }

        return SupportAssistantResult(
            answer = "Активный тикет установлен: ${ticket.id} (${ticket.subject})",
            usedRag = false,
            usedMcp = true,
            activeUserId = ticket.userId ?: resolveActiveUserId(chatId),
            activeTicketId = ticket.id
        )
    }

    override suspend fun answer(projectId: String, chatId: String, question: String): SupportAssistantResult {
        val normalizedQuestion = question.trim()
        if (normalizedQuestion.isBlank()) {
            return SupportAssistantResult(
                answer = "Используйте формат: /support <вопрос>",
                usedRag = false,
                usedMcp = false,
                activeUserId = resolveActiveUserId(chatId),
                activeTicketId = resolveActiveTicketId(chatId)
            )
        }

        val project = projectRepository.getProject(projectId)
            ?: return SupportAssistantResult(
                answer = "Проект не найден.",
                usedRag = false,
                usedMcp = false,
                activeUserId = null,
                activeTicketId = null
            )

        val activeUserId = resolveActiveUserId(chatId)
        val activeTicketId = resolveActiveTicketId(chatId)

        val supportContext = supportMcpServer
            .supportBuildContext(projectId, activeUserId, activeTicketId)
            .getOrNull()

        val ragChunks = runCatching {
            ragRepository.retrieve(projectId, normalizedQuestion, topK = 5)
        }.getOrDefault(emptyList())

        if (supportContext == null && ragChunks.isEmpty()) {
            return SupportAssistantResult(
                answer = "Недостаточно данных для ответа. Подключите Support MCP и импортируйте FAQ/Support Docs в RAG.",
                usedRag = false,
                usedMcp = false,
                activeUserId = activeUserId,
                activeTicketId = activeTicketId
            )
        }

        val context = promptBuilder.buildContext(
            projectTitle = project.title,
            memorySummary = memoryRepository.getMemory(projectId)?.summary.orEmpty(),
            chatWindow = chatRepository.getMessages(chatId).takeLast(10),
            supportContext = supportContext,
            docs = ragChunks,
            userQuestion = normalizedQuestion
        )

        val answer = runCatching {
            openAiRepository.responses(
                model = project.selectedModel,
                systemPrompt = promptBuilder.systemPrompt(),
                context = context,
                userInput = normalizedQuestion
            )
        }.getOrElse { throwable ->
            "Ошибка при обращении к OpenAI: ${throwable.message ?: "unknown"}"
        }

        return SupportAssistantResult(
            answer = answer,
            usedRag = ragChunks.isNotEmpty(),
            usedMcp = supportContext != null,
            activeUserId = supportContext?.user?.id ?: activeUserId,
            activeTicketId = supportContext?.ticket?.id ?: activeTicketId
        )
    }

    private suspend fun resolveActiveUserId(chatId: String): String? {
        return chatRepository.getMessages(chatId)
            .asReversed()
            .firstOrNull { it.role == MessageRole.USER && it.content.trim().startsWith("/support_user ") }
            ?.content
            ?.trim()
            ?.removePrefix("/support_user")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun resolveActiveTicketId(chatId: String): String? {
        return chatRepository.getMessages(chatId)
            .asReversed()
            .firstOrNull { it.role == MessageRole.USER && it.content.trim().startsWith("/support_ticket ") }
            ?.content
            ?.trim()
            ?.removePrefix("/support_ticket")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
