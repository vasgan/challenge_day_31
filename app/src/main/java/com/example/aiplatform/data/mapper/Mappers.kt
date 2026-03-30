package com.example.aiplatform.data.mapper

import com.example.aiplatform.data.local.entity.ChatEntity
import com.example.aiplatform.data.local.entity.McpConnectionEntity
import com.example.aiplatform.data.local.entity.MessageEntity
import com.example.aiplatform.data.local.entity.ProjectEntity
import com.example.aiplatform.data.local.entity.ProjectGithubBindingEntity
import com.example.aiplatform.data.local.entity.ProjectMemoryEntity
import com.example.aiplatform.data.local.entity.RagChunkEntity
import com.example.aiplatform.data.local.entity.RagIndexEntity
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.McpConnection
import com.example.aiplatform.domain.model.McpConnectionType
import com.example.aiplatform.domain.model.Message
import com.example.aiplatform.domain.model.MessageRole
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectGithubBinding
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.model.RagChunk
import com.example.aiplatform.domain.model.RagIndex

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    title = title,
    description = description,
    selectedModel = ProjectTextModel.fromApiName(selectedModel),
    createdAt = createdAt,
    rootPath = rootPath
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    title = title,
    description = description,
    selectedModel = selectedModel.apiName,
    createdAt = createdAt,
    rootPath = rootPath
)

fun ChatEntity.toDomain(): Chat = Chat(id, projectId, title)

fun Chat.toEntity(): ChatEntity = ChatEntity(id, projectId, title)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    chatId = chatId,
    role = MessageRole.valueOf(role),
    content = content,
    metadata = metadata,
    createdAt = createdAt
)

fun Message.toEntity(archived: Boolean = false): MessageEntity = MessageEntity(
    id = id,
    chatId = chatId,
    role = role.name,
    content = content,
    metadata = metadata,
    createdAt = createdAt,
    archived = archived
)

fun ProjectMemoryEntity.toDomain(): ProjectMemory = ProjectMemory(
    projectId = projectId,
    summary = summary,
    updatedAt = updatedAt
)

fun ProjectMemory.toEntity(): ProjectMemoryEntity = ProjectMemoryEntity(projectId, summary, updatedAt)

fun McpConnectionEntity.toDomain(): McpConnection = McpConnection(
    id = id,
    projectId = projectId,
    name = name,
    serverUrl = serverUrl,
    projectPath = projectPath,
    connectionType = McpConnectionType.valueOf(connectionType)
)

fun McpConnection.toEntity(): McpConnectionEntity = McpConnectionEntity(
    id = id,
    projectId = projectId,
    name = name,
    serverUrl = serverUrl,
    projectPath = projectPath,
    connectionType = connectionType.name
)

fun RagIndexEntity.toDomain(): RagIndex = RagIndex(id, projectId, title, isActive)

fun RagIndex.toEntity(): RagIndexEntity = RagIndexEntity(id, projectId, title, isActive)

fun RagChunkEntity.toDomain(): RagChunk = RagChunk(
    id = id,
    indexId = indexId,
    projectId = projectId,
    content = content,
    embedding = embeddingJson.split(",").mapNotNull { it.toDoubleOrNull() },
    source = source,
    section = section
)

fun RagChunk.toEntity(): RagChunkEntity = RagChunkEntity(
    id = id,
    indexId = indexId,
    projectId = projectId,
    content = content,
    embeddingJson = embedding.joinToString(","),
    source = source,
    section = section
)

fun ProjectGithubBindingEntity.toDomain(): ProjectGithubBinding = ProjectGithubBinding(
    projectId = projectId,
    owner = owner,
    repo = repo,
    repoUrl = repoUrl,
    defaultBranch = defaultBranch,
    readmeImportedAt = readmeImportedAt,
    ragIndexId = ragIndexId,
    createdAt = createdAt
)

fun ProjectGithubBinding.toEntity(): ProjectGithubBindingEntity = ProjectGithubBindingEntity(
    projectId = projectId,
    owner = owner,
    repo = repo,
    repoUrl = repoUrl,
    defaultBranch = defaultBranch,
    readmeImportedAt = readmeImportedAt,
    ragIndexId = ragIndexId,
    createdAt = createdAt
)
