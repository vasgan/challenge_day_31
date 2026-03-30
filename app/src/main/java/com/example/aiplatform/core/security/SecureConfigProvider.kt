package com.example.aiplatform.core.security

import com.example.aiplatform.BuildConfig

class MissingApiKeyException : IllegalStateException("OpenAI API token is missing")

interface SecureConfigProvider {
    fun openAiApiKey(): String
}

class DefaultSecureConfigProvider : SecureConfigProvider {
    override fun openAiApiKey(): String {
        val buildConfigValue = BuildConfig.OPENAI_API_KEY.trim()
        if (buildConfigValue.isNotEmpty()) return buildConfigValue

        val envValue = System.getenv("OPENAI_API_KEY")?.trim().orEmpty()
        if (envValue.isNotEmpty()) return envValue

        throw MissingApiKeyException()
    }
}
