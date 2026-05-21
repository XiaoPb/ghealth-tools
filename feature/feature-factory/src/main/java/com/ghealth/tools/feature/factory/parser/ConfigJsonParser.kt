package com.ghealth.tools.feature.factory.parser

import com.ghealth.tools.feature.factory.model.FactoryConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigJsonParser @Inject constructor() {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(FactoryConfig::class.java)

    fun parse(jsonString: String): FactoryConfig {
        val config = adapter.fromJson(jsonString)
            ?: throw IllegalArgumentException("Failed to parse factory config JSON")
        return config
    }

    fun parseOrNull(jsonString: String): FactoryConfig? =
        try {
            adapter.fromJson(jsonString)
        } catch (_: Exception) {
            null
        }
}
