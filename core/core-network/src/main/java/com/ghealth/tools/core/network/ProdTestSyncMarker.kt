package com.ghealth.tools.core.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.File

@JsonClass(generateAdapter = true)
data class ProdTestSyncState(
    val configId: Int,
    val uploadedAt: String,
    val jsonFileName: String,
    val fileNames: List<String>
)

class ProdTestSyncMarker private constructor(
    private val markerFile: File,
    private val moshi: Moshi
) {
    companion object {
        const val MARKER_FILE_NAME = ".prod_test_sync.meta"

        fun forDir(targetDir: File): ProdTestSyncMarker {
            return ProdTestSyncMarker(
                markerFile = File(targetDir, MARKER_FILE_NAME),
                moshi = Moshi.Builder().build()
            )
        }
    }

    fun read(): ProdTestSyncState? {
        if (!markerFile.isFile) return null
        return try {
            val adapter = moshi.adapter(ProdTestSyncState::class.java)
            adapter.fromJson(markerFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun write(state: ProdTestSyncState) {
        markerFile.parentFile?.mkdirs()
        val adapter = moshi.adapter(ProdTestSyncState::class.java)
        markerFile.writeText(adapter.toJson(state))
    }

    fun upToDateState(configId: Int, uploadedAt: String): ProdTestSyncState? {
        val state = read() ?: return null
        if (state.configId != configId || state.uploadedAt != uploadedAt) return null
        val parent = markerFile.parentFile
        if (state.jsonFileName != File(state.jsonFileName).name) return null
        if (!File(parent, state.jsonFileName).isFile) return null
        if (state.fileNames.any { name ->
                name != File(name).name || !File(parent, name).isFile
            }
        ) return null
        return state
    }

    fun delete() {
        markerFile.delete()
    }
}
