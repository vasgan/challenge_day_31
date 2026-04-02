package com.example.aiplatform.domain.model

enum class FileOpsTaskType {
    FIND_USAGE,
    UPDATE_DOCS,
    GENERATE_FILE,
    INVARIANT_CHECK
}

data class FileSearchHit(
    val path: String,
    val line: Int,
    val snippet: String
)

data class FileChangePlan(
    val path: String,
    val action: String,
    val reason: String
)

data class FilePatch(
    val path: String,
    val content: String,
    val message: String
)

data class PullRequestPlan(
    val title: String,
    val body: String,
    val head: String,
    val base: String
)
