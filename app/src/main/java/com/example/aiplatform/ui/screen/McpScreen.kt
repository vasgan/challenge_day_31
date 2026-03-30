package com.example.aiplatform.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aiplatform.domain.model.GithubRepo
import com.example.aiplatform.ui.viewmodel.McpViewModel

@Composable
fun McpScreen(viewModel: McpViewModel) {
    val connections by viewModel.connections.collectAsState()
    val githubState by viewModel.githubUiState.collectAsState()
    var genericUrl by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("MCP", style = MaterialTheme.typography.headlineMedium)

        Text("Подключить GitHub repo", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = githubState.ownerInput,
            onValueChange = { viewModel.updateOwner(it) },
            label = { Text("GitHub owner") }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.loadReposByOwner() }) {
                Text("Загрузить репозитории")
            }
            Button(onClick = { viewModel.refreshBinding() }) {
                Text("Обновить binding")
            }
        }

        Text("Status: ${githubState.status.name}")
        if (githubState.message.isNotBlank()) {
            Text("Info: ${githubState.message}")
        }
        if (githubState.error.isNotBlank()) {
            Text("Error: ${githubState.error}")
        }

        if (githubState.binding != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Текущий binding проекта")
                    Text("Repo: ${githubState.binding?.owner}/${githubState.binding?.repo}")
                    Text("Branch: ${githubState.binding?.defaultBranch}")
                    Text("URL: ${githubState.binding?.repoUrl}")
                    Text("RAG index: ${githubState.binding?.ragIndexId ?: "not built"}")
                    Text("README imported at: ${githubState.binding?.readmeImportedAt ?: 0L}")
                }
            }
        }

        if (githubState.repos.isNotEmpty()) {
            Text("Репозитории owner", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                githubState.repos.forEach { repo ->
                    GithubRepoRow(
                        repo = repo,
                        selected = githubState.selectedRepo?.fullName == repo.fullName,
                        onSelect = { viewModel.selectRepo(repo) }
                    )
                }
            }

            Button(
                onClick = { viewModel.bindSelectedRepo() },
                enabled = githubState.selectedRepo != null
            ) {
                Text("Привязать выбранный repo к проекту")
            }
        }

        Button(onClick = { viewModel.importReadmeAndBuildRag() }) {
            Text("Импортировать README / Построить RAG")
        }

        Text("Generic MCP", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = genericUrl,
            onValueChange = { genericUrl = it },
            label = { Text("Server URL") }
        )
        Button(onClick = {
            if (genericUrl.isNotBlank()) {
                viewModel.addConnection(genericUrl.trim())
                genericUrl = ""
            }
        }) {
            Text("Add MCP connection")
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            connections.forEach { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.name)
                        Text("type=${item.connectionType.name}")
                        Text("server=${item.serverUrl}")
                        if (item.projectPath.isNotBlank()) {
                            Text("path=${item.projectPath}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GithubRepoRow(
    repo: GithubRepo,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(repo.fullName)
            Text("defaultBranch=${repo.defaultBranch}")
            Text(repo.htmlUrl)
            Text(if (selected) "selected" else "tap to select")
        }
    }
}
