package com.example.aiplatform.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProjectScreen(
    projectId: String,
    openChats: () -> Unit,
    openSettings: () -> Unit,
    openMcp: () -> Unit,
    openRag: () -> Unit,
    openMemory: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Project", style = MaterialTheme.typography.headlineMedium)
        Text("id: $projectId")

        Button(onClick = openChats, modifier = Modifier.fillMaxWidth()) { Text("Chats") }
        Button(onClick = openSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings (model)") }
        Button(onClick = openMcp, modifier = Modifier.fillMaxWidth()) { Text("MCP connections / Добавить GitHub MCP") }
        Button(onClick = openRag, modifier = Modifier.fillMaxWidth()) { Text("RAG indexes") }
        Button(onClick = openMemory, modifier = Modifier.fillMaxWidth()) { Text("Memory") }
    }
}
