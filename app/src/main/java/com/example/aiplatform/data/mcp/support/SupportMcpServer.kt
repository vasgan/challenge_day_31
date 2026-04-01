package com.example.aiplatform.data.mcp.support

import com.example.aiplatform.domain.model.SupportContext
import com.example.aiplatform.domain.model.SupportTicket
import com.example.aiplatform.domain.model.SupportUser

class SupportMcpServer(
    private val executor: SupportMcpToolExecutor
) {
    suspend fun supportListUsers(projectId: String): Result<List<SupportUser>> {
        val result = executeTool(SupportMcpTools.LIST_USERS, mapOf("projectId" to projectId))
        val payload = result.data as? SupportMcpToolData.Users
            ?: return Result.failure(IllegalStateException(result.error ?: "Users payload missing"))
        return Result.success(payload.users)
    }

    suspend fun supportGetUser(projectId: String, userId: String): Result<SupportUser> {
        val result = executeTool(
            SupportMcpTools.GET_USER,
            mapOf("projectId" to projectId, "userId" to userId)
        )
        val payload = result.data as? SupportMcpToolData.User
            ?: return Result.failure(IllegalStateException(result.error ?: "User payload missing"))
        return Result.success(payload.user)
    }

    suspend fun supportListTickets(projectId: String): Result<List<SupportTicket>> {
        val result = executeTool(SupportMcpTools.LIST_TICKETS, mapOf("projectId" to projectId))
        val payload = result.data as? SupportMcpToolData.Tickets
            ?: return Result.failure(IllegalStateException(result.error ?: "Tickets payload missing"))
        return Result.success(payload.tickets)
    }

    suspend fun supportGetTicket(projectId: String, ticketId: String): Result<SupportTicket> {
        val result = executeTool(
            SupportMcpTools.GET_TICKET,
            mapOf("projectId" to projectId, "ticketId" to ticketId)
        )
        val payload = result.data as? SupportMcpToolData.Ticket
            ?: return Result.failure(IllegalStateException(result.error ?: "Ticket payload missing"))
        return Result.success(payload.ticket)
    }

    suspend fun supportGetUserTickets(projectId: String, userId: String): Result<List<SupportTicket>> {
        val result = executeTool(
            SupportMcpTools.GET_USER_TICKETS,
            mapOf("projectId" to projectId, "userId" to userId)
        )
        val payload = result.data as? SupportMcpToolData.UserTickets
            ?: return Result.failure(IllegalStateException(result.error ?: "User tickets payload missing"))
        return Result.success(payload.tickets)
    }

    suspend fun supportBuildContext(projectId: String, userId: String?, ticketId: String?): Result<SupportContext> {
        val args = mutableMapOf("projectId" to projectId)
        userId?.trim()?.takeIf { it.isNotBlank() }?.let { args["userId"] = it }
        ticketId?.trim()?.takeIf { it.isNotBlank() }?.let { args["ticketId"] = it }

        val result = executeTool(SupportMcpTools.BUILD_CONTEXT, args)
        val payload = result.data as? SupportMcpToolData.ContextPayload
            ?: return Result.failure(IllegalStateException(result.error ?: "Support context payload missing"))
        return Result.success(payload.context)
    }

    private suspend fun executeTool(tool: String, arguments: Map<String, String>): SupportMcpToolResult {
        val result = executor.execute(SupportMcpToolCall(tool = tool, arguments = arguments))
        return if (result.success) result else SupportMcpToolResult(success = false, error = result.error ?: "Tool failed")
    }
}
