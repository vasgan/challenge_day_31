package com.example.aiplatform.assistant

import com.example.aiplatform.data.mcp.github.GithubMcpServer
import com.example.aiplatform.domain.model.FileOpsTaskType
import com.example.aiplatform.domain.model.FileSearchHit
import com.example.aiplatform.domain.model.PullRequestPlan
import com.example.aiplatform.domain.repository.ChatRepository
import com.example.aiplatform.domain.repository.MemoryRepository
import com.example.aiplatform.domain.repository.OpenAiRepository
import com.example.aiplatform.domain.repository.ProjectRepository

class FileOpsAssistantService(
    private val projectRepository: ProjectRepository,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val openAiRepository: OpenAiRepository,
    private val githubMcpServer: GithubMcpServer,
    private val planner: FileOpsTaskPlanner,
    private val promptBuilder: FileOpsPromptBuilder
) : FileOpsAssistantHandler {

    override suspend fun runTask(projectId: String, chatId: String, goal: String): FileOpsResult {
        val normalizedGoal = goal.trim()
        if (normalizedGoal.isBlank()) {
            return FileOpsResult(
                answer = "Используйте формат: /file_task <цель>",
                success = false,
                changedFiles = emptyList(),
                openedPr = false,
                prUrl = null
            )
        }

        val project = projectRepository.getProject(projectId)
            ?: return FileOpsResult(
                answer = "Проект не найден.",
                success = false,
                changedFiles = emptyList(),
                openedPr = false,
                prUrl = null
            )

        val binding = githubMcpServer.githubGetBoundRepo(projectId).getOrElse { throwable ->
            return FileOpsResult(
                answer = throwable.message ?: "GitHub repo не привязан к проекту.",
                success = false,
                changedFiles = emptyList(),
                openedPr = false,
                prUrl = null
            )
        }

        val taskType = planner.classify(normalizedGoal)

        return when (taskType) {
            FileOpsTaskType.FIND_USAGE,
            FileOpsTaskType.INVARIANT_CHECK -> runReadOnlyTask(projectId, chatId, normalizedGoal, project.title)

            FileOpsTaskType.UPDATE_DOCS,
            FileOpsTaskType.GENERATE_FILE -> runWriteTask(
                projectId = projectId,
                chatId = chatId,
                goal = normalizedGoal,
                projectTitle = project.title,
                baseBranch = binding.defaultBranch,
                taskType = taskType
            )
        }
    }

    private suspend fun runReadOnlyTask(
        projectId: String,
        chatId: String,
        goal: String,
        projectTitle: String
    ): FileOpsResult {
        val query = planner.extractSearchQuery(goal)

        val matches = githubMcpServer.githubSearchInFiles(
            projectId = projectId,
            query = query,
            extensions = listOf("kt", "kts", "md", "xml", "yml", "yaml")
        ).getOrElse { throwable ->
            return FileOpsResult(
                answer = throwable.message ?: "Не удалось выполнить поиск по файлам.",
                success = false,
                changedFiles = emptyList(),
                openedPr = false,
                prUrl = null
            )
        }

        val hits = matches.map { FileSearchHit(path = it.path, line = it.line, snippet = it.snippet) }
        val recentChat = chatRepository.getMessages(chatId).takeLast(10)
        val llmContext = promptBuilder.findUsageContext(
            projectTitle = projectTitle,
            goal = goal,
            hits = hits,
            recentChat = recentChat
        )

        val analysis = runCatching {
            openAiRepository.responses(
                model = projectRepository.getProject(projectId)!!.selectedModel,
                systemPrompt = promptBuilder.systemPrompt(),
                context = llmContext,
                userInput = goal
            )
        }.getOrElse { throwable ->
            "Анализ не выполнен: ${throwable.message ?: "unknown"}"
        }

        val summary = if (hits.isEmpty()) {
            "Совпадений по запросу '$query' не найдено.\n\n$analysis"
        } else {
            val compact = hits.take(30).joinToString("\n") { "- ${it.path}:${it.line} -> ${it.snippet}" }
            "Найдено совпадений: ${hits.size}\n$compact\n\n$analysis"
        }

        return FileOpsResult(
            answer = summary,
            success = true,
            changedFiles = emptyList(),
            openedPr = false,
            prUrl = null
        )
    }

    private suspend fun runWriteTask(
        projectId: String,
        chatId: String,
        goal: String,
        projectTitle: String,
        baseBranch: String,
        taskType: FileOpsTaskType
    ): FileOpsResult {
        val query = planner.extractSearchQuery(goal)
        val targetPath = planner.targetPath(goal)

        val evidenceMatches = githubMcpServer.githubSearchInFiles(
            projectId = projectId,
            query = query,
            extensions = listOf("kt", "kts", "md", "xml")
        ).getOrDefault(emptyList())

        val evidenceHits = evidenceMatches
            .map { FileSearchHit(path = it.path, line = it.line, snippet = it.snippet) }
            .take(80)

        val oldContent = githubMcpServer.githubGetFileContent(projectId, targetPath, baseBranch)
            .getOrNull()
            ?.content
            .orEmpty()

        val memorySummary = memoryRepository.getMemory(projectId)?.summary.orEmpty()
        val recentChat = chatRepository.getMessages(chatId).takeLast(10)

        val llmContext = promptBuilder.writeContext(
            projectTitle = projectTitle,
            goal = buildString {
                append(goal)
                if (memorySummary.isNotBlank()) {
                    append("\n\nProject memory:\n")
                    append(memorySummary)
                }
            },
            targetPath = targetPath,
            oldContent = oldContent,
            evidence = evidenceHits,
            recentChat = recentChat
        )

        val generatedContent = runCatching {
            openAiRepository.responses(
                model = projectRepository.getProject(projectId)!!.selectedModel,
                systemPrompt = promptBuilder.systemPrompt(),
                context = llmContext,
                userInput = "Generate updated content for $targetPath"
            )
        }.getOrElse { throwable ->
            return FileOpsResult(
                answer = "Не удалось сгенерировать обновление файла: ${throwable.message ?: "unknown"}",
                success = false,
                changedFiles = emptyList(),
                openedPr = false,
                prUrl = null
            )
        }

        val sanitizedContent = generatedContent
            .removePrefix("```markdown")
            .removePrefix("```md")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        if (sanitizedContent.isBlank()) {
            return FileOpsResult(
                answer = "Модель вернула пустой контент для $targetPath.",
                success = false,
                changedFiles = emptyList(),
                openedPr = false,
                prUrl = null
            )
        }

        val branchName = "ai/fileops-${System.currentTimeMillis()}"
        val branchResult = githubMcpServer.githubCreateBranch(projectId, baseBranch, branchName)
        if (branchResult.isFailure) {
            return FileOpsResult(
                answer = "Не удалось создать ветку $branchName: ${branchResult.exceptionOrNull()?.message ?: "unknown"}",
                success = false,
                changedFiles = emptyList(),
                openedPr = false,
                prUrl = null
            )
        }

        val commitMessage = when (taskType) {
            FileOpsTaskType.GENERATE_FILE -> "chore(fileops): generate $targetPath"
            else -> "docs(fileops): update $targetPath"
        }

        val upsert = githubMcpServer.githubUpsertFileContent(
            projectId = projectId,
            branch = branchName,
            path = targetPath,
            content = sanitizedContent,
            message = commitMessage
        )

        if (upsert.isFailure) {
            return FileOpsResult(
                answer = "Не удалось обновить файл $targetPath: ${upsert.exceptionOrNull()?.message ?: "unknown"}",
                success = false,
                changedFiles = emptyList(),
                openedPr = false,
                prUrl = null
            )
        }

        val diffSummary = promptBuilder.diffSummary(oldContent, sanitizedContent)
        val prPlan = PullRequestPlan(
            title = "AI FileOps: update $targetPath",
            body = buildString {
                appendLine("Automated by /file_task")
                appendLine()
                appendLine("Goal: $goal")
                appendLine("Changed file: $targetPath")
                appendLine("Diff summary: $diffSummary")
            },
            head = branchName,
            base = baseBranch
        )

        val prResult = githubMcpServer.githubCreatePullRequest(
            projectId = projectId,
            title = prPlan.title,
            body = prPlan.body,
            head = prPlan.head,
            base = prPlan.base
        )

        return prResult.fold(
            onSuccess = { pr ->
                FileOpsResult(
                    answer = buildString {
                        appendLine("Файл обновлён: $targetPath")
                        appendLine("Diff summary: $diffSummary")
                        appendLine("PR: ${pr.htmlUrl}")
                    }.trim(),
                    success = true,
                    changedFiles = listOf(targetPath),
                    openedPr = true,
                    prUrl = pr.htmlUrl
                )
            },
            onFailure = { throwable ->
                FileOpsResult(
                    answer = buildString {
                        appendLine("Файл обновлён локально в ветке: $branchName")
                        appendLine("Diff summary: $diffSummary")
                        appendLine("Не удалось открыть PR: ${throwable.message ?: "unknown"}")
                    }.trim(),
                    success = true,
                    changedFiles = listOf(targetPath),
                    openedPr = false,
                    prUrl = null
                )
            }
        )
    }
}
