package com.example.aiplatform.assistant

import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectGithubBindingRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository

data class DeveloperAssistantResult(
    val answer: String,
    val usedRag: Boolean,
    val usedMcp: Boolean
)

interface DeveloperAssistantHandler {
    suspend fun handleHelp(projectId: String, chatId: String, userQuestion: String): DeveloperAssistantResult
}

class DeveloperAssistantService(
    private val projectRepository: ProjectRepository,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val ragRepository: RagRepository,
    private val projectGithubBindingRepository: ProjectGithubBindingRepository,
    private val openAiRepository: OpenAiRepository,
    private val promptBuilder: DeveloperAssistantPromptBuilder
) : DeveloperAssistantHandler {

    override suspend fun handleHelp(projectId: String, chatId: String, userQuestion: String): DeveloperAssistantResult {
        val question = userQuestion.trim()
        if (question.isBlank()) {
            return DeveloperAssistantResult(
                answer = "Используйте формат: /help <вопрос о проекте>",
                usedRag = false,
                usedMcp = false
            )
        }

        val project = projectRepository.getProject(projectId)
            ?: return DeveloperAssistantResult("Проект не найден.", usedRag = false, usedMcp = false)

        val ragChunks = ragRepository.retrieve(projectId, question, topK = 5)
        val binding = projectGithubBindingRepository.getBinding(projectId)
        val branch = binding?.defaultBranch
        val memory = memoryRepository.getMemory(projectId)
        val chatWindow = chatRepository.getMessages(chatId).takeLast(10)

        if (ragChunks.isEmpty() && branch == null) {
            val fallback = "Недостаточно данных о проекте. Подключите GitHub repo и импортируйте README в RAG."
            return DeveloperAssistantResult(fallback, usedRag = false, usedMcp = false)
        }

        val context = promptBuilder.buildContext(
            projectTitle = project.title,
            projectMemory = memory?.summary.orEmpty(),
            branch = branch,
            docs = ragChunks,
            chatWindow = chatWindow,
            userQuestion = question
        )

        val answer = runCatching {
            openAiRepository.responses(
                model = project.selectedModel,
                systemPrompt = promptBuilder.systemPrompt(),
                context = context,
                userInput = question
            )
        }.getOrElse { throwable ->
            "Ошибка при обращении к OpenAI: ${throwable.message ?: "unknown"}"
        }

        return DeveloperAssistantResult(
            answer = answer,
            usedRag = ragChunks.isNotEmpty(),
            usedMcp = branch != null
        )
    }
}
