package com.example.aiplatform.assistant

import com.example.aiplatform.domain.model.FileSearchHit
import com.example.aiplatform.domain.model.Message

class FileOpsPromptBuilder {
    fun systemPrompt(): String {
        return "You are a software assistant that works with repository files. " +
            "Use only provided context. Be explicit and concise. " +
            "When asked to update docs, produce full file content without markdown fences."
    }

    fun findUsageContext(
        projectTitle: String,
        goal: String,
        hits: List<FileSearchHit>,
        recentChat: List<Message>
    ): String {
        val hitBlock = if (hits.isEmpty()) {
            "No matches found."
        } else {
            hits.joinToString("\n") { hit ->
                "- ${hit.path}:${hit.line} -> ${hit.snippet}"
            }
        }

        val chatBlock = if (recentChat.isEmpty()) {
            "No chat history."
        } else {
            recentChat.joinToString("\n") { "${it.role}: ${it.content}" }
        }

        return buildString {
            appendLine("Project: $projectTitle")
            appendLine("Goal: $goal")
            appendLine()
            appendLine("Matches:")
            appendLine(hitBlock)
            appendLine()
            appendLine("Recent chat:")
            appendLine(chatBlock)
            appendLine()
            appendLine("Return:")
            appendLine("1) Findings")
            appendLine("2) Affected files")
            appendLine("3) Recommended next changes")
        }
    }

    fun writeContext(
        projectTitle: String,
        goal: String,
        targetPath: String,
        oldContent: String,
        evidence: List<FileSearchHit>,
        recentChat: List<Message>
    ): String {
        val evidenceBlock = if (evidence.isEmpty()) {
            "No evidence found."
        } else {
            evidence.joinToString("\n") { hit ->
                "- ${hit.path}:${hit.line} -> ${hit.snippet}"
            }
        }
        val chatBlock = if (recentChat.isEmpty()) {
            "No chat history."
        } else {
            recentChat.joinToString("\n") { "${it.role}: ${it.content}" }
        }

        return buildString {
            appendLine("Project: $projectTitle")
            appendLine("Goal: $goal")
            appendLine("Target file: $targetPath")
            appendLine()
            appendLine("Evidence:")
            appendLine(evidenceBlock)
            appendLine()
            appendLine("Current file content:")
            appendLine(oldContent.ifBlank { "(file missing or empty)" })
            appendLine()
            appendLine("Recent chat:")
            appendLine(chatBlock)
            appendLine()
            appendLine("Task: produce final full content for target file only.")
            appendLine("Output raw file content without markdown code fences.")
        }
    }

    fun diffSummary(oldContent: String, newContent: String): String {
        val oldLines = oldContent.lineSequence().count()
        val newLines = newContent.lineSequence().count()
        val delta = newLines - oldLines
        return "lines old=$oldLines, new=$newLines, delta=$delta"
    }
}
