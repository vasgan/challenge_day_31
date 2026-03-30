package com.example.aiplatform.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aiplatform.ui.viewmodel.MemoryViewModel

@Composable
fun MemoryScreen(viewModel: MemoryViewModel) {
    val memory by viewModel.memory.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Project memory", style = MaterialTheme.typography.headlineMedium)
        Text(memory?.summary ?: "No summary yet")
        Button(onClick = { viewModel.refresh() }) {
            Text("Refresh")
        }
    }
}
