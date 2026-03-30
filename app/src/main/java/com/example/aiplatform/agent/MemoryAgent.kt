package com.example.aiplatform.agent

import com.example.aiplatform.data.memory.MemoryWindow
import com.example.aiplatform.data.memory.ProjectMemoryManager
import com.example.aiplatform.domain.agent.AgentContext
import com.example.aiplatform.domain.agent.AgentResult
import com.example.aiplatform.domain.agent.SubAgent
import com.example.aiplatform.domain.model.Project

class MemoryAgent(
    private val memoryManager: ProjectMemoryManager
) : SubAgent {
    suspend fun collect(project: Project, chatId: String): MemoryWindow =
        memoryManager.prepare(project, chatId)

    override suspend fun handle(context: AgentContext): AgentResult {
        return AgentResult(answer = "", usedRag = false, usedMcp = false)
    }
}
