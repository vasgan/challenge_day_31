package com.example.aiplatform.assistant

import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class SupportRagBootstrapper(
    private val projectRepository: ProjectRepository,
    private val ragRepository: RagRepository,
    private val assetTextProvider: (String) -> String? = { null }
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importFaq(projectId: String): Result<Pair<String, Int>> = runCatching {
        val project = projectRepository.getProject(projectId)
            ?: error("Project not found")
        val root = project.rootPath.trim().ifBlank { null }
        val rawFaq = resolveFileText(root, "faq.json")

        val chunks = parseFaqChunks(rawFaq)
        if (chunks.isEmpty()) error("faq.json has no valid entries")

        val indexId = "support-faq-index-$projectId"
        val index = RagIndex(
            id = indexId,
            projectId = projectId,
            title = "Support FAQ",
            isActive = true
        )
        ragRepository.upsertIndex(index)
        ragRepository.addDocuments(index, chunks)

        indexId to chunks.size
    }

    suspend fun importSupportDocs(projectId: String): Result<Pair<String, Int>> = runCatching {
        val project = projectRepository.getProject(projectId)
            ?: error("Project not found")
        val root = project.rootPath.trim().ifBlank { null }
        val rawDocs = resolveFileText(root, "support_docs.md")

        val chunks = chunkMarkdown(rawDocs)
        if (chunks.isEmpty()) error("support_docs.md is empty")

        val indexId = "support-docs-index-$projectId"
        val index = RagIndex(
            id = indexId,
            projectId = projectId,
            title = "Support Docs",
            isActive = true
        )
        ragRepository.upsertIndex(index)
        ragRepository.addDocuments(index, chunks)

        indexId to chunks.size
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

    private fun parseFaqChunks(rawJson: String): List<RagDocumentChunk> {
        val root = json.parseToJsonElement(rawJson)
        val objects = extractObjects(root)

        return objects.mapIndexedNotNull { index, obj ->
            val q = obj.string("question") ?: obj.string("q") ?: obj.string("title")
            val a = obj.string("answer") ?: obj.string("a") ?: obj.string("response") ?: obj.string("content")
            if (q.isNullOrBlank() && a.isNullOrBlank()) {
                null
            } else {
                RagDocumentChunk(
                    content = buildString {
                        appendLine("Question: ${q ?: "(missing)"}")
                        appendLine("Answer: ${a ?: "(missing)"}")
                    }.trim(),
                    source = "support_faq|faq.json",
                    section = "item-${index + 1}"
                )
            }
        }
    }

    private fun extractObjects(root: JsonElement): List<JsonObject> {
        return when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                val nested = listOf("faq", "items", "questions", "data")
                    .mapNotNull { key -> root[key] as? JsonArray }
                    .flatMap { arr -> arr.mapNotNull { it as? JsonObject } }
                if (nested.isNotEmpty()) nested else listOf(root)
            }
            else -> emptyList()
        }
    }

    private fun JsonObject.string(key: String): String? {
        return (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun chunkMarkdown(text: String): List<RagDocumentChunk> {
        val paragraphs = text.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) return emptyList()

        val chunks = mutableListOf<RagDocumentChunk>()
        var part = 1
        var buffer = StringBuilder()

        paragraphs.forEach { paragraph ->
            if (buffer.length + paragraph.length > 900 && buffer.isNotBlank()) {
                chunks += RagDocumentChunk(
                    content = buffer.toString().trim(),
                    source = "support_docs|support_docs.md",
                    section = "part-$part"
                )
                part += 1
                buffer = StringBuilder()
            }
            buffer.appendLine(paragraph)
        }

        if (buffer.isNotBlank()) {
            chunks += RagDocumentChunk(
                content = buffer.toString().trim(),
                source = "support_docs|support_docs.md",
                section = "part-$part"
            )
        }

        return chunks
    }
}
