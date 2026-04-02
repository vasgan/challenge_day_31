# AI Assistant Platform (Android, Kotlin, Compose)

Android-приложение для project-scoped developer assistant workflow.

## Последние изменения в GitHubMcpServer

По предоставленному контексту явных изменений в реализации GitHubMcpServer не обнаружено. Текущий README обновлён, чтобы зафиксировать текущее состояние и структуру кода, где реализованы контракты, регистр инструментов, исполнитель и фасад MCP сервера для GitHub. Если у вас есть конкретные коммиты/патчи или список изменений — пришлите их, и я внесу детальное обновление с перечислением новых методов/файлов и инструкциями по миграции.

## Что реализовано сейчас

- `Projects` (изолированные рабочие контексты)
- `Chats` внутри проекта
- `ProjectMemory` (shared memory на проект)
- `RAG` индексы, привязанные к проекту
- `MCP` подключения, привязанные к проекту
- `/help` routing через `DeveloperAssistantService`
- `/review_pr` routing через `PullRequestReviewService`
- OpenAI only:
  - `POST /v1/responses`
  - `POST /v1/embeddings`
- Compose UI

## Команды в чате

- `/help <вопрос>` — вопросы о проекте с использованием project memory + project RAG + repo context.
- `/review_pr` — список открытых PR текущего привязанного GitHub repo.
- `/review_pr <number>` — AI review конкретного PR:
  - анализ diff + changed files + project RAG
  - ответ в чат новым assistant-сообщением
  - попытка публикации review в GitHub PR.

## Внутренний GitHub MCP server

В приложении используется **собственный MCP server** (внутри app, не внешний), который работает через GitHub API.

### MCP tools

#### Repo / README / RAG

- `github_list_user_repos`
- `github_bind_repo_to_project`
- `github_get_bound_repo`
- `github_fetch_readme`
- `github_build_rag_from_readme`

#### Pull Request review

- `github_list_open_pull_requests`
- `github_get_pull_request_details`
- `github_get_pull_request_files`
- `github_get_pull_request_diff`
- `github_submit_pull_request_review`

### Где реализовано

- MCP contracts/tools:
  - `app/src/main/java/com/example/aiplatform/data/mcp/github/GithubMcpContracts.kt`
- Tool registry:
  - `app/src/main/java/com/example/aiplatform/data/mcp/github/GithubToolRegistry.kt`
- Tool executor:
  - `app/src/main/java/com/example/aiplatform/data/mcp/github/GithubMcpToolExecutorImpl.kt`
- MCP server facade:
  - `app/src/main/java/com/example/aiplatform/data/mcp/github/GithubMcpServer.kt`
- GitHub API client:
  - `app/src/main/java/com/example/aiplatform/data/github/GithubApiClient.kt`

## Project-scoped GitHub binding

Привязка repo к проекту хранится в Room-сущности `ProjectGithubBindingEntity`.

Поля:

- `projectId`
- `owner`
- `repo`
- `repoUrl`
- `defaultBranch`
- `readmeImportedAt`
- `ragIndexId`
- `createdAt`

Где:

- entity: `app/src/main/java/com/example/aiplatform/data/local/entity/ProjectGithubBindingEntity.kt`
- dao: `app/src/main/java/com/example/aiplatform/data/local/dao/ProjectGithubBindingDao.kt`
- repository: `app/src/main/java/com/example/aiplatform/data/repository/ProjectGithubBindingRepositoryImpl.kt`

## README -> RAG flow

Flow:

1. `github_fetch_readme(projectId)`
2. README text (base64 decode при необходимости)
3. chunking
4. embeddings (`text-embedding-3-small`)
5. сохранение chunks в Room
6. create/update `RagIndex`
7. update `ProjectGithubBinding.readmeImportedAt/ragIndexId`

Metadata source для chunk:

- `github_readme|owner/repo|path|branch`

## /help flow

`/help <вопрос>` перехватывается до обычного chat flow.

`DeveloperAssistantService` использует:

- project memory
- последние 10 сообщений чата
- project RAG retrieval
- bound repo context (`defaultBranch`)

## /review_pr flow

`/review_pr` и `/review_pr <number>` перехватываются в `AgentOrchestrator` до обычного chat flow.

`PullRequestReviewService` выполняет:

1. Проверка `project` и project-scoped GitHub binding.
2. Получение PR details/files/diff через GitHub MCP tools.
3. Retrieval из project-scoped RAG (`topK=10`).
4. Сбор prompt через `PullRequestReviewPromptBuilder`.
5. Вызов OpenAI Responses API.
6. Возврат review в чат.
7. Публикация review в GitHub PR.
8. Fallback: если GitHub publish упал, review всё равно возвращается в чат.

Где:

- service: `app/src/main/java/com/example/aiplatform/assistant/PullRequestReviewService.kt`
- prompt builder: `app/src/main/java/com/example/aiplatform/assistant/PullRequestReviewPromptBuilder.kt`
- routing: `app/src/main/java/com/example/aiplatform/agent/AgentOrchestrator.kt`

## UI flow

### MCP экран (`McpScreen`)

1. Ввод `owner`
2. `Загрузить репозитории`
3. Выбор репозитория
4. `Привязать выбранный repo к проекту`
5. `Импортировать README / Построить RAG`

Состояния:

- `Idle`
- `LoadingRepos`
- `RepoListLoaded`
- `BindingRepo`
- `ReadmeImporting`
- `RagBuilding`
- `Success`
- `Error`

### Chat экран (`ChatScreen`)

- Для `/review_pr` список PR приходит как assistant message.
- Для `/review_pr <number>` review приходит как assistant message.
- В metadata сообщения пишется `reviewCommand` + mode/status.

## GitHub Action (автоматический review на PR)

Workflow:

- `.github/workflows/ai-pr-review.yml`

Trigger:

- `pull_request` (`opened`, `synchronize`, `reopened`)
- `workflow_dispatch`

Действия:

1. Забирает PR metadata/files/diff из GitHub API.
2. Формирует review context.
3. Вызывает OpenAI Responses API.
4. Публикует comment в PR.

## Сеть и таймауты

Для OpenAI и GitHub клиентов выставлены таймауты:

- connect/read/write/call: `180 seconds`

## Конфигурация секретов

Секреты не хардкодятся.

Поддерживаются:

- `OPENAI_API_KEY`
- `GITHUB_API_TOKEN`

Источники:

- `local.properties`
- `secrets.properties`
- Gradle properties
- environment variables

## Запуск

1. Откройте проект в Android Studio.
2. Укажите `OPENAI_API_KEY` и `GITHUB_API_TOKEN`.
3. Sync Gradle.
4. Запустите `app`.

## Как проверить вручную

1. Откройте проект.
2. Перейдите в `MCP`.
3. Подключите GitHub repo (owner -> list repos -> select -> bind).
4. Нажмите `Импортировать README / Построить RAG`.
5. Откройте `Chat`.
6. Выполните `/review_pr`.
7. Выберите номер и выполните `/review_pr <number>`.
8. Проверьте:
   - review появился в чате
   - review/comment появился в GitHub PR.

## Тесты

Запуск unit-тестов:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest
```

Ключевые тесты:

- `GithubMcpServerTest`
- `GithubPrMcpToolsTest`
- `DeveloperHelpRoutingTest`
- `PullRequestReviewRoutingTest`
- `PullRequestReviewServiceTest`
- `ProjectIsolationTest`
- `MemoryPolicyTest`
- `ModelSelectionPerProjectTest`
- `RagOpenAiOnlyTest`
- `McpIsolationTest`

Если вы ожидаете конкретных изменений в GitHubMcpServer (новые инструменты, изменения сигнатур методов, перемещение файлов и т.п.), приложите diff или список изменённых файлов — я обновлю документацию с точными инструкциями по миграции и обновлению кода.