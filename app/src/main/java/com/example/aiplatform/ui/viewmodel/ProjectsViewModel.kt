package com.example.aiplatform.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiplatform.domain.model.Chat
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(
    private val projectRepository: ProjectRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {
    val projects: StateFlow<List<Project>> = projectRepository.observeProjects().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun createProject(title: String, description: String) {
        viewModelScope.launch {
            val projectId = UUID.randomUUID().toString()
            projectRepository.createProject(
                Project(
                    id = projectId,
                    title = title,
                    description = description,
                    selectedModel = ProjectTextModel.GPT_5_MINI,
                    createdAt = System.currentTimeMillis()
                )
            )
            chatRepository.createChat(
                Chat(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    title = "General"
                )
            )
        }
    }
}
