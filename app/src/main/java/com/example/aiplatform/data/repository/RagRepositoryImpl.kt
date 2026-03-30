package com.example.aiplatform.data.repository

import com.example.aiplatform.data.local.dao.RagDao
import com.example.aiplatform.data.mapper.toDomain
import com.example.aiplatform.data.local.entity.RagChunkEntity
import com.example.aiplatform.data.mapper.toEntity
import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.RagRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RagRepositoryImpl(
    private val ragDao: RagDao,
    private val openAiRepository: OpenAiRepository
) : RagRepository {

    override fun observeIndexes(projectId: String): Flow<List<RagIndex>> =
        ragDao.observeIndexes(projectId).map { list -> list.map { it.toDomain() } }

    override suspend fun listActiveIndexes(projectId: String): List<RagIndex> =
        ragDao.listActiveIndexes(projectId).map { it.toDomain() }

    override suspend fun upsertIndex(index: RagIndex) {
        ragDao.upsertIndex(index.toEntity())
    }

    override suspend fun addDocuments(index: RagIndex, chunks: List<String>) {
        if (chunks.isEmpty()) return
        val embeddings = openAiRepository.embeddings(chunks)
        val entities = chunks.mapIndexed { i, text ->
            RagChunkEntity(
                id = UUID.randomUUID().toString(),
                indexId = index.id,
                projectId = index.projectId,
                content = text,
                embeddingJson = embeddings[i].joinToString(",")
            )
        }
        ragDao.insertChunks(entities)
    }

    override suspend fun retrieve(projectId: String, query: String, topK: Int): List<RagChunk> {
        val indexes = ragDao.listActiveIndexes(projectId)
        if (indexes.isEmpty()) return emptyList()

        val queryEmbedding = openAiRepository.embeddings(listOf(query)).firstOrNull().orEmpty()
        if (queryEmbedding.isEmpty()) return emptyList()

        val chunks = ragDao.listChunksForIndexes(projectId, indexes.map { it.id })
            .map { it.toDomain() }

        return chunks
            .map { chunk -> chunk to cosineSimilarity(queryEmbedding, chunk.embedding) }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    private fun cosineSimilarity(left: List<Double>, right: List<Double>): Double {
        if (left.size != right.size || left.isEmpty()) return 0.0
        var dot = 0.0
        var normLeft = 0.0
        var normRight = 0.0
        for (i in left.indices) {
            dot += left[i] * right[i]
            normLeft += left[i] * left[i]
            normRight += right[i] * right[i]
        }
        if (normLeft == 0.0 || normRight == 0.0) return 0.0
        return dot / (kotlin.math.sqrt(normLeft) * kotlin.math.sqrt(normRight))
    }
}
