package com.example.aiplatform.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.aiplatform.AssistantApplication
import com.example.aiplatform.ui.screen.ChatListScreen
import com.example.aiplatform.ui.screen.ChatScreen
import com.example.aiplatform.ui.screen.McpScreen
import com.example.aiplatform.ui.screen.MemoryScreen
import com.example.aiplatform.ui.screen.ProjectScreen
import com.example.aiplatform.ui.screen.ProjectsScreen
import com.example.aiplatform.ui.screen.RagScreen
import com.example.aiplatform.ui.screen.SettingsScreen
import com.example.aiplatform.ui.viewmodel.AppViewModelFactory
import com.example.aiplatform.ui.viewmodel.ChatListViewModel
import com.example.aiplatform.ui.viewmodel.ChatViewModel
import com.example.aiplatform.ui.viewmodel.McpViewModel
import com.example.aiplatform.ui.viewmodel.MemoryViewModel
import com.example.aiplatform.ui.viewmodel.ProjectsViewModel
import com.example.aiplatform.ui.viewmodel.RagViewModel
import com.example.aiplatform.ui.viewmodel.SettingsViewModel

private object Route {
    const val Projects = "projects"
    const val Project = "project/{projectId}"
    const val Chats = "project/{projectId}/chats"
    const val Chat = "project/{projectId}/chat/{chatId}"
    const val Settings = "project/{projectId}/settings"
    const val Mcp = "project/{projectId}/mcp"
    const val Rag = "project/{projectId}/rag"
    const val Memory = "project/{projectId}/memory"
}

private fun app(contextApplication: Application): AssistantApplication = contextApplication as AssistantApplication

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val application = LocalContext.current.applicationContext as Application
    val container = app(application).container

    NavHost(navController = navController, startDestination = Route.Projects) {
        composable(Route.Projects) {
            val vm: ProjectsViewModel = viewModel(factory = AppViewModelFactory {
                ProjectsViewModel(container.projectRepository, container.chatRepository)
            })
            ProjectsScreen(
                viewModel = vm,
                openProject = { projectId -> navController.navigate("project/$projectId") }
            )
        }

        composable(
            route = Route.Project,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            ProjectScreen(
                projectId = projectId,
                openChats = { navController.navigate("project/$projectId/chats") },
                openSettings = { navController.navigate("project/$projectId/settings") },
                openMcp = { navController.navigate("project/$projectId/mcp") },
                openRag = { navController.navigate("project/$projectId/rag") },
                openMemory = { navController.navigate("project/$projectId/memory") }
            )
        }

        composable(
            route = Route.Chats,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val vm: ChatListViewModel = viewModel(factory = AppViewModelFactory {
                ChatListViewModel(projectId = projectId, chatRepository = container.chatRepository)
            })
            ChatListScreen(
                projectId = projectId,
                viewModel = vm,
                openChat = { chatId -> navController.navigate("project/$projectId/chat/$chatId") }
            )
        }

        composable(
            route = Route.Chat,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("chatId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val vm: ChatViewModel = viewModel(factory = AppViewModelFactory {
                ChatViewModel(
                    projectId = projectId,
                    chatId = chatId,
                    chatRepository = container.chatRepository,
                    orchestrator = container.orchestrator
                )
            })
            ChatScreen(viewModel = vm)
        }

        composable(
            route = Route.Settings,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory {
                SettingsViewModel(projectId = projectId, projectRepository = container.projectRepository)
            })
            SettingsScreen(viewModel = vm)
        }

        composable(
            route = Route.Mcp,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val vm: McpViewModel = viewModel(factory = AppViewModelFactory {
                McpViewModel(
                    projectId = projectId,
                    mcpRepository = container.mcpRepository,
                    projectRepository = container.projectRepository,
                    githubMcpServer = container.githubMcpServer,
                    supportMcpServer = container.supportMcpServer
                )
            })
            McpScreen(viewModel = vm)
        }

        composable(
            route = Route.Rag,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val vm: RagViewModel = viewModel(factory = AppViewModelFactory {
                RagViewModel(
                    projectId = projectId,
                    ragRepository = container.ragRepository,
                    projectRepository = container.projectRepository,
                    supportRagBootstrapper = container.supportRagBootstrapper
                )
            })
            RagScreen(viewModel = vm)
        }

        composable(
            route = Route.Memory,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val vm: MemoryViewModel = viewModel(factory = AppViewModelFactory {
                MemoryViewModel(projectId = projectId, memoryRepository = container.memoryRepository)
            })
            MemoryScreen(viewModel = vm)
        }
    }
}
