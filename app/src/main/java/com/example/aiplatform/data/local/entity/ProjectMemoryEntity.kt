package com.example.aiplatform.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "project_memory")
data class ProjectMemoryEntity(
    @PrimaryKey val projectId: String,
    val summary: String,
    val updatedAt: Long
)
