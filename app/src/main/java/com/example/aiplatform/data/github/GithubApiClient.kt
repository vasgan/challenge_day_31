package com.example.aiplatform.data.github

import com.example.aiplatform.core.network.GithubApiErrorDto
import com.example.aiplatform.core.network.GithubApiService
import com.example.aiplatform.core.network.GithubContentDto
import com.example.aiplatform.core.network.GithubRepoDto
import com.example.aiplatform.domain.model.GithubReadme
import com.example.aiplatform.domain.model.GithubRepo
import java.io.IOException
import java.util.Base64
import kotlinx.serialization.json.Json
import retrofit2.HttpException

interface GithubApiGateway {
    suspend fun listUserRepos(owner: String): Result<List<GithubRepo>>
    suspend fun getRepo(owner: String, repo: String): Result<GithubRepo>
    suspend fun fetchReadme(owner: String, repo: String): Result<GithubReadme>
}

class GithubApiClient(
    private val service: GithubApiService
) : GithubApiGateway {
    private val readmeCandidates = listOf("README.md", "Readme.md", "readme.md", "README")

    override suspend fun listUserRepos(owner: String): Result<List<GithubRepo>> {
        if (owner.isBlank()) return Result.failure(IllegalArgumentException("Owner is empty"))
        return safeCall {
            service.listUserRepos(owner.trim())
                .map { it.toDomain() }
                .sortedBy { it.name.lowercase() }
        }
    }

    override suspend fun getRepo(owner: String, repo: String): Result<GithubRepo> {
        if (owner.isBlank() || repo.isBlank()) {
            return Result.failure(IllegalArgumentException("Owner or repo is empty"))
        }
        return safeCall { service.getRepo(owner.trim(), repo.trim()).toDomain() }
    }

    override suspend fun fetchReadme(owner: String, repo: String): Result<GithubReadme> {
        val repoInfo = getRepo(owner, repo).getOrElse { return Result.failure(it) }
        val contentDto = findReadme(owner, repo).getOrElse { return Result.failure(it) }

        val text = decodeContent(contentDto.content.orEmpty(), contentDto.encoding)
        if (text.isBlank()) {
            return Result.failure(IllegalStateException("README content is empty"))
        }

        return Result.success(
            GithubReadme(
                owner = owner,
                repo = repo,
                path = contentDto.path,
                branch = repoInfo.defaultBranch,
                text = text
            )
        )
    }

    private suspend fun findReadme(owner: String, repo: String): Result<GithubContentDto> {
        safeCall { service.getReadme(owner, repo) }.onSuccess { return Result.success(it) }

        readmeCandidates.forEach { name ->
            safeCall { service.getContentByPath(owner, repo, name) }
                .onSuccess { return Result.success(it) }
        }

        return Result.failure(IllegalStateException("README not found in $owner/$repo"))
    }

    private fun decodeContent(content: String, encoding: String?): String {
        if (content.isBlank()) return ""
        return if (encoding == "base64") {
            val normalized = content.replace("\n", "")
            String(Base64.getDecoder().decode(normalized), Charsets.UTF_8)
        } else {
            content
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
        return runCatching { block() }.recoverCatching { throwable ->
            throw mapError(throwable)
        }
    }

    private fun mapError(throwable: Throwable): Throwable {
        return when (throwable) {
            is IOException -> IllegalStateException("GitHub API unavailable: ${throwable.message}")
            is HttpException -> {
                val details = extractHttpError(throwable)
                when (throwable.code()) {
                    401, 403 -> IllegalStateException("GitHub auth error: $details")
                    404 -> IllegalStateException("GitHub resource not found: $details")
                    else -> IllegalStateException("GitHub API error ${throwable.code()}: $details")
                }
            }
            else -> IllegalStateException(throwable.message ?: "Unknown GitHub error")
        }
    }

    private fun extractHttpError(httpException: HttpException): String {
        val raw = runCatching { httpException.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        if (raw.isBlank()) return httpException.message().orEmpty()
        val parsed = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString(GithubApiErrorDto.serializer(), raw)
        }.getOrNull()
        return parsed?.message ?: raw.take(300)
    }
}

private fun GithubRepoDto.toDomain(): GithubRepo = GithubRepo(
    owner = owner.login,
    name = name,
    fullName = fullName,
    htmlUrl = htmlUrl,
    defaultBranch = defaultBranch
)
