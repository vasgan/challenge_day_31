package com.example.aiplatform.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiplatform.assistant.SupportRagBootstrapper
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SupportRagImportStatus {
    Idle,
    Loading,
    Success,
    Error
}

data class SupportRagImportUiState(
    val status: SupportRagImportStatus = SupportRagImportStatus.Idle,
    val message: String = "",
    val error: String = ""
)

class RagViewModel(
    private val projectId: String,
    private val ragRepository: RagRepository,
    private val projectRepository: ProjectRepository,
    private val supportRagBootstrapper: SupportRagBootstrapper
) : ViewModel() {
    val indexes: StateFlow<List<RagIndex>> = ragRepository.observeIndexes(projectId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    private val _supportImportState = MutableStateFlow(SupportRagImportUiState())
    val supportImportState: StateFlow<SupportRagImportUiState> = _supportImportState.asStateFlow()

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
        val chunks = rawText.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { i, chunk ->
                RagDocumentChunk(
                    content = chunk,
                    source = "manual-input",
                    section = "part-${i + 1}"
                )
            }
        viewModelScope.launch {
            ragRepository.addDocuments(index, chunks)
        }
    }

    fun importFaqJson() {
        viewModelScope.launch {
            _supportImportState.value = SupportRagImportUiState(status = SupportRagImportStatus.Loading)
            val project = projectRepository.getProject(projectId)
            if (project == null) {
                _supportImportState.value = SupportRagImportUiState(
                    status = SupportRagImportStatus.Error,
                    error = "Проект не найден"
                )
                return@launch
            }

            supportRagBootstrapper.importFaq(projectId).fold(
                onSuccess = { (indexId, count) ->
                    _supportImportState.value = SupportRagImportUiState(
                        status = SupportRagImportStatus.Success,
                        message = "FAQ imported to $indexId, chunks=$count"
                    )
                },
                onFailure = { throwable ->
                    _supportImportState.value = SupportRagImportUiState(
                        status = SupportRagImportStatus.Error,
                        error = throwable.message ?: "Не удалось импортировать faq.json"
                    )
                }
            )
        }
    }

    fun importSupportDocsMd() {
        viewModelScope.launch {
            _supportImportState.value = SupportRagImportUiState(status = SupportRagImportStatus.Loading)
            val project = projectRepository.getProject(projectId)
            if (project == null) {
                _supportImportState.value = SupportRagImportUiState(
                    status = SupportRagImportStatus.Error,
                    error = "Проект не найден"
                )
                return@launch
            }

            supportRagBootstrapper.importSupportDocs(projectId).fold(
                onSuccess = { (indexId, count) ->
                    _supportImportState.value = SupportRagImportUiState(
                        status = SupportRagImportStatus.Success,
                        message = "Support docs imported to $indexId, chunks=$count"
                    )
                },
                onFailure = { throwable ->
                    _supportImportState.value = SupportRagImportUiState(
                        status = SupportRagImportStatus.Error,
                        error = throwable.message ?: "Не удалось импортировать support_docs.md"
                    )
                }
            )
        }
    }
}
