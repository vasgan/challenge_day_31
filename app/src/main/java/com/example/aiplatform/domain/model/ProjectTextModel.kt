package com.example.aiplatform.domain.model

enum class ProjectTextModel(val apiName: String) {
    GPT_5_MINI("gpt-5-mini"),
    GPT_5_NANO("gpt-5-nano"),
    GPT_5("gpt-5"),
    GPT_5_CODER("gpt-5-coder");

    companion object {
        fun fromApiName(value: String): ProjectTextModel = entries.firstOrNull { it.apiName == value }
            ?: GPT_5_MINI
    }
}

const val EMBEDDING_MODEL = "text-embedding-3-small"
