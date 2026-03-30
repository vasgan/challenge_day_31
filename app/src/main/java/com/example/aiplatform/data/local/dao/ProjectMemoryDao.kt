package com.example.aiplatform.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aiplatform.data.local.entity.ProjectMemoryEntity

@Dao
interface ProjectMemoryDao {
    @Query("SELECT * FROM project_memory WHERE projectId = :projectId LIMIT 1")
    suspend fun get(projectId: String): ProjectMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: ProjectMemoryEntity)
}
