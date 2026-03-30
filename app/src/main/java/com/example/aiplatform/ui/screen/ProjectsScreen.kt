package com.example.aiplatform.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.aiplatform.ui.viewmodel.ProjectsViewModel

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    openProject: (String) -> Unit
) {
    val projects by viewModel.projects.collectAsState()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("Projects", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") }
            )
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") }
            )
        }
        item {
            Button(onClick = {
                if (title.isNotBlank()) {
                    viewModel.createProject(title.trim(), description.trim())
                    title = ""
                    description = ""
                }
            }) {
                Text("Create project")
            }
        }

        items(projects) { project ->
            Card(modifier = Modifier.fillMaxWidth().clickable { openProject(project.id) }) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(project.title, style = MaterialTheme.typography.titleMedium)
                    Text(project.description)
                    Text("Model: ${project.selectedModel.apiName}")
                }
            }
        }
    }
}
