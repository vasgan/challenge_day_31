# AI Assistant Platform (Android, Kotlin, Compose)

Production-ready каркас Android-платформы для AI/developer assistant workflow с поддержкой:

- несколько `Projects`
- несколько `Chats` в каждом проекте
- общая `ProjectMemory` на проект
- несколько `MCP` подключений на проект
- несколько `RAG` индексов на проект
- выбор OpenAI модели на уровне проекта (строгий whitelist)
- только OpenAI remote API (`/v1/responses`, `/v1/embeddings`)

## OpenAI API (строго)

Используются только:

- `POST /v1/responses` для chat, summarization, control/reasoning
- `POST /v1/embeddings` только для RAG embeddings

## Модели

`ProjectTextModel`:

- `gpt-5-mini`
- `gpt-5-nano`
- `gpt-5`
- `gpt-5-coder`

Embedding модель фиксирована:

- `text-embedding-3-small`

## Секреты

API токен не хранится в коде.

Поддержано чтение:

- `OPENAI_API_KEY` из `local.properties` / `gradle.properties`
- fallback: env `OPENAI_API_KEY`

`SecureConfigProvider` находится в:

- `app/src/main/java/com/example/aiplatform/core/security/SecureConfigProvider.kt`

Добавлено в `.gitignore`:

- `local.properties`
- `secrets.properties`

## Memory policy

- short memory: последние 10 сообщений
- archived: остальные
- при `> 10 + 30` запускается summarization через Responses API
- после summarization старый overflow удаляется, summary обновляется

`ProjectMemoryManager`:

- `app/src/main/java/com/example/aiplatform/data/memory/ProjectMemoryManager.kt`

## Оркестрация

`AgentOrchestrator` flow:

1. сохранить user message
2. собрать project memory
3. собрать chat window (last 10)
4. optional RAG retrieval
5. optional MCP payload
6. вызвать OpenAI Responses API
7. сохранить assistant message

Файл:

- `app/src/main/java/com/example/aiplatform/agent/AgentOrchestrator.kt`

## Compose экраны

- ProjectsScreen
- ProjectScreen
- ChatListScreen
- ChatScreen
- SettingsScreen (model selection)
- McpScreen
- RagScreen
- MemoryScreen

## Тесты

Добавлены unit-тесты:

- `ProjectIsolationTest`
- `MemoryPolicyTest`
- `ModelSelectionPerProjectTest`
- `RagOpenAiOnlyTest`
- `McpIsolationTest`

## Запуск

1. Откройте проект в Android Studio.
2. Укажите `OPENAI_API_KEY` в `local.properties` или `~/.gradle/gradle.properties`.
3. Sync project.
4. Запустите `app`.
