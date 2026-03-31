package com.example.aiplatform.assistant

import com.example.aiplatform.data.mcp.github.GithubMcpServer
import com.example.aiplatform.domain.model.GithubPullRequestInlineComment
import com.example.aiplatform.domain.model.GithubPullRequestReviewRequest
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectGithubBindingRepository
import com.example.aiplatform.domain.repository.ProjectRepository
import com.example.aiplatform.domain.repository.RagRepository

class PullRequestReviewService(
    private val projectRepository: ProjectRepository,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val ragRepository: RagRepository,
    private val projectGithubBindingRepository: ProjectGithubBindingRepository,
    private val openAiRepository: OpenAiRepository,
    private val githubMcpServer: GithubMcpServer,
    private val promptBuilder: PullRequestReviewPromptBuilder
) : PullRequestReviewHandler {

    override suspend fun listOpenPrs(projectId: String): PullRequestListResult {
        val binding = projectGithubBindingRepository.getBinding(projectId)
            ?: return PullRequestListResult(
                answer = "GitHub repo не привязан к проекту. Сначала подключите repo на MCP экране.",
                success = false
            )

        val pullRequests = githubMcpServer.githubListOpenPullRequests(projectId).getOrElse { throwable ->
            return PullRequestListResult(
                answer = throwable.message ?: "Не удалось получить список PR.",
                success = false
            )
        }

        if (pullRequests.isEmpty()) {
            return PullRequestListResult(
                answer = "В репозитории ${binding.owner}/${binding.repo} нет открытых PR.",
                success = true
            )
        }

        val lines = pullRequests.joinToString("\n") { pr ->
            "PR #${pr.number} — ${pr.title} (${pr.updatedAt})"
        }

        return PullRequestListResult(
            answer = "Открытые PR для ${binding.owner}/${binding.repo}:\n$lines\n\nВведите /review_pr <number>",
            success = true
        )
    }

    override suspend fun reviewPr(projectId: String, chatId: String, prNumber: Int): PullRequestReviewExecutionResult {
        if (prNumber <= 0) {
            return PullRequestReviewExecutionResult(
                answer = "Некорректный номер PR. Используйте: /review_pr <number>",
                usedRag = false,
                usedMcp = false,
                postedToGithub = false
            )
        }

        val project = projectRepository.getProject(projectId)
            ?: return PullRequestReviewExecutionResult(
                answer = "Проект не найден.",
                usedRag = false,
                usedMcp = false,
                postedToGithub = false
            )

        val binding = projectGithubBindingRepository.getBinding(projectId)
            ?: return PullRequestReviewExecutionResult(
                answer = "GitHub repo не привязан к проекту. Сначала подключите repo на MCP экране.",
                usedRag = false,
                usedMcp = false,
                postedToGithub = false
            )

        val prDetails = githubMcpServer.githubGetPullRequestDetails(projectId, prNumber).getOrElse { throwable ->
            return PullRequestReviewExecutionResult(
                answer = throwable.message ?: "Не удалось получить данные PR #$prNumber.",
                usedRag = false,
                usedMcp = true,
                postedToGithub = false
            )
        }

        val prFiles = githubMcpServer.githubGetPullRequestFiles(projectId, prNumber).getOrElse { throwable ->
            return PullRequestReviewExecutionResult(
                answer = throwable.message ?: "Не удалось получить файлы PR #$prNumber.",
                usedRag = false,
                usedMcp = true,
                postedToGithub = false
            )
        }

        val prDiff = githubMcpServer.githubGetPullRequestDiff(projectId, prNumber).getOrElse { throwable ->
            return PullRequestReviewExecutionResult(
                answer = throwable.message ?: "Не удалось получить diff PR #$prNumber.",
                usedRag = false,
                usedMcp = true,
                postedToGithub = false
            )
        }

        if (prDiff.diff.isBlank()) {
            return PullRequestReviewExecutionResult(
                answer = "Diff для PR #$prNumber пуст. Недостаточно данных для ревью.",
                usedRag = false,
                usedMcp = true,
                postedToGithub = false
            )
        }

        val retrievalQuery = buildRetrievalQuery(
            title = prDetails.title,
            body = prDetails.body,
            fileNames = prFiles.map { it.filename },
            diff = prDiff.diff
        )

        val ragChunks = runCatching {
            ragRepository.retrieve(projectId, retrievalQuery, topK = 10)
        }.getOrDefault(emptyList())

        val recentMessages = runCatching {
            chatRepository.getMessages(chatId).takeLast(10).joinToString("\n") { "${it.role}: ${it.content}" }
        }.getOrDefault("")

        val context = promptBuilder.buildContext(
            projectTitle = project.title,
            binding = binding,
            details = prDetails,
            files = prFiles,
            diff = prDiff.diff,
            projectMemorySummary = memoryRepository.getMemory(projectId)?.summary.orEmpty(),
            ragChunks = ragChunks
        ) + if (recentMessages.isBlank()) "" else "\n\nRecent chat (last 10):\n$recentMessages"

        val reviewText = runCatching {
            openAiRepository.responses(
                model = project.selectedModel,
                systemPrompt = promptBuilder.systemPrompt(),
                context = context,
                userInput = "Review pull request #${prDetails.number}."
            )
        }.getOrElse { throwable ->
            return PullRequestReviewExecutionResult(
                answer = "Ошибка генерации ревью: ${throwable.message ?: "unknown"}",
                usedRag = ragChunks.isNotEmpty(),
                usedMcp = true,
                postedToGithub = false
            )
        }

        val normalizedReview = reviewText.ifBlank {
            "Potential Bugs:\n- Not enough evidence.\n\nArchitecture Concerns:\n- Not enough evidence.\n\nRecommendations:\n- Not enough evidence.\n\nOverall Verdict:\ncomment"
        }

        val inlineComments = parseInlineSuggestions(normalizedReview)
        val publishResult = githubMcpServer.githubSubmitPullRequestReview(
            projectId = projectId,
            prNumber = prNumber,
            review = GithubPullRequestReviewRequest(
                body = normalizedReview,
                comments = inlineComments
            )
        )

        return publishResult.fold(
            onSuccess = { published ->
                PullRequestReviewExecutionResult(
                    answer = normalizedReview + "\n\nGitHub review posted: ${published.htmlUrl}",
                    usedRag = ragChunks.isNotEmpty(),
                    usedMcp = true,
                    postedToGithub = true
                )
            },
            onFailure = { throwable ->
                PullRequestReviewExecutionResult(
                    answer = normalizedReview + "\n\nGitHub publish failed: ${throwable.message ?: "unknown error"}",
                    usedRag = ragChunks.isNotEmpty(),
                    usedMcp = true,
                    postedToGithub = false
                )
            }
        )
    }

    private fun buildRetrievalQuery(title: String, body: String, fileNames: List<String>, diff: String): String {
        return buildString {
            appendLine(title)
            appendLine(body)
            if (fileNames.isNotEmpty()) {
                appendLine("Files: ${fileNames.joinToString(", ")}")
            }
            appendLine(diff.take(4_000))
        }
    }

    private fun parseInlineSuggestions(reviewText: String): List<GithubPullRequestInlineComment> {
        return reviewText.lineSequence()
            .map { it.trim() }
            .filter { it.count { ch -> ch == '|' } >= 2 }
            .mapNotNull { line ->
                val parts = line.split("|", limit = 3).map { it.trim() }
                if (parts.size < 3) return@mapNotNull null
                val path = parts[0]
                val lineNumber = parts[1].toIntOrNull()
                val body = parts[2]
                if (path.isBlank() || lineNumber == null || lineNumber <= 0 || body.isBlank()) {
                    null
                } else {
                    GithubPullRequestInlineComment(path = path, line = lineNumber, body = body)
                }
            }
            .take(20)
            .toList()
    }
}
