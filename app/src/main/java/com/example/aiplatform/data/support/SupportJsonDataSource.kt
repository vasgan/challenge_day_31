package com.example.aiplatform.data.support

import com.example.aiplatform.domain.model.SupportTicket
import com.example.aiplatform.domain.model.SupportUser
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SupportJsonDataSource {
    constructor() : this(assetTextProvider = { null })
    constructor(assetTextProvider: (String) -> String?) {
        this.assetTextProvider = assetTextProvider
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val assetTextProvider: (String) -> String?

    fun loadUsers(rootPath: String?): Result<List<SupportUser>> = runCatching {
        val text = resolveFileText(rootPath, "user.json").trim()
        if (text.isBlank()) return@runCatching emptyList()
        val root = json.parseToJsonElement(text)
        extractObjects(root).mapNotNull { parseUser(it) }
    }

    fun loadTickets(rootPath: String?): Result<List<SupportTicket>> = runCatching {
        val text = resolveFileText(rootPath, "ticket.json").trim()
        if (text.isBlank()) return@runCatching emptyList()
        val root = json.parseToJsonElement(text)
        extractObjects(root).mapNotNull { parseTicket(it) }
    }

    private fun resolveFileText(rootPath: String?, fileName: String): String {
        if (!rootPath.isNullOrBlank()) {
            val file = File(rootPath, fileName)
            if (file.exists()) {
                return file.readText()
            }
        }

        val assetText = assetTextProvider(fileName)
        if (!assetText.isNullOrBlank()) {
            return assetText
        }

        if (!rootPath.isNullOrBlank()) {
            error("$fileName not found: ${File(rootPath, fileName).absolutePath} and bundled asset support/$fileName is missing")
        } else {
            error("$fileName not found. Configure project rootPath or add bundled asset support/$fileName")
        }
    }

    private fun extractObjects(root: JsonElement): List<JsonObject> {
        return when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                val direct = listOf(root)
                val nested = listOf("users", "data", "items", "tickets")
                    .mapNotNull { key -> root[key] as? JsonArray }
                    .flatMap { array -> array.mapNotNull { it as? JsonObject } }
                if (nested.isNotEmpty()) nested else direct
            }
            else -> emptyList()
        }
    }

    private fun parseUser(obj: JsonObject): SupportUser? {
        val id = obj.string("id")
            ?: obj.string("userId")
            ?: obj.string("uid")
            ?: return null

        val name = obj.string("name")
            ?: obj.string("fullName")
            ?: obj.string("username")
            ?: "User $id"

        val email = obj.string("email")
        val segment = obj.string("segment") ?: obj.string("plan") ?: obj.string("tier")

        val metadata = obj
            .filterKeys { it !in setOf("id", "userId", "uid", "name", "fullName", "username", "email", "segment", "plan", "tier") }
            .mapNotNull { (k, v) -> primitiveToString(v)?.let { k to it } }
            .toMap()

        return SupportUser(
            id = id,
            name = name,
            email = email,
            segment = segment,
            metadata = metadata
        )
    }

    private fun parseTicket(obj: JsonObject): SupportTicket? {
        val id = obj.string("id")
            ?: obj.string("ticketId")
            ?: obj.string("tid")
            ?: return null

        val subject = obj.string("subject")
            ?: obj.string("title")
            ?: obj.string("summary")
            ?: "Ticket $id"

        val status = obj.string("status") ?: obj.string("state") ?: "unknown"
        val userId = obj.string("userId") ?: obj.string("user_id") ?: obj.string("customerId")
        val priority = obj.string("priority")
        val description = obj.string("description") ?: obj.string("body") ?: obj.string("details")

        val metadata = obj
            .filterKeys {
                it !in setOf(
                    "id", "ticketId", "tid", "subject", "title", "summary", "status", "state",
                    "userId", "user_id", "customerId", "priority", "description", "body", "details"
                )
            }
            .mapNotNull { (k, v) -> primitiveToString(v)?.let { k to it } }
            .toMap()

        return SupportTicket(
            id = id,
            userId = userId,
            subject = subject,
            status = status,
            priority = priority,
            description = description,
            metadata = metadata
        )
    }

    private fun JsonObject.string(key: String): String? {
        return (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun primitiveToString(element: JsonElement): String? {
        val primitive = element as? JsonPrimitive ?: return null
        primitive.contentOrNull?.let { return it }
        primitive.intOrNull?.let { return it.toString() }
        primitive.doubleOrNull?.let { return it.toString() }
        primitive.booleanOrNull?.let { return it.toString() }
        return null
    }
}
