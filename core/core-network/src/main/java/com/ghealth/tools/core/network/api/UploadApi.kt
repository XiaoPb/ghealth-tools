package com.ghealth.tools.core.network.api

import com.ghealth.tools.core.network.model.ApiResponse
import com.ghealth.tools.core.network.model.CsvFileResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface UploadApi {
    @Multipart
    @POST("projects/{project_id}/csv-files/")
    suspend fun uploadCsvFile(
        @Path("project_id") projectId: Int,
        @Part csvFile: MultipartBody.Part,
        @Part("project") project: RequestBody? = null
    ): Response<ApiResponse<CsvFileResponse>>
}
