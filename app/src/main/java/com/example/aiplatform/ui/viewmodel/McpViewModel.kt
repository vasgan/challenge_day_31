package com.example.aiplatform.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiplatform.domain.model.McpConnection
import com.example.aiplatform.domain.repository.McpRepository
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class McpViewModel(
    private val projectId: String,
    private val mcpRepository: McpRepository
) : ViewModel() {
    val connections: StateFlow<List<McpConnection>> = mcpRepository.observeConnections(projectId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addConnection(serverUrl: String) {
        viewModelScope.launch {
            mcpRepository.upsertConnection(
                McpConnection(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    serverUrl = serverUrl
                )
            )
        }
    }
}
