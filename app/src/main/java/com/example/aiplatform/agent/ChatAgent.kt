package com.example.aiplatform.agent

import com.example.aiplatform.domain.agent.AgentContext
import com.example.aiplatform.domain.agent.AgentResult
import com.example.aiplatform.domain.agent.SubAgent
import com.example.aiplatform.domain.model.MemoryPolicy
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.repository.ChatRepository

class ChatAgent(
    private val chatRepository: ChatRepository
) : SubAgent {
    suspend fun chatWindow(chatId: String): List<Message> =
        chatRepository.getMessages(chatId).takeLast(MemoryPolicy.SHORT_WINDOW_SIZE)

    override suspend fun handle(context: AgentContext): AgentResult {
        return AgentResult(answer = "", usedRag = false, usedMcp = false)
    }
}
