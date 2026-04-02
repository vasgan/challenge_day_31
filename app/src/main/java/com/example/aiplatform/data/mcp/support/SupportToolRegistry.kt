package com.example.aiplatform.data.mcp.support

import com.example.aiplatform.data.support.SupportJsonDataSource
import com.example.aiplatform.domain.model.SupportContext
import com.example.aiplatform.domain.repository.ProjectRepository

class SupportToolRegistry(
    private val projectRepository: ProjectRepository,
    private val jsonDataSource: SupportJsonDataSource
) {
    suspend fun execute(call: SupportMcpToolCall): SupportMcpToolResult {
        return when (call.tool) {
            SupportMcpTools.LIST_USERS -> listUsers(call.arguments)
            SupportMcpTools.GET_USER -> getUser(call.arguments)
            SupportMcpTools.LIST_TICKETS -> listTickets(call.arguments)
            SupportMcpTools.GET_TICKET -> getTicket(call.arguments)
            SupportMcpTools.GET_USER_TICKETS -> getUserTickets(call.arguments)
            SupportMcpTools.BUILD_CONTEXT -> buildContext(call.arguments)
            else -> SupportMcpToolResult(success = false, error = "Unknown tool: ${call.tool}")
        }
    }

    private suspend fun listUsers(arguments: Map<String, String>): SupportMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) {
            return SupportMcpToolResult(success = false, error = "projectId is required")
        }
        val rootPath = resolveProjectRootPath(projectId)

        val users = jsonDataSource.loadUsers(rootPath).getOrElse { throwable ->
            return SupportMcpToolResult(success = false, error = throwable.message ?: "Failed to load users")
        }

        return SupportMcpToolResult(success = true, data = SupportMcpToolData.Users(users))
    }

    private suspend fun getUser(arguments: Map<String, String>): SupportMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        val userId = arguments["userId"].orEmpty().trim()
        if (projectId.isBlank() || userId.isBlank()) {
            return SupportMcpToolResult(success = false, error = "projectId and userId are required")
        }

        val usersResult = listUsers(mapOf("projectId" to projectId))
        if (!usersResult.success) return usersResult
        val users = (usersResult.data as? SupportMcpToolData.Users)?.users.orEmpty()

        val user = users.firstOrNull { it.id == userId }
            ?: return SupportMcpToolResult(success = false, error = "User not found: $userId")

        return SupportMcpToolResult(success = true, data = SupportMcpToolData.User(user))
    }

    private suspend fun listTickets(arguments: Map<String, String>): SupportMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) {
            return SupportMcpToolResult(success = false, error = "projectId is required")
        }
        val rootPath = resolveProjectRootPath(projectId)

        val tickets = jsonDataSource.loadTickets(rootPath).getOrElse { throwable ->
            return SupportMcpToolResult(success = false, error = throwable.message ?: "Failed to load tickets")
        }

        return SupportMcpToolResult(success = true, data = SupportMcpToolData.Tickets(tickets))
    }

    private suspend fun getTicket(arguments: Map<String, String>): SupportMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        val ticketId = arguments["ticketId"].orEmpty().trim()
        if (projectId.isBlank() || ticketId.isBlank()) {
            return SupportMcpToolResult(success = false, error = "projectId and ticketId are required")
        }

        val ticketsResult = listTickets(mapOf("projectId" to projectId))
        if (!ticketsResult.success) return ticketsResult
        val tickets = (ticketsResult.data as? SupportMcpToolData.Tickets)?.tickets.orEmpty()

        val ticket = tickets.firstOrNull { it.id == ticketId }
            ?: return SupportMcpToolResult(success = false, error = "Ticket not found: $ticketId")

        return SupportMcpToolResult(success = true, data = SupportMcpToolData.Ticket(ticket))
    }

    private suspend fun getUserTickets(arguments: Map<String, String>): SupportMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        val userId = arguments["userId"].orEmpty().trim()
        if (projectId.isBlank() || userId.isBlank()) {
            return SupportMcpToolResult(success = false, error = "projectId and userId are required")
        }

        val ticketsResult = listTickets(mapOf("projectId" to projectId))
        if (!ticketsResult.success) return ticketsResult
        val tickets = (ticketsResult.data as? SupportMcpToolData.Tickets)?.tickets.orEmpty()
            .filter { it.userId == userId }

        return SupportMcpToolResult(success = true, data = SupportMcpToolData.UserTickets(userId = userId, tickets = tickets))
    }

    private suspend fun buildContext(arguments: Map<String, String>): SupportMcpToolResult {
        val projectId = arguments["projectId"].orEmpty().trim()
        if (projectId.isBlank()) {
            return SupportMcpToolResult(success = false, error = "projectId is required")
        }

        val userId = arguments["userId"].orEmpty().trim().ifBlank { null }
        val ticketId = arguments["ticketId"].orEmpty().trim().ifBlank { null }

        val users = (listUsers(mapOf("projectId" to projectId)).data as? SupportMcpToolData.Users)?.users.orEmpty()
        val tickets = (listTickets(mapOf("projectId" to projectId)).data as? SupportMcpToolData.Tickets)?.tickets.orEmpty()

        val ticket = ticketId?.let { tid -> tickets.firstOrNull { it.id == tid } }
        val resolvedUserId = userId ?: ticket?.userId
        val user = resolvedUserId?.let { uid -> users.firstOrNull { it.id == uid } }
        val related = resolvedUserId?.let { uid -> tickets.filter { it.userId == uid }.take(10) }.orEmpty()

        return SupportMcpToolResult(
            success = true,
            data = SupportMcpToolData.ContextPayload(
                context = SupportContext(
                    user = user,
                    ticket = ticket,
                    relatedTickets = related
                )
            )
        )
    }

    private suspend fun resolveProjectRootPath(projectId: String): String? {
        val project = projectRepository.getProject(projectId) ?: return null
        return project.rootPath.trim().ifBlank { null }
    }
}
