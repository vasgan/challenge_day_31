package com.example.aiplatform.core.network

import retrofit2.http.Body
import retrofit2.http.POST

interface OpenAiApiService {
    @POST("v1/responses")
    suspend fun responses(@Body request: ResponsesRequest): ResponsesResponse

    @POST("v1/embeddings")
    suspend fun embeddings(@Body request: EmbeddingsRequest): EmbeddingsResponse
}
