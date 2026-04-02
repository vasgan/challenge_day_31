package com.example.aiplatform.data.mcp.support

import com.example.aiplatform.domain.model.SupportContext
import com.example.aiplatform.domain.model.SupportTicket
import com.example.aiplatform.domain.model.SupportUser

data class SupportMcpToolCall(
    val tool: String,
    val arguments: Map<String, String>
)

sealed interface SupportMcpToolData {
    data class Users(val users: List<SupportUser>) : SupportMcpToolData
    data class User(val user: SupportUser) : SupportMcpToolData
    data class Tickets(val tickets: List<SupportTicket>) : SupportMcpToolData
    data class Ticket(val ticket: SupportTicket) : SupportMcpToolData
    data class UserTickets(val userId: String, val tickets: List<SupportTicket>) : SupportMcpToolData
    data class ContextPayload(val context: SupportContext) : SupportMcpToolData
}

data class SupportMcpToolResult(
    val success: Boolean,
    val data: SupportMcpToolData? = null,
    val error: String? = null
)

interface SupportMcpToolExecutor {
    suspend fun execute(call: SupportMcpToolCall): SupportMcpToolResult
}

object SupportMcpTools {
    const val LIST_USERS = "support_list_users"
    const val GET_USER = "support_get_user"
    const val LIST_TICKETS = "support_list_tickets"
    const val GET_TICKET = "support_get_ticket"
    const val GET_USER_TICKETS = "support_get_user_tickets"
    const val BUILD_CONTEXT = "support_build_context"
}
