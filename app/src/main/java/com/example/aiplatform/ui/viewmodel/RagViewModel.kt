package com.example.aiplatform.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.RagRepository
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RagViewModel(
    private val projectId: String,
    private val ragRepository: RagRepository
) : ViewModel() {
    val indexes: StateFlow<List<RagIndex>> = ragRepository.observeIndexes(projectId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addIndex(title: String) {
        viewModelScope.launch {
            ragRepository.upsertIndex(
                RagIndex(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    title = title,
                    isActive = true
                )
            )
        }
    }

    fun addDocument(index: RagIndex, rawText: String) {
        val chunks = rawText.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            ragRepository.addDocuments(index, chunks)
        }
    }
}
