package com.example.aiplatform.core.network

import retrofit2.http.GET
import retrofit2.http.Path

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
}
