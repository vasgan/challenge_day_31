package com.example.aiplatform.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResponsesRequest(
    val model: String,
    val input: List<ResponseInput>
)

@Serializable
data class ResponseInput(
    val role: String,
    val content: List<ResponseContent>
)

@Serializable
data class ResponseContent(
    val type: String,
    val text: String
)

@Serializable
data class ResponsesResponse(
    val output: List<ResponseOutput> = emptyList()
)

@Serializable
data class ResponseOutput(
    val content: List<ResponseOutputContent> = emptyList()
)

@Serializable
data class ResponseOutputContent(
    val type: String? = null,
    val text: String? = null
)

@Serializable
data class EmbeddingsRequest(
    val model: String,
    val input: List<String>
)

@Serializable
data class EmbeddingsResponse(
    val data: List<EmbeddingData>
)

@Serializable
data class EmbeddingData(
    val index: Int,
    val embedding: List<Double>
)

@Serializable
data class OpenAiErrorEnvelope(
    val error: OpenAiErrorDetail? = null
)

@Serializable
data class OpenAiErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
    @SerialName("param") val parameter: String? = null
)
