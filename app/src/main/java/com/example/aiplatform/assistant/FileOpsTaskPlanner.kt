package com.example.aiplatform.assistant

import com.example.aiplatform.domain.model.FileOpsTaskType

class FileOpsTaskPlanner {

    fun classify(goal: String): FileOpsTaskType {
        val normalized = goal.lowercase().trim()
        return when {
            normalized.contains("найд") || normalized.contains("where") ||
                normalized.contains("использ") || normalized.contains("usage") -> FileOpsTaskType.FIND_USAGE

            normalized.contains("readme") || normalized.contains("changelog") ||
                normalized.contains("док") || normalized.contains("обнов") -> FileOpsTaskType.UPDATE_DOCS

            normalized.contains("adr") || normalized.contains("сгенер") ||
                normalized.contains("создай") || normalized.contains("generate") -> FileOpsTaskType.GENERATE_FILE

            else -> FileOpsTaskType.INVARIANT_CHECK
        }
    }

    fun extractSearchQuery(goal: String): String {
        val cleaned = goal
            .replace("/file_task", "", ignoreCase = true)
            .replace("найди", "", ignoreCase = true)
            .replace("где используется", "", ignoreCase = true)
            .replace("where is", "", ignoreCase = true)
            .trim()
        return cleaned.ifBlank { "SupportMcpServer" }
    }

    fun targetPath(goal: String): String {
        val normalized = goal.lowercase()
        return when {
            normalized.contains("changelog") -> "CHANGELOG.md"
            normalized.contains("adr") -> "docs/adr/ADR-fileops-assistant.md"
            else -> "README.md"
        }
    }
}
