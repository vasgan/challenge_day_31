package com.example.aiplatform

import com.example.aiplatform.data.local.dao.RagDao
import com.example.aiplatform.data.local.entity.RagChunkEntity
import com.example.aiplatform.data.local.entity.RagIndexEntity
import com.example.aiplatform.data.repository.RagRepositoryImpl
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.OpenAiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagOpenAiOnlyTest {
    @Test
    fun `rag uses OpenAI embeddings and never responses`() = runTest {
        val ragDao = FakeRagDao()
        val openAi = FakeOpenAiRepository()
        val ragRepo = RagRepositoryImpl(ragDao, openAi)

        val index = RagIndex("idx", "project-1", "Docs", true)
        ragDao.upsertIndex(RagIndexEntity(index.id, index.projectId, index.title, index.isActive))

        ragRepo.addDocuments(
            index,
            listOf(
                RagDocumentChunk("chunk one", "README.md", "part-1"),
                RagDocumentChunk("chunk two", "README.md", "part-2")
            )
        )
        ragRepo.retrieve("project-1", "query", topK = 1)

        assertTrue(openAi.embeddingCalls >= 2)
        assertEquals(0, openAi.responsesCalls)
    }

    private class FakeOpenAiRepository : OpenAiRepository {
        var embeddingCalls = 0
        var responsesCalls = 0

        override suspend fun responses(
            model: ProjectTextModel,
            systemPrompt: String,
            context: String,
            userInput: String
        ): String {
            responsesCalls += 1
            return "no"
        }

        override suspend fun summarizeMemory(
            model: ProjectTextModel,
            currentSummary: String,
            archivedConversation: String
        ): String = currentSummary

        override suspend fun embeddings(input: List<String>): List<List<Double>> {
            embeddingCalls += 1
            return input.map { listOf(1.0, 0.0, 0.0) }
        }
    }

    private class FakeRagDao : RagDao {
        private val indexes = mutableListOf<RagIndexEntity>()
        private val chunks = mutableListOf<RagChunkEntity>()

        override fun observeIndexes(projectId: String): Flow<List<RagIndexEntity>> = emptyFlow()

        override suspend fun listActiveIndexes(projectId: String): List<RagIndexEntity> =
            indexes.filter { it.projectId == projectId && it.isActive }

        override suspend fun upsertIndex(index: RagIndexEntity) {
            indexes.removeAll { it.id == index.id }
            indexes += index
        }

        override suspend fun insertChunks(chunks: List<RagChunkEntity>) {
            this.chunks += chunks
        }

        override suspend fun listChunksForIndexes(projectId: String, indexIds: List<String>): List<RagChunkEntity> =
            chunks.filter { it.projectId == projectId && it.indexId in indexIds }
    }
}
