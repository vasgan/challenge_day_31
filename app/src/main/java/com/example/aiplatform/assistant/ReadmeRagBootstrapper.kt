package com.example.aiplatform.assistant

import com.example.aiplatform.domain.model.Project
import com.example.aiplatform.domain.model.RagDocumentChunk
import com.example.aiplatform.domain.model.RagIndex
import com.example.aiplatform.domain.repository.RagRepository
import java.io.File

sealed class ReadmeBootstrapState {
    data object Indexed : ReadmeBootstrapState()
    data object AlreadyIndexed : ReadmeBootstrapState()
    data class MissingProjectPath(val projectId: String) : ReadmeBootstrapState()
    data class MissingReadme(val expectedPath: String) : ReadmeBootstrapState()
}

class ReadmeRagBootstrapper(
    private val ragRepository: RagRepository
) {
    private val readmeFileNames = setOf("README.md", "Readme.md", "readme.md")

    suspend fun ensureIndexed(project: Project): ReadmeBootstrapState {
        if (project.rootPath.isBlank()) {
            return ReadmeBootstrapState.MissingProjectPath(project.id)
        }

        val rootDir = File(project.rootPath)
        val readme = findReadmeFile(rootDir)
        if (readme == null) {
            val expected = readmeFileNames.joinToString(", ") { File(rootDir, it).absolutePath }
            return ReadmeBootstrapState.MissingReadme(expected)
        }

        val indexId = readmeIndexId(project.id)
        val alreadyIndexed = ragRepository.listActiveIndexes(project.id).any { it.id == indexId }
        if (alreadyIndexed) {
            return ReadmeBootstrapState.AlreadyIndexed
        }

        val docs = loadDocumentationFiles(rootDir)
        val chunks = docs.flatMap { (source, content) -> chunkDocument(content, source) }
        val index = RagIndex(
            id = indexId,
            projectId = project.id,
            title = "Project docs (README)",
            isActive = true
        )

        ragRepository.upsertIndex(index)
        ragRepository.addDocuments(index, chunks)

        return ReadmeBootstrapState.Indexed
    }

    private fun loadDocumentationFiles(rootDir: File): List<Pair<String, String>> {
        val files = mutableListOf<File>()
        findReadmeFile(rootDir)?.let { files += it }

        val docsDir = File(rootDir, "docs")
        if (docsDir.exists() && docsDir.isDirectory) {
            docsDir.walkTopDown()
                .filter { it.isFile && (it.extension == "md" || it.extension == "txt") }
                .forEach { files += it }
        }

        return files.distinctBy { it.absolutePath }.map { file ->
            val source = file.absolutePath.removePrefix(rootDir.absolutePath).trimStart(File.separatorChar)
            source to file.readText()
        }
    }

    private fun findReadmeFile(rootDir: File): File? {
        if (!rootDir.exists() || !rootDir.isDirectory) return null
        return rootDir.listFiles()
            ?.firstOrNull { file -> file.isFile && file.name.lowercase() == "readme.md" }
    }

    private fun chunkDocument(content: String, source: String): List<RagDocumentChunk> {
        val paragraphs = content.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) return emptyList()

        val chunks = mutableListOf<RagDocumentChunk>()
        var current = StringBuilder()
        var section = 1

        paragraphs.forEach { paragraph ->
            if (current.length + paragraph.length > 900 && current.isNotBlank()) {
                chunks += RagDocumentChunk(
                    content = current.toString().trim(),
                    source = source,
                    section = "part-$section"
                )
                section += 1
                current = StringBuilder()
            }
            current.appendLine(paragraph)
        }

        if (current.isNotBlank()) {
            chunks += RagDocumentChunk(
                content = current.toString().trim(),
                source = source,
                section = "part-$section"
            )
        }

        return chunks
    }

    fun readmeIndexId(projectId: String): String = "readme-index-$projectId"
}
