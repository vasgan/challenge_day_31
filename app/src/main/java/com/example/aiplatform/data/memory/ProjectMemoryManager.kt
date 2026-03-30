package com.example.aiplatform.data.memory

import com.example.aiplatform.domain.model.MemoryPolicy
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository

data class MemoryWindow(
    val memory: ProjectMemory?,
    val shortWindow: List<Message>,
    val archived: List<Message>
)

class ProjectMemoryManager(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val openAiRepository: OpenAiRepository
) {
    suspend fun prepare(project: Project, chatId: String): MemoryWindow {
        val messages = chatRepository.getMessages(chatId)
        val shortWindow = messages.takeLast(MemoryPolicy.SHORT_WINDOW_SIZE)
        val archived = messages.dropLast(MemoryPolicy.SHORT_WINDOW_SIZE)

        chatRepository.archiveMessages(chatId, archived.map(Message::id))

        var memory = memoryRepository.getMemory(project.id)

        if (shortWindow.size >= MemoryPolicy.SHORT_WINDOW_SIZE && archived.size > MemoryPolicy.ARCHIVED_LIMIT) {
            val overflowCount = archived.size - MemoryPolicy.ARCHIVED_LIMIT
            val toSummarize = archived.take(overflowCount)

            val updatedSummary = openAiRepository.summarizeMemory(
                model = project.selectedModel,
                currentSummary = memory?.summary.orEmpty(),
                archivedConversation = toSummarize.joinToString("\n") { "${it.role}: ${it.content}" }
            )

            memory = ProjectMemory(
                projectId = project.id,
                summary = updatedSummary,
                updatedAt = System.currentTimeMillis()
            )
            memoryRepository.upsertMemory(memory)
            chatRepository.deleteMessages(toSummarize.map(Message::id))
        }

        val currentMessages = chatRepository.getMessages(chatId)
        return MemoryWindow(
            memory = memory,
            shortWindow = currentMessages.takeLast(MemoryPolicy.SHORT_WINDOW_SIZE),
            archived = currentMessages.dropLast(MemoryPolicy.SHORT_WINDOW_SIZE)
        )
    }
}
