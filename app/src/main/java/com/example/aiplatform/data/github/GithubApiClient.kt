package com.example.aiplatform.data.github

import com.example.aiplatform.core.network.GithubApiErrorDto
import com.example.aiplatform.core.network.GithubApiService
import com.example.aiplatform.core.network.GithubContentDto
import com.example.aiplatform.core.network.GithubPullRequestDetailsDto
import com.example.aiplatform.core.network.GithubPullRequestFileDto
import com.example.aiplatform.core.network.GithubPullRequestSummaryDto
import com.example.aiplatform.core.network.GithubRepoDto
import com.example.aiplatform.core.network.GithubSubmitReviewCommentDto
import com.example.aiplatform.core.network.GithubSubmitReviewRequestDto
import com.example.aiplatform.domain.model.GithubReadme
import com.example.aiplatform.domain.model.GithubPullRequestDetails
import com.example.aiplatform.domain.model.GithubPullRequestDiff
import com.example.aiplatform.domain.model.GithubPullRequestFile
import com.example.aiplatform.domain.model.GithubPullRequestReviewRequest
import com.example.aiplatform.domain.model.GithubPullRequestReviewResult
import com.example.aiplatform.domain.model.GithubPullRequestSummary
import com.example.aiplatform.domain.model.GithubRepo
import java.io.IOException
import java.util.Base64
import kotlinx.serialization.json.Json
import retrofit2.HttpException

interface GithubApiGateway {
    suspend fun listUserRepos(owner: String): Result<List<GithubRepo>>
    suspend fun getRepo(owner: String, repo: String): Result<GithubRepo>
    suspend fun fetchReadme(owner: String, repo: String): Result<GithubReadme>
    suspend fun listOpenPullRequests(owner: String, repo: String): Result<List<GithubPullRequestSummary>>
    suspend fun getPullRequest(owner: String, repo: String, prNumber: Int): Result<GithubPullRequestDetails>
    suspend fun listPullRequestFiles(owner: String, repo: String, prNumber: Int): Result<List<GithubPullRequestFile>>
    suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): Result<GithubPullRequestDiff>
    suspend fun submitPullRequestReview(
        owner: String,
        repo: String,
        prNumber: Int,
        request: GithubPullRequestReviewRequest
    ): Result<GithubPullRequestReviewResult>
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

    override suspend fun listOpenPullRequests(owner: String, repo: String): Result<List<GithubPullRequestSummary>> {
        if (owner.isBlank() || repo.isBlank()) {
            return Result.failure(IllegalArgumentException("Owner or repo is empty"))
        }
        return safeCall {
            service.listOpenPullRequests(owner.trim(), repo.trim())
                .map { it.toDomain() }
                .sortedByDescending { it.updatedAt }
        }
    }

    override suspend fun getPullRequest(owner: String, repo: String, prNumber: Int): Result<GithubPullRequestDetails> {
        if (owner.isBlank() || repo.isBlank() || prNumber <= 0) {
            return Result.failure(IllegalArgumentException("Invalid pull request lookup arguments"))
        }
        return safeCall {
            service.getPullRequest(owner.trim(), repo.trim(), prNumber).toDomain()
        }
    }

    override suspend fun listPullRequestFiles(owner: String, repo: String, prNumber: Int): Result<List<GithubPullRequestFile>> {
        if (owner.isBlank() || repo.isBlank() || prNumber <= 0) {
            return Result.failure(IllegalArgumentException("Invalid pull request files arguments"))
        }
        return safeCall {
            service.listPullRequestFiles(owner.trim(), repo.trim(), prNumber).map { it.toDomain() }
        }
    }

    override suspend fun getPullRequestDiff(owner: String, repo: String, prNumber: Int): Result<GithubPullRequestDiff> {
        if (owner.isBlank() || repo.isBlank() || prNumber <= 0) {
            return Result.failure(IllegalArgumentException("Invalid pull request diff arguments"))
        }
        return safeCall {
            val diffText = service.getPullRequestDiff(owner.trim(), repo.trim(), prNumber).use { body ->
                body.string()
            }
            GithubPullRequestDiff(diff = diffText)
        }
    }

    override suspend fun submitPullRequestReview(
        owner: String,
        repo: String,
        prNumber: Int,
        request: GithubPullRequestReviewRequest
    ): Result<GithubPullRequestReviewResult> {
        if (owner.isBlank() || repo.isBlank() || prNumber <= 0) {
            return Result.failure(IllegalArgumentException("Invalid pull request review arguments"))
        }
        if (request.body.isBlank()) {
            return Result.failure(IllegalArgumentException("Review body is empty"))
        }
        return safeCall {
            val ownerTrimmed = owner.trim()
            val repoTrimmed = repo.trim()
            val dtoWithComments = request.toGithubDto(includeComments = true)
            val response = runCatching {
                service.submitPullRequestReview(ownerTrimmed, repoTrimmed, prNumber, dtoWithComments)
            }.recoverCatching { throwable ->
                // GitHub often returns 422 when inline comments don't match PR diff lines.
                val http = throwable as? HttpException
                if (http?.code() == 422 && dtoWithComments.comments.isNotEmpty()) {
                    service.submitPullRequestReview(
                        ownerTrimmed,
                        repoTrimmed,
                        prNumber,
                        request.toGithubDto(includeComments = false)
                    )
                } else {
                    throw throwable
                }
            }.getOrElse { throw it }
            GithubPullRequestReviewResult(
                reviewId = response.id.toString(),
                htmlUrl = response.htmlUrl,
                submitted = true
            )
        }
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

private fun GithubPullRequestSummaryDto.toDomain(): GithubPullRequestSummary = GithubPullRequestSummary(
    number = number,
    title = title,
    author = user.login,
    updatedAt = updatedAt,
    htmlUrl = htmlUrl
)

private fun GithubPullRequestDetailsDto.toDomain(): GithubPullRequestDetails = GithubPullRequestDetails(
    number = number,
    title = title,
    body = body.orEmpty(),
    baseBranch = base.ref,
    headBranch = head.ref,
    author = user.login,
    htmlUrl = htmlUrl
)

private fun GithubPullRequestFileDto.toDomain(): GithubPullRequestFile = GithubPullRequestFile(
    filename = filename,
    status = status,
    additions = additions,
    deletions = deletions,
    patch = patch
)

private fun GithubPullRequestReviewRequest.toGithubDto(includeComments: Boolean): GithubSubmitReviewRequestDto {
    val commentsPayload = if (!includeComments) {
        emptyList()
    } else {
        comments.map { comment ->
            GithubSubmitReviewCommentDto(
                path = comment.path.removePrefix("-").trim(),
                line = comment.line,
                side = comment.side,
                body = comment.body.trim()
            )
        }.filter { dto ->
            dto.path.isNotBlank() && dto.line > 0 && dto.body.isNotBlank()
        }
    }

    return GithubSubmitReviewRequestDto(
        body = body,
        event = "COMMENT",
        comments = commentsPayload
    )
}
