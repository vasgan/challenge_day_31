package com.example.aiplatform.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aiplatform.data.local.entity.ProjectGithubBindingEntity

@Dao
interface ProjectGithubBindingDao {
    @Query("SELECT * FROM project_github_bindings WHERE projectId = :projectId LIMIT 1")
    suspend fun getBinding(projectId: String): ProjectGithubBindingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(binding: ProjectGithubBindingEntity)

    @Query(
        "UPDATE project_github_bindings SET readmeImportedAt = :readmeImportedAt, ragIndexId = :ragIndexId WHERE projectId = :projectId"
    )
    suspend fun updateReadmeImport(projectId: String, readmeImportedAt: Long, ragIndexId: String?)
}
