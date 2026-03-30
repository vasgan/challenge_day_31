package com.example.aiplatform.agent

import com.example.aiplatform.domain.agent.AgentContext
import com.example.aiplatform.domain.agent.AgentResult
import com.example.aiplatform.domain.agent.SubAgent
import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.repository.RagRepository

class RagAgent(
    private val ragRepository: RagRepository
) : SubAgent {
    suspend fun retrieve(projectId: String, query: String): List<RagChunk> {
        val hasActive = ragRepository.listActiveIndexes(projectId).isNotEmpty()
        if (!hasActive) return emptyList()
        return ragRepository.retrieve(projectId, query, topK = 4)
    }

    override suspend fun handle(context: AgentContext): AgentResult {
        return AgentResult(answer = "", usedRag = context.ragChunks.isNotEmpty(), usedMcp = false)
    }
}
