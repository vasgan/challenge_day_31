package com.example.aiplatform.core.security

import com.example.aiplatform.BuildConfig

class MissingApiKeyException : IllegalStateException("OpenAI API token is missing")

interface SecureConfigProvider {
    fun openAiApiKey(): String
    fun githubApiTokenOrNull(): String?
}

class DefaultSecureConfigProvider : SecureConfigProvider {
    override fun openAiApiKey(): String {
        val buildConfigValue = BuildConfig.OPENAI_API_KEY.trim()
        if (buildConfigValue.isNotEmpty()) return buildConfigValue

        val envValue = System.getenv("OPENAI_API_KEY")?.trim().orEmpty()
        if (envValue.isNotEmpty()) return envValue

        throw MissingApiKeyException()
    }

    override fun githubApiTokenOrNull(): String? {
        val buildConfigValue = BuildConfig.GITHUB_API_TOKEN.trim()
        if (buildConfigValue.isNotEmpty()) return buildConfigValue

        val envValue = System.getenv("GITHUB_API_TOKEN")?.trim().orEmpty()
        if (envValue.isNotEmpty()) return envValue

        return null
    }
}
