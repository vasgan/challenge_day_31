# AI Assistant Platform (Android, Kotlin, Compose)

Android-приложение для project-scoped developer assistant workflow.

## Что уже реализовано

- `Projects` (изолированные рабочие контексты)
- `Chats` внутри проекта
- `ProjectMemory` (shared memory на проект)
- `RAG` индексы, привязанные к проекту
- `MCP` подключения, привязанные к проекту
- `/help` routing через `DeveloperAssistantService`
- OpenAI only: `POST /v1/responses` и `POST /v1/embeddings`
- Compose UI

## Внутренний GitHub MCP server

В приложении реализован **собственный MCP server** (не внешний), который работает с GitHub API.

### MCP tools

- `github_list_user_repos`
- `github_bind_repo_to_project`
- `github_get_bound_repo`
- `github_fetch_readme`
- `github_build_rag_from_readme`

### Где реализовано

- MCP contracts/tools: `app/src/main/java/com/example/aiplatform/data/mcp/github/GithubMcpContracts.kt`
- Tool registry: `app/src/main/java/com/example/aiplatform/data/mcp/github/GithubToolRegistry.kt`
- Tool executor: `app/src/main/java/com/example/aiplatform/data/mcp/github/GithubMcpToolExecutorImpl.kt`
- MCP server facade: `app/src/main/java/com/example/aiplatform/data/mcp/github/GithubMcpServer.kt`
- GitHub API client: `app/src/main/java/com/example/aiplatform/data/github/GithubApiClient.kt`

## Project-scoped GitHub binding

Для привязки репозитория к проекту используется локальная Room сущность:

- `ProjectGithubBindingEntity`

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

Если проектный RAG уже построен из GitHub README, `/help` автоматически использует эти документы.

## UI flow (MCP экран)

На `McpScreen`:

1. Ввод `owner`
2. Кнопка `Загрузить репозитории`
3. Список репозиториев
4. Выбор одного репозитория
5. Кнопка `Привязать выбранный repo к проекту`
6. Кнопка `Импортировать README / Построить RAG`

Состояния UI:

- `Idle`
- `LoadingRepos`
- `RepoListLoaded`
- `BindingRepo`
- `ReadmeImporting`
- `RagBuilding`
- `Success`
- `Error`

## Сеть и таймауты

Для OpenAI и GitHub клиентов выставлены таймауты:

- connect/read/write/call: `20 seconds`

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
2. Укажите `OPENAI_API_KEY` и (опционально, но желательно) `GITHUB_API_TOKEN`.
3. Sync Gradle.
4. Запустите `app`.

## Тесты

Запуск unit-тестов:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest
```

Ключевые тесты:

- `GithubMcpServerTest`
- `DeveloperHelpRoutingTest`
- `ProjectIsolationTest`
- `MemoryPolicyTest`
- `ModelSelectionPerProjectTest`
- `RagOpenAiOnlyTest`
- `McpIsolationTest`
