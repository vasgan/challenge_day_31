package com.example.aiplatform.domain.repository

import com.example.aiplatform.domain.model.ProjectTextModel

interface OpenAiRepository {
    suspend fun responses(
        model: ProjectTextModel,
        systemPrompt: String,
        context: String,
        userInput: String
    ): String

    suspend fun summarizeMemory(
        model: ProjectTextModel,
        currentSummary: String,
        archivedConversation: String
    ): String

    suspend fun embeddings(input: List<String>): List<List<Double>>
}
