package com.example.aiplatform.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository
) : ViewModel() {
    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _project.value = projectRepository.getProject(projectId)
        }
    }

    fun selectModel(model: ProjectTextModel) {
        viewModelScope.launch {
            projectRepository.updateProjectModel(projectId, model)
            refresh()
        }
    }
}
