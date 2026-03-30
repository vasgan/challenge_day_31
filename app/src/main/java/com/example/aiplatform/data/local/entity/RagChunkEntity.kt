package com.example.aiplatform.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rag_chunks",
    indices = [Index("projectId"), Index("indexId")]
)
data class RagChunkEntity(
    @PrimaryKey val id: String,
    val indexId: String,
    val projectId: String,
    val content: String,
    val embeddingJson: String,
    val source: String,
    val section: String
)
