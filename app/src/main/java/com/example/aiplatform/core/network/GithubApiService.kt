package com.example.aiplatform.core.network

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GithubApiService {
    @GET("users/{owner}/repos")
    suspend fun listUserRepos(@Path("owner") owner: String): List<GithubRepoDto>

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GithubRepoDto

    @GET("repos/{owner}/{repo}/readme")
    suspend fun getReadme(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GithubContentDto

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContentByPath(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): GithubContentDto

    @GET("repos/{owner}/{repo}/pulls")
    suspend fun listOpenPullRequests(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open"
    ): List<GithubPullRequestSummaryDto>

    @GET("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun getPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int
    ): GithubPullRequestDetailsDto

    @GET("repos/{owner}/{repo}/pulls/{pull_number}/files")
    suspend fun listPullRequestFiles(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int
    ): List<GithubPullRequestFileDto>

    @GET("repos/{owner}/{repo}/pulls/{pull_number}")
    suspend fun getPullRequestDiff(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Header("Accept") accept: String = "application/vnd.github.v3.diff"
    ): ResponseBody

    @POST("repos/{owner}/{repo}/pulls/{pull_number}/reviews")
    suspend fun submitPullRequestReview(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pull_number") pullNumber: Int,
        @Body body: GithubSubmitReviewRequestDto
    ): GithubPullRequestReviewResponseDto
}
