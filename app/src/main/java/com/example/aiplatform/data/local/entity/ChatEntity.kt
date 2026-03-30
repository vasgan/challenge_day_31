package com.example.aiplatform.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    indices = [Index("projectId")]
)
data class ChatEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String
)
