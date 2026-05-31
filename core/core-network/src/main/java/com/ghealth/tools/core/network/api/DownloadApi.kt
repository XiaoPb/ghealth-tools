package com.ghealth.tools.core.network.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

interface DownloadApi {
    @Streaming
    @GET("prod-test-config/{id}/download/")
    suspend fun downloadProdTestConfig(
        @Path("id") configId: Int
    ): Response<ResponseBody>

    @Streaming
    @GET("prod-test-config/{id}/download/{field}/")
    suspend fun downloadProdTestConfigFile(
        @Path("id") configId: Int,
        @Path("field") field: String
    ): Response<ResponseBody>

    @Streaming
    @GET("regular-configs/{id}/download/")
    suspend fun downloadRegularConfig(
        @Path("id") configId: Int
    ): Response<ResponseBody>

    @Streaming
    @GET("csv-files/{id}/download/")
    suspend fun downloadCsvFile(
        @Path("id") fileId: Int
    ): Response<ResponseBody>
}