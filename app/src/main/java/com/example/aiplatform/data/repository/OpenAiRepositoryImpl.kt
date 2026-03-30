package com.example.aiplatform.data.repository

import com.example.aiplatform.core.network.EmbeddingData
import com.example.aiplatform.core.network.EmbeddingsRequest
import com.example.aiplatform.core.network.OpenAiApiService
import com.example.aiplatform.core.network.OpenAiErrorEnvelope
import com.example.aiplatform.core.network.ResponseContent
import com.example.aiplatform.core.network.ResponseInput
import com.example.aiplatform.core.network.ResponsesRequest
import com.example.aiplatform.core.security.MissingApiKeyException
import com.example.aiplatform.domain.model.EMBEDDING_MODEL
import com.example.aiplatform.domain.model.ProjectTextModel
import com.example.aiplatform.domain.repository.OpenAiRepository
import java.io.IOException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

sealed class OpenAiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class MissingToken(cause: Throwable) : OpenAiException("OpenAI API token is missing", cause)
    class Network(cause: Throwable) : OpenAiException("OpenAI network error", cause)
    class RateLimit(details: String, cause: Throwable) : OpenAiException("OpenAI rate limit: $details", cause)
    class Unauthorized(details: String, cause: Throwable) : OpenAiException("OpenAI unauthorized: $details", cause)
    class BadRequest(details: String, cause: Throwable) : OpenAiException("OpenAI bad request: $details", cause)
    class Unknown(details: String, cause: Throwable) : OpenAiException("OpenAI request failed: $details", cause)
}

class OpenAiRepositoryImpl(
    private val service: OpenAiApiService
) : OpenAiRepository {

    override suspend fun responses(
        model: ProjectTextModel,
        systemPrompt: String,
        context: String,
        userInput: String
    ): String {
        val request = ResponsesRequest(
            model = model.apiName,
            input = listOf(
                ResponseInput(
                    role = "system",
                    content = listOf(ResponseContent(type = "input_text", text = systemPrompt))
                ),
                ResponseInput(
                    role = "user",
                    content = listOf(
                        ResponseContent(
                            type = "input_text",
                            text = "Context:\n$context\n\nUser:\n$userInput"
                        )
                    )
                )
            )
        )

        return runCatching { service.responses(request) }
            .map { response ->
                response.output
                    .flatMap { it.content }
                    .firstOrNull { it.text != null }
                    ?.text
                    ?.trim()
                    .orEmpty()
            }
            .getOrElse { throw mapError(it) }
    }

    override suspend fun summarizeMemory(
        model: ProjectTextModel,
        currentSummary: String,
        archivedConversation: String
    ): String {
        val systemPrompt =
            "Summarize conversation history into compact structured memory. Keep: decisions, context, constraints. Do not write long prose."

        return responses(
            model = model,
            systemPrompt = systemPrompt,
            context = "Current summary:\n$currentSummary\n\nArchived conversation:\n$archivedConversation",
            userInput = "Update memory summary."
        )
    }

    override suspend fun embeddings(input: List<String>): List<List<Double>> {
        if (input.isEmpty()) return emptyList()

        return runCatching {
            service.embeddings(EmbeddingsRequest(model = EMBEDDING_MODEL, input = input))
        }.map { response ->
            response.data.sortedBy(EmbeddingData::index).map(EmbeddingData::embedding)
        }.getOrElse { throw mapError(it) }
    }

    private fun mapError(throwable: Throwable): OpenAiException {
        return when (throwable) {
            is MissingApiKeyException -> OpenAiException.MissingToken(throwable)
            is IOException -> OpenAiException.Network(throwable)
            is HttpException -> {
                val details = extractHttpErrorDetails(throwable)
                when (throwable.code()) {
                    400 -> OpenAiException.BadRequest(details, throwable)
                    401 -> OpenAiException.Unauthorized(details, throwable)
                    429 -> OpenAiException.RateLimit(details, throwable)
                    else -> OpenAiException.Unknown("HTTP ${throwable.code()}: $details", throwable)
                }
            }
            else -> OpenAiException.Unknown(throwable.message.orEmpty().ifBlank { "unknown error" }, throwable)
        }
    }

    private fun extractHttpErrorDetails(httpException: HttpException): String {
        val raw = runCatching { httpException.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        if (raw.isBlank()) return httpException.message().orEmpty().ifBlank { "no error body" }

        val parsed = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString(OpenAiErrorEnvelope.serializer(), raw)
        }.getOrNull()

        val serverMessage = parsed?.error?.message.orEmpty()
        return if (serverMessage.isNotBlank()) serverMessage else raw.take(500)
    }
}
