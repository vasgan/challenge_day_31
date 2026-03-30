package com.example.aiplatform.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.example.aiplatform.ui.viewmodel.McpViewModel

@Composable
fun McpScreen(viewModel: McpViewModel) {
    val connections by viewModel.connections.collectAsState()
    var url by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MCP", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") }
        )
        Button(onClick = {
            if (url.isNotBlank()) {
                viewModel.addConnection(url.trim())
                url = ""
            }
        }) {
            Text("Add MCP connection")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(connections) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.serverUrl)
                    }
                }
            }
        }
    }
}
