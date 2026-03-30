package com.example.aiplatform.core.network

import com.example.aiplatform.core.security.SecureConfigProvider
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

object NetworkModule {
    fun createOpenAiApiService(configProvider: SecureConfigProvider): OpenAiApiService {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${configProvider.openAiApiKey()}")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiApiService::class.java)
    }

    fun createGithubApiService(configProvider: SecureConfigProvider): GithubApiService {
        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")

            configProvider.githubApiTokenOrNull()?.let { token ->
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GithubApiService::class.java)
    }
}
