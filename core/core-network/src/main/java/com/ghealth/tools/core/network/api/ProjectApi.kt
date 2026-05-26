package com.ghealth.tools.core.network.api

import com.ghealth.tools.core.network.model.ApiResponse
import com.ghealth.tools.core.network.model.ProductionTestConfigResponse
import com.ghealth.tools.core.network.model.ProjectResponse
import com.ghealth.tools.core.network.model.RegularConfigResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ProjectApi {
    @GET("projects/")
    suspend fun getProjects(): Response<ApiResponse<List<ProjectResponse>>>

    @GET("projects/{id}/")
    suspend fun getProject(
        @Path("id") projectId: Int
    ): Response<ApiResponse<ProjectResponse>>

    @GET("projects/{project_id}/prod-test-config/")
    suspend fun getProductionTestConfig(
        @Path("project_id") projectId: Int
    ): Response<ApiResponse<ProductionTestConfigResponse>>

    @GET("projects/{project_id}/regular-configs/")
    suspend fun getRegularConfigs(
        @Path("project_id") projectId: Int
    ): Response<ApiResponse<List<RegularConfigResponse>>>
}
