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
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.ui.viewmodel.RagViewModel

@Composable
fun RagScreen(viewModel: RagViewModel) {
    val indexes by viewModel.indexes.collectAsState()
    var title by remember { mutableStateOf("") }
    var doc by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("RAG", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("New index title") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            if (title.isNotBlank()) {
                viewModel.addIndex(title.trim())
                title = ""
            }
        }) {
            Text("Create index")
        }

        OutlinedTextField(
            value = doc,
            onValueChange = { doc = it },
            label = { Text("Document text (split by blank lines)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val active: RagIndex? = indexes.firstOrNull { it.isActive }
            if (active != null && doc.isNotBlank()) {
                viewModel.addDocument(active, doc)
                doc = ""
            }
        }) {
            Text("Embed to active index")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(indexes) { index ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(index.title)
                        Text("active=${index.isActive}")
                    }
                }
            }
        }
    }
}
