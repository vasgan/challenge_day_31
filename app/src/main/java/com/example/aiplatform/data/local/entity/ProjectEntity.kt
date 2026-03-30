package com.example.aiplatform.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val selectedModel: String,
    val createdAt: Long,
    val rootPath: String
)
