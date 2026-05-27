package com.ghealth.tools.core.network.api

import com.ghealth.tools.core.network.model.ApiResponse
import com.ghealth.tools.core.network.model.CreateProjectRequest
import com.ghealth.tools.core.network.model.PaginatedListResponse
import com.ghealth.tools.core.network.model.ProductionTestConfigResponse
import com.ghealth.tools.core.network.model.ProjectResponse
import com.ghealth.tools.core.network.model.RegularConfigResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ProjectApi {
    @GET("projects/")
    suspend fun getProjects(): Response<PaginatedListResponse<ProjectResponse>>

    @GET("projects/{id}/")
    suspend fun getProject(
        @Path("id") projectId: Int
    ): Response<ProjectResponse>

    @POST("projects/")
    suspend fun createProject(
        @Body request: CreateProjectRequest
    ): Response<ApiResponse<ProjectResponse>>

    @DELETE("projects/{id}/")
    suspend fun deleteProject(
        @Path("id") projectId: Int
    ): Response<ApiResponse<Unit>>

    @GET("projects/{project_id}/prod-test-config/")
    suspend fun getProductionTestConfig(
        @Path("project_id") projectId: Int
    ): Response<ApiResponse<ProductionTestConfigResponse>>

    @GET("projects/{project_id}/regular-configs/")
    suspend fun getRegularConfigs(
        @Path("project_id") projectId: Int
    ): Response<ApiResponse<List<RegularConfigResponse>>>
}