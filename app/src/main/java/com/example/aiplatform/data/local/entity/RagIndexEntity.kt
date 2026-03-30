package com.example.aiplatform.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rag_indexes",
    indices = [Index("projectId")]
)
data class RagIndexEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val isActive: Boolean
)
