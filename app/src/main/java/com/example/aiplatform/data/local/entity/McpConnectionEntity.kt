package com.example.aiplatform.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mcp_connections",
    indices = [Index("projectId")]
)
data class McpConnectionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val serverUrl: String
)
