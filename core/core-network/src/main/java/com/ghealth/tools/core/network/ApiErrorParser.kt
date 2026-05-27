package com.ghealth.tools.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiErrorParser @Inject constructor() {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun parseErrors(response: Response<*>): String {
        val errorBody = response.errorBody() ?: return "请求失败: ${response.code()}"
        val rawJson = errorBody.string()
        return try {
            val envelope = moshi.adapter(ErrorEnvelope::class.java).fromJson(rawJson)
            if (envelope != null) {
                val msg = envelope.message
                val fieldErrors = envelope.data?.flatMap { (field, messages) ->
                    messages.map { "$field: $it" }
                }?.joinToString("\n")
                
                if (!fieldErrors.isNullOrBlank()) fieldErrors else msg
            } else {
                "请求失败: ${response.code()}"
            }
        } catch (e: Exception) {
            "请求失败: ${response.code()}"
        }.also { errorBody.close() }
    }

    suspend fun parseErrorsString(response: ResponseBody): String {
        val rawJson = response.string()
        return try {
            val envelope = moshi.adapter(ErrorEnvelope::class.java).fromJson(rawJson)
            if (envelope != null) {
                val fieldErrors = envelope.data?.flatMap { (field, messages) ->
                    messages.map { "$field: $it" }
                }?.joinToString("\n")
                fieldErrors ?: envelope.message
            } else {
                "请求失败"
            }
        } catch (e: Exception) {
            "请求失败"
        }
    }
}

private data class ErrorEnvelope(
    val code: Int = 0,
    val message: String = "",
    val data: Map<String, List<String>>? = null
)