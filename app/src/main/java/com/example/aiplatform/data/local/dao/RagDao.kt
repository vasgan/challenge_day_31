package com.example.aiplatform.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aiplatform.data.local.entity.RagChunkEntity
import com.example.aiplatform.data.local.entity.RagIndexEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RagDao {
    @Query("SELECT * FROM rag_indexes WHERE projectId = :projectId ORDER BY title")
    fun observeIndexes(projectId: String): Flow<List<RagIndexEntity>>

    @Query("SELECT * FROM rag_indexes WHERE projectId = :projectId AND isActive = 1")
    suspend fun listActiveIndexes(projectId: String): List<RagIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIndex(index: RagIndexEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<RagChunkEntity>)

    @Query("SELECT * FROM rag_chunks WHERE projectId = :projectId AND indexId IN (:indexIds)")
    suspend fun listChunksForIndexes(projectId: String, indexIds: List<String>): List<RagChunkEntity>
}
