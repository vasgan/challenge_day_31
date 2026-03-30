package com.example.aiplatform.ui.screen

import androidx.compose.foundation.clickable
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
import com.example.aiplatform.ui.viewmodel.ChatListViewModel

@Composable
fun ChatListScreen(
    projectId: String,
    viewModel: ChatListViewModel,
    openChat: (String) -> Unit
) {
    val chats by viewModel.chats.collectAsState()
    var chatTitle by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Chats", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = chatTitle,
            onValueChange = { chatTitle = it },
            label = { Text("New chat title") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            if (chatTitle.isNotBlank()) {
                viewModel.createChat(projectId, chatTitle.trim())
                chatTitle = ""
            }
        }) {
            Text("Create chat")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chats) { chat ->
                Card(modifier = Modifier.fillMaxWidth().clickable { openChat(chat.id) }) {
                    Text(chat.title, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
