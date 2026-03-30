package com.example.aiplatform.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aiplatform.data.local.entity.McpConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface McpDao {
    @Query("SELECT * FROM mcp_connections WHERE projectId = :projectId ORDER BY name")
    fun observeByProject(projectId: String): Flow<List<McpConnectionEntity>>

    @Query("SELECT * FROM mcp_connections WHERE projectId = :projectId ORDER BY name")
    suspend fun listByProject(projectId: String): List<McpConnectionEntity>

    @Query("SELECT * FROM mcp_connections WHERE projectId = :projectId AND connectionType = :connectionType ORDER BY name")
    suspend fun listByProjectAndType(projectId: String, connectionType: String): List<McpConnectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connection: McpConnectionEntity)
}
