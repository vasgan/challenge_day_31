package com.example.aiplatform.data.github

import com.example.aiplatform.core.network.GithubApiErrorDto
import com.example.aiplatform.core.network.GithubApiService
import com.example.aiplatform.core.network.GithubContentDto
import com.example.aiplatform.core.network.GithubCreateOrUpdateFileRequestDto
import com.example.aiplatform.core.network.GithubCreatePullRequestRequestDto
import com.example.aiplatform.core.network.GithubPullRequestDetailsDto
import com.example.aiplatform.core.network.GithubPullRequestFileDto
import com.example.aiplatform.core.network.GithubPullRequestSummaryDto
import com.example.aiplatform.core.network.GithubRepoDto
import com.example.aiplatform.core.network.GithubGitTreeEntryDto
import com.example.aiplatform.core.network.GithubCreateRefRequestDto
import com.example.aiplatform.core.network.GithubSubmitReviewCommentDto
import com.example.aiplatform.core.network.GithubSubmitReviewRequestDto
import com.example.aiplatform.domain.model.GithubBranchInfo
import com.example.aiplatform.domain.model.GithubCreatedPullRequest
import com.example.aiplatform.domain.model.GithubFileContent
import com.example.aiplatform.domain.model.GithubFileSearchMatch
import com.example.aiplatform.domain.model.GithubFileUpsertResult
import com.example.aiplatform.domain.model.GithubReadme
import com.example.aiplatform.domain.model.GithubRepoFileEntry
import com.example.aiplatform.domain.model.GithubPullRequestDetails
import com.example.aiplatform.domain.model.GithubPullRequestDiff
import com.example.aiplatform.domain.model.GithubPullRequestFile
import com.example.aiplatform.domain.model.GithubPullRequestReviewRequest
import com.example.aiplatform.domain.model.GithubPullRequestReviewResult
import com.example.aiplatform.domain.model.GithubPullRequestSummary
import com.example.aiplatform.domain.model.GithubRepo
import java.io.IOException
import java.util.Base64
import kotlin.text.RegexOption.IGNORE_CASE
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
    suspend fun listRepositoryFiles(
        owner: String,
        repo: String,
        path: String = "",
        recursive: Boolean = true
    ): Result<List<GithubRepoFileEntry>>
    suspend fun getFileContent(
        owner: String,
        repo: String,
        path: String,
        ref: String? = null
    ): Result<GithubFileContent>
    suspend fun searchInFiles(
        owner: String,
        repo: String,
        query: String,
        extensions: List<String>
    ): Result<List<GithubFileSearchMatch>>
    suspend fun createBranch(
        owner: String,
        repo: String,
        base: String,
        branch: String
    ): Result<GithubBranchInfo>
    suspend fun upsertFileContent(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        content: String,
        message: String
    ): Result<GithubFileUpsertResult>
    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String
    ): Result<GithubCreatedPullRequest>
}

class GithubApiClient(
    private val service: GithubApiService
) : GithubApiGateway {
    private val readmeCandidates = listOf("README.md", "Readme.md", "readme.md", "README")
    private companion object {
        const val MAX_SEARCH_FILES = 200
        const val MAX_SEARCH_MATCHES = 200
        const val MAX_FILE_SIZE_FOR_SEARCH_BYTES = 200_000
        const val MAX_SNIPPET_CHARS = 500
    }

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

    override suspend fun listRepositoryFiles(
        owner: String,
        repo: String,
        path: String,
        recursive: Boolean
    ): Result<List<GithubRepoFileEntry>> {
        if (owner.isBlank() || repo.isBlank()) {
            return Result.failure(IllegalArgumentException("Owner or repo is empty"))
        }

        val normalizedPath = path.trim().trim('/')
        val repoInfo = getRepo(owner, repo).getOrElse { return Result.failure(it) }
        return safeCall {
            val tree = service.getRepositoryTree(
                owner = owner.trim(),
                repo = repo.trim(),
                treeSha = repoInfo.defaultBranch,
                recursive = if (recursive) 1 else null
            )

            tree.tree
                .filter { entry ->
                    normalizedPath.isBlank() ||
                        entry.path == normalizedPath ||
                        entry.path.startsWith("$normalizedPath/")
                }
                .map { it.toDomain() }
                .sortedBy { it.path }
        }
    }

    override suspend fun getFileContent(
        owner: String,
        repo: String,
        path: String,
        ref: String?
    ): Result<GithubFileContent> {
        val normalizedPath = path.trim().trim('/')
        if (owner.isBlank() || repo.isBlank() || normalizedPath.isBlank()) {
            return Result.failure(IllegalArgumentException("Owner, repo and path are required"))
        }

        return safeCall {
            val dto = service.getContentByPath(owner.trim(), repo.trim(), normalizedPath, ref?.trim()?.ifBlank { null })
            val text = decodeContent(dto.content.orEmpty(), dto.encoding)
            GithubFileContent(
                path = dto.path,
                sha = dto.sha.orEmpty(),
                ref = ref,
                content = text
            )
        }
    }

    override suspend fun searchInFiles(
        owner: String,
        repo: String,
        query: String,
        extensions: List<String>
    ): Result<List<GithubFileSearchMatch>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return Result.failure(IllegalArgumentException("query is required"))
        }

        val normalizedExt = extensions
            .map { it.trim().trimStart('.').lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        val files = listRepositoryFiles(owner, repo, recursive = true).getOrElse { return Result.failure(it) }
            .asSequence()
            .filter { it.type == "blob" }
            .filter { entry ->
                normalizedExt.isEmpty() || normalizedExt.any { ext -> entry.path.lowercase().endsWith(".$ext") }
            }
            .take(MAX_SEARCH_FILES)
            .toList()

        return runCatching {
            val regex = Regex(Regex.escape(normalizedQuery), setOf(IGNORE_CASE))
            val matches = mutableListOf<GithubFileSearchMatch>()

            files.forEach { file ->
                if ((file.size ?: 0) > MAX_FILE_SIZE_FOR_SEARCH_BYTES) {
                    return@forEach
                }
                val content = getFileContent(owner, repo, file.path, null).getOrElse { return@forEach }
                content.content.lineSequence().forEachIndexed { index, line ->
                    if (regex.containsMatchIn(line)) {
                        matches += GithubFileSearchMatch(
                            path = file.path,
                            line = index + 1,
                            snippet = line.trim().take(MAX_SNIPPET_CHARS)
                        )
                    }
                }
            }

            matches.take(MAX_SEARCH_MATCHES)
        }.recoverCatching { throwable ->
            throw mapError(throwable)
        }
    }

    override suspend fun createBranch(
        owner: String,
        repo: String,
        base: String,
        branch: String
    ): Result<GithubBranchInfo> {
        val baseRef = base.trim()
        val newBranch = branch.trim().removePrefix("refs/heads/")
        if (owner.isBlank() || repo.isBlank() || baseRef.isBlank() || newBranch.isBlank()) {
            return Result.failure(IllegalArgumentException("owner, repo, base and branch are required"))
        }

        return safeCall {
            val reference = service.getReference(owner.trim(), repo.trim(), "heads/$baseRef")
            val created = service.createReference(
                owner = owner.trim(),
                repo = repo.trim(),
                body = GithubCreateRefRequestDto(
                    ref = "refs/heads/$newBranch",
                    sha = reference.obj.sha
                )
            )
            GithubBranchInfo(
                name = newBranch,
                ref = created.ref,
                sha = created.obj.sha
            )
        }
    }

    override suspend fun upsertFileContent(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        content: String,
        message: String
    ): Result<GithubFileUpsertResult> {
        val normalizedPath = path.trim().trim('/')
        val normalizedBranch = branch.trim().removePrefix("refs/heads/")
        if (
            owner.isBlank() || repo.isBlank() || normalizedBranch.isBlank() ||
            normalizedPath.isBlank() || content.isBlank() || message.isBlank()
        ) {
            return Result.failure(IllegalArgumentException("owner, repo, branch, path, content, message are required"))
        }

        val existingSha = runCatching {
            service.getContentByPath(owner.trim(), repo.trim(), normalizedPath, normalizedBranch).sha
        }.getOrNull()

        return safeCall {
            val response = service.createOrUpdateFileContent(
                owner = owner.trim(),
                repo = repo.trim(),
                path = normalizedPath,
                body = GithubCreateOrUpdateFileRequestDto(
                    message = message.trim(),
                    content = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8)),
                    branch = normalizedBranch,
                    sha = existingSha
                )
            )
            GithubFileUpsertResult(
                path = response.content.path,
                fileSha = response.content.sha,
                commitSha = response.commit.sha,
                commitUrl = response.commit.htmlUrl
            )
        }
    }

    override suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String
    ): Result<GithubCreatedPullRequest> {
        if (
            owner.isBlank() || repo.isBlank() || title.isBlank() ||
            head.isBlank() || base.isBlank()
        ) {
            return Result.failure(IllegalArgumentException("owner, repo, title, head, base are required"))
        }
        return safeCall {
            val response = service.createPullRequest(
                owner = owner.trim(),
                repo = repo.trim(),
                body = GithubCreatePullRequestRequestDto(
                    title = title.trim(),
                    body = body,
                    head = head.trim(),
                    base = base.trim()
                )
            )
            GithubCreatedPullRequest(
                number = response.number,
                title = response.title,
                htmlUrl = response.htmlUrl
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

private fun GithubGitTreeEntryDto.toDomain(): GithubRepoFileEntry = GithubRepoFileEntry(
    path = path,
    type = type,
    size = size,
    sha = sha
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
