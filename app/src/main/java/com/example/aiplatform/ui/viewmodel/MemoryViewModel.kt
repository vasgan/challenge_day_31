package com.example.aiplatform.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiplatform.domain.model.ProjectMemory
import com.example.aiplatform.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MemoryViewModel(
    private val projectId: String,
    private val memoryRepository: MemoryRepository
) : ViewModel() {
    private val _memory = MutableStateFlow<ProjectMemory?>(null)
    val memory: StateFlow<ProjectMemory?> = _memory.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _memory.value = memoryRepository.getMemory(projectId)
        }
    }
}
