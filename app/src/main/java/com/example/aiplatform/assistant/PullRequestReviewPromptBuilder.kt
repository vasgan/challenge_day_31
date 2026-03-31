package com.example.aiplatform.assistant

import com.example.aiplatform.domain.model.GithubPullRequestDetails
import com.example.aiplatform.domain.model.GithubPullRequestFile
import com.example.aiplatform.domain.model.ProjectGithubBinding
import com.example.aiplatform.domain.model.RagChunk

class PullRequestReviewPromptBuilder {
    fun systemPrompt(): String =
        "You are a senior code reviewer. Use only provided diff, changed files, and retrieved project docs. " +
            "If evidence is insufficient, say so explicitly. " +
            "Return sections exactly: 1) Potential Bugs 2) Architecture Concerns 3) Recommendations " +
            "4) Overall Verdict 5) Optional Inline Suggestions (file|line|comment)."

    fun buildContext(
        projectTitle: String,
        binding: ProjectGithubBinding,
        details: GithubPullRequestDetails,
        files: List<GithubPullRequestFile>,
        diff: String,
        projectMemorySummary: String,
        ragChunks: List<RagChunk>
    ): String {
        val changedFiles = if (files.isEmpty()) {
            "No changed files found."
        } else {
            files.joinToString("\n") { file ->
                "- ${file.filename} [${file.status}] +${file.additions}/-${file.deletions}"
            }
        }
        val docsBlock = if (ragChunks.isEmpty()) {
            "No relevant documentation chunks were retrieved."
        } else {
            ragChunks.joinToString("\n\n") { chunk ->
                "[Source: ${chunk.source} | Section: ${chunk.section}]\n${chunk.content}"
            }
        }

        val memoryBlock = projectMemorySummary.ifBlank { "No project memory summary." }
        val diffBlock = diff.take(MAX_DIFF_CHARS).ifBlank { "No diff provided." }

        return buildString {
            appendLine("Project context:")
            appendLine("- Project title: $projectTitle")
            appendLine("- Repo: ${binding.owner}/${binding.repo}")
            appendLine("- Default branch: ${binding.defaultBranch}")
            appendLine()
            appendLine("Pull request context:")
            appendLine("- PR #${details.number}: ${details.title}")
            appendLine("- Author: ${details.author}")
            appendLine("- Base branch: ${details.baseBranch}")
            appendLine("- Head branch: ${details.headBranch}")
            appendLine("- URL: ${details.htmlUrl}")
            appendLine("- Body: ${details.body.ifBlank { "(empty)" }}")
            appendLine()
            appendLine("Changed files:")
            appendLine(changedFiles)
            appendLine()
            appendLine("Project memory:")
            appendLine(memoryBlock)
            appendLine()
            appendLine("Relevant documentation (RAG):")
            appendLine(docsBlock)
            appendLine()
            appendLine("Unified diff:")
            appendLine(diffBlock)
            if (diff.length > MAX_DIFF_CHARS) {
                appendLine()
                appendLine("[Diff truncated to $MAX_DIFF_CHARS characters]")
            }
        }
    }

    private companion object {
        const val MAX_DIFF_CHARS = 30_000
    }
}
