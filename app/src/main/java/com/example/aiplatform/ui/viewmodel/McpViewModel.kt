package com.example.aiplatform.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiplatform.data.mcp.github.GithubMcpServer
import com.example.aiplatform.data.mcp.support.SupportMcpServer
import com.example.aiplatform.domain.model.GithubRepo
import com.example.aiplatform.domain.model.McpConnection
import com.example.aiplatform.domain.model.McpConnectionType
import com.example.aiplatform.domain.model.ProjectGithubBinding
import com.example.aiplatform.domain.repository.McpRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GithubMcpUiStatus {
    Idle,
    LoadingRepos,
    RepoListLoaded,
    BindingRepo,
    ReadmeImporting,
    RagBuilding,
    Success,
    Error
}

data class GithubMcpUiState(
    val status: GithubMcpUiStatus = GithubMcpUiStatus.Idle,
    val ownerInput: String = "",
    val repos: List<GithubRepo> = emptyList(),
    val selectedRepo: GithubRepo? = null,
    val binding: ProjectGithubBinding? = null,
    val message: String = "",
    val error: String = ""
)

enum class SupportMcpUiStatus {
    Idle,
    Connecting,
    LoadingSnapshot,
    Success,
    Error
}

data class SupportMcpUiState(
    val status: SupportMcpUiStatus = SupportMcpUiStatus.Idle,
    val isConnected: Boolean = false,
    val usersCount: Int = 0,
    val ticketsCount: Int = 0,
    val message: String = "",
    val error: String = ""
)

class McpViewModel(
    private val projectId: String,
    private val mcpRepository: McpRepository,
    private val projectRepository: ProjectRepository,
    private val githubMcpServer: GithubMcpServer,
    private val supportMcpServer: SupportMcpServer
) : ViewModel() {
    val connections: StateFlow<List<McpConnection>> = mcpRepository.observeConnections(projectId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    private val _githubUiState = MutableStateFlow(GithubMcpUiState())
    val githubUiState: StateFlow<GithubMcpUiState> = _githubUiState.asStateFlow()
    private val _supportUiState = MutableStateFlow(SupportMcpUiState())
    val supportUiState: StateFlow<SupportMcpUiState> = _supportUiState.asStateFlow()

    init {
        refreshBinding()
    }

    fun addConnection(serverUrl: String) {
        viewModelScope.launch {
            mcpRepository.upsertConnection(
                McpConnection(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    name = "Generic MCP",
                    serverUrl = serverUrl,
                    projectPath = "",
                    connectionType = McpConnectionType.GENERIC
                )
            )
        }
    }

    fun addGitConnection(name: String, projectPath: String) {
        viewModelScope.launch {
            mcpRepository.upsertConnection(
                McpConnection(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    name = name,
                    serverUrl = "git://local",
                    projectPath = projectPath,
                    connectionType = McpConnectionType.GIT
                )
            )
            projectRepository.updateProjectRootPath(projectId, projectPath)
        }
    }

    fun updateOwner(owner: String) {
        _githubUiState.value = _githubUiState.value.copy(ownerInput = owner, error = "", message = "")
    }

    fun loadReposByOwner() {
        val owner = _githubUiState.value.ownerInput.trim()
        if (owner.isBlank()) {
            _githubUiState.value = _githubUiState.value.copy(
                status = GithubMcpUiStatus.Error,
                error = "Введите owner"
            )
            return
        }

        viewModelScope.launch {
            _githubUiState.value = _githubUiState.value.copy(status = GithubMcpUiStatus.LoadingRepos, error = "")
            val repos = githubMcpServer.githubListUserRepos(owner).getOrElse { throwable ->
                _githubUiState.value = _githubUiState.value.copy(
                    status = GithubMcpUiStatus.Error,
                    error = throwable.message ?: "Не удалось загрузить репозитории",
                    repos = emptyList(),
                    selectedRepo = null
                )
                return@launch
            }

            _githubUiState.value = _githubUiState.value.copy(
                status = GithubMcpUiStatus.RepoListLoaded,
                repos = repos,
                selectedRepo = null,
                message = "Найдено репозиториев: ${repos.size}",
                error = ""
            )
        }
    }

    fun selectRepo(repo: GithubRepo) {
        _githubUiState.value = _githubUiState.value.copy(selectedRepo = repo, error = "", message = "")
    }

    fun bindSelectedRepo() {
        val owner = _githubUiState.value.ownerInput.trim()
        val selectedRepo = _githubUiState.value.selectedRepo
        if (selectedRepo == null) {
            _githubUiState.value = _githubUiState.value.copy(
                status = GithubMcpUiStatus.Error,
                error = "Выберите репозиторий из списка"
            )
            return
        }

        viewModelScope.launch {
            _githubUiState.value = _githubUiState.value.copy(status = GithubMcpUiStatus.BindingRepo, error = "")

            val binding = githubMcpServer.githubBindRepoToProject(projectId, owner, selectedRepo.name).getOrElse { throwable ->
                _githubUiState.value = _githubUiState.value.copy(
                    status = GithubMcpUiStatus.Error,
                    error = throwable.message ?: "Не удалось привязать репозиторий"
                )
                return@launch
            }

            // Register internal GitHub MCP endpoint as project-scoped connection metadata.
            mcpRepository.upsertConnection(
                McpConnection(
                    id = "github-mcp-$projectId",
                    projectId = projectId,
                    name = "GitHub MCP (${binding.owner}/${binding.repo})",
                    serverUrl = "mcp://internal/github",
                    projectPath = "${binding.owner}/${binding.repo}",
                    connectionType = McpConnectionType.GITHUB
                )
            )

            _githubUiState.value = _githubUiState.value.copy(
                status = GithubMcpUiStatus.Success,
                binding = binding,
                message = "Репозиторий привязан: ${binding.owner}/${binding.repo}",
                error = ""
            )
        }
    }

    fun importReadmeAndBuildRag() {
        viewModelScope.launch {
            _githubUiState.value = _githubUiState.value.copy(status = GithubMcpUiStatus.ReadmeImporting, error = "")

            githubMcpServer.githubFetchReadme(projectId).getOrElse { throwable ->
                _githubUiState.value = _githubUiState.value.copy(
                    status = GithubMcpUiStatus.Error,
                    error = throwable.message ?: "Не удалось импортировать README"
                )
                return@launch
            }

            _githubUiState.value = _githubUiState.value.copy(status = GithubMcpUiStatus.RagBuilding)
            val ragResult = githubMcpServer.githubBuildRagFromReadme(projectId).getOrElse { throwable ->
                _githubUiState.value = _githubUiState.value.copy(
                    status = GithubMcpUiStatus.Error,
                    error = throwable.message ?: "Не удалось построить RAG из README"
                )
                return@launch
            }

            refreshBinding()
            _githubUiState.value = _githubUiState.value.copy(
                status = GithubMcpUiStatus.Success,
                message = "README импортирован. RAG index: ${ragResult.ragIndexId}, chunks=${ragResult.chunkCount}",
                error = ""
            )
        }
    }

    fun refreshBinding() {
        viewModelScope.launch {
            val binding = githubMcpServer.githubGetBoundRepo(projectId).getOrNull()
            _githubUiState.value = _githubUiState.value.copy(binding = binding)
        }
    }

    fun connectSupportMcp() {
        viewModelScope.launch {
            _supportUiState.value = _supportUiState.value.copy(
                status = SupportMcpUiStatus.Connecting,
                error = "",
                message = ""
            )

            val project = projectRepository.getProject(projectId)
            if (project == null) {
                _supportUiState.value = _supportUiState.value.copy(
                    status = SupportMcpUiStatus.Error,
                    error = "Проект не найден"
                )
                return@launch
            }

            val projectPath = project.rootPath.trim().ifBlank { "assets://support" }

            mcpRepository.upsertConnection(
                McpConnection(
                    id = "support-mcp-$projectId",
                    projectId = projectId,
                    name = "Support MCP",
                    serverUrl = "mcp://internal/support",
                    projectPath = projectPath,
                    connectionType = McpConnectionType.SUPPORT
                )
            )

            loadSupportSnapshotInternal(connected = true)
        }
    }


    /*Короткий рабочий чеклист в UI:

    challenge_day_31

    Открой проект в приложении и выбери нужный Project.
    На экране MCP проверь, что GitHub repo уже привязан (если нет, сначала привяжи).
    Перейди в Chat этого проекта.
    Введи:
    /file_task найди где используется SupportMcpServer
    Должен прийти ответ с найденными местами (path:line:snippet), без создания PR.
    Проверка write-сценария:

    В том же чате введи:
    /file_task обнови README по последним изменениям в support assistant
    Должен прийти ответ с:
    Файл обновлён: README.md
    Diff summary: ...
    PR: https://github.com/.../pull/... (если PR создался)
    Что смотреть в GitHub:

    Открой PR ссылку из ответа.
    Проверь:
    новая ветка вида ai/fileops-...
    изменён README.md
    commit message docs(fileops): update README.md (или похожий по сценарию).
    Если хочешь, следующим шагом можем пройти это вместе на твоём конкретном проекте: ты присылаешь, на каком шаге остановился, и я даю точную проверку/диагностику.
*/


    fun loadSupportSnapshot() {
        viewModelScope.launch {
            loadSupportSnapshotInternal(connected = _supportUiState.value.isConnected)
        }
    }
    private suspend fun loadSupportSnapshotInternal(connected: Boolean) {
        _supportUiState.value = _supportUiState.value.copy(
            status = SupportMcpUiStatus.LoadingSnapshot,
            error = "",
            message = ""
        )

        val users = supportMcpServer.supportListUsers(projectId).getOrElse { throwable ->
            _supportUiState.value = _supportUiState.value.copy(
                status = SupportMcpUiStatus.Error,
                isConnected = connected,
                error = throwable.message ?: "Не удалось загрузить users"
            )
            return
        }
        val tickets = supportMcpServer.supportListTickets(projectId).getOrElse { throwable ->
            _supportUiState.value = _supportUiState.value.copy(
                status = SupportMcpUiStatus.Error,
                isConnected = connected,
                error = throwable.message ?: "Не удалось загрузить tickets"
            )
            return
        }

        _supportUiState.value = _supportUiState.value.copy(
            status = SupportMcpUiStatus.Success,
            isConnected = connected,
            usersCount = users.size,
            ticketsCount = tickets.size,
            message = "Support MCP ready: users=${users.size}, tickets=${tickets.size}",
            error = ""
        )
    }
}
