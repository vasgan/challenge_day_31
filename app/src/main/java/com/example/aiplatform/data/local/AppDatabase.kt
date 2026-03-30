package com.example.aiplatform.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.aiplatform.data.local.dao.ChatDao
import com.example.aiplatform.data.local.dao.McpDao
import com.example.aiplatform.data.local.dao.MessageDao
import com.example.aiplatform.data.local.dao.ProjectDao
import com.example.aiplatform.data.local.dao.ProjectGithubBindingDao
import com.example.aiplatform.data.local.dao.ProjectMemoryDao
import com.example.aiplatform.data.local.dao.RagDao
import com.example.aiplatform.data.local.entity.ChatEntity
import com.example.aiplatform.data.local.entity.McpConnectionEntity
import com.example.aiplatform.data.local.entity.MessageEntity
import com.example.aiplatform.data.local.entity.ProjectEntity
import com.example.aiplatform.data.local.entity.ProjectGithubBindingEntity
import com.example.aiplatform.data.local.entity.ProjectMemoryEntity
import com.example.aiplatform.data.local.entity.RagChunkEntity
import com.example.aiplatform.data.local.entity.RagIndexEntity

@Database(
    entities = [
        ProjectEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        ProjectMemoryEntity::class,
        ProjectGithubBindingEntity::class,
        McpConnectionEntity::class,
        RagIndexEntity::class,
        RagChunkEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun projectMemoryDao(): ProjectMemoryDao
    abstract fun projectGithubBindingDao(): ProjectGithubBindingDao
    abstract fun mcpDao(): McpDao
    abstract fun ragDao(): RagDao
}
