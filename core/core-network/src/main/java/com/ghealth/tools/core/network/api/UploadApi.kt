package com.ghealth.tools.core.network.api

import com.ghealth.tools.core.network.model.ApiResponse
import com.ghealth.tools.core.network.model.CsvFileResponse
import com.ghealth.tools.core.network.model.ProductionTestConfigResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface UploadApi {
    companion object {
        const val MAX_UPLOAD_SIZE = 50L * 1024 * 1024
        const val MIN_UPLOAD_SIZE = 500L * 1024
    }

    @Multipart
    @POST("projects/{project_id}/csv-files/")
    suspend fun uploadCsvFile(
        @Path("project_id") projectId: Int,
        @Part file: MultipartBody.Part,
        @Part("filename") filename: RequestBody
    ): Response<ApiResponse<CsvFileResponse>>

    @Multipart
    @POST("projects/{project_id}/prod-test-config/")
    suspend fun uploadProdTestConfig(
        @Path("project_id") projectId: Int,
        @Part json_config: MultipartBody.Part? = null,
        @Part base_noise_config: MultipartBody.Part? = null,
        @Part lpctr_config: MultipartBody.Part? = null,
        @Part lplctr_config: MultipartBody.Part? = null,
        @Part ppg_noise_config: MultipartBody.Part? = null,
        @Part hardware_version: RequestBody? = null,
        @Part test_frequency: RequestBody? = null
    ): Response<ApiResponse<ProductionTestConfigResponse>>
}