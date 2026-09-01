package com.ghealth.tools.core.network.api

import com.ghealth.tools.core.network.model.ApiResponse
import com.ghealth.tools.core.network.model.ArchiveActionRequest
import com.ghealth.tools.core.network.model.CreateProjectRequest
import com.ghealth.tools.core.network.model.CsvFileResponse
import com.ghealth.tools.core.network.model.ProductionTestConfigResponse
import com.ghealth.tools.core.network.model.ProjectExportResponse
import com.ghealth.tools.core.network.model.ProjectResponse
import com.ghealth.tools.core.network.model.RegularConfigResponse
import com.ghealth.tools.core.network.model.UpdateProjectRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ProjectApi {
    @GET("projects/")
    suspend fun getProjects(
        @Query("archived") archived: Int? = null,
    ): Response<ApiResponse<List<ProjectResponse>>>

    @GET("projects/{id}/")
    suspend fun getProject(
        @Path("id") projectId: Int
    ): Response<ApiResponse<ProjectResponse>>

    @POST("projects/")
    suspend fun createProject(
        @Body request: CreateProjectRequest
    ): Response<ApiResponse<ProjectResponse>>

    @PUT("projects/{id}/")
    suspend fun updateProject(
        @Path("id") projectId: Int,
        @Body request: UpdateProjectRequest
    ): Response<ApiResponse<ProjectResponse>>

    @DELETE("projects/{id}/")
    suspend fun deleteProject(
        @Path("id") projectId: Int
    ): Response<ApiResponse<Unit>>

    @POST("projects/{id}/")
    suspend fun projectAction(
        @Path("id") projectId: Int,
        @Body request: ArchiveActionRequest
    ): Response<ApiResponse<Unit>>

    @GET("projects/{id}/export/")
    suspend fun exportProject(
        @Path("id") projectId: Int
    ): Response<ApiResponse<ProjectExportResponse>>

    @GET("projects/{project_id}/prod-test-config/")
    suspend fun getProductionTestConfig(
        @Path("project_id") projectId: Int
    ): Response<ApiResponse<ProductionTestConfigResponse>>

    @DELETE("projects/{project_id}/prod-test-config/")
    suspend fun deleteProductionTestConfig(
        @Path("project_id") projectId: Int
    ): Response<ApiResponse<Unit>>

    @GET("projects/{project_id}/regular-configs/")
    suspend fun getRegularConfigs(
        @Path("project_id") projectId: Int
    ): Response<ApiResponse<List<RegularConfigResponse>>>

    @DELETE("regular-configs/{id}/")
    suspend fun deleteRegularConfig(
        @Path("id") configId: Int
    ): Response<ApiResponse<Unit>>

    @GET("projects/{project_id}/csv-files/")
    suspend fun getCsvFiles(
        @Path("project_id") projectId: Int
    ): Response<ApiResponse<List<CsvFileResponse>>>

    @DELETE("csv-files/{id}/")
    suspend fun deleteCsvFile(
        @Path("id") fileId: Int
    ): Response<ApiResponse<Unit>>
}
