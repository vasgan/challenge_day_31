package com.example.aiplatform.domain.repository

import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import kotlinx.coroutines.flow.Flow

interface RagRepository {
    fun observeIndexes(projectId: String): Flow<List<RagIndex>>
    suspend fun listActiveIndexes(projectId: String): List<RagIndex>
    suspend fun upsertIndex(index: RagIndex)
    suspend fun addDocuments(index: RagIndex, chunks: List<RagDocumentChunk>)
    suspend fun retrieve(projectId: String, query: String, topK: Int): List<RagChunk>
}
