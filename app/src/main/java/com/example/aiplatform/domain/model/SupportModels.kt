package com.example.aiplatform.domain.model

data class SupportUser(
    val id: String,
    val name: String,
    val email: String?,
    val segment: String?,
    val metadata: Map<String, String>
)

data class SupportTicket(
    val id: String,
    val userId: String?,
    val subject: String,
    val status: String,
    val priority: String?,
    val description: String?,
    val metadata: Map<String, String>
)

data class SupportContext(
    val user: SupportUser?,
    val ticket: SupportTicket?,
    val relatedTickets: List<SupportTicket>
)
