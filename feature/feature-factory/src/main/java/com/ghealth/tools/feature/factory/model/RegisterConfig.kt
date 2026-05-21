package com.ghealth.tools.feature.factory.model

data class RegisterConfig(
    val registers: List<RegEntry>
)

data class RegEntry(
    val addr: Int,
    val value: Int
) {
    companion object {
        fun toInterleavedArray(entries: List<RegEntry>): IntArray =
            IntArray(entries.size * 2).also { arr ->
                entries.forEachIndexed { i, entry ->
                    arr[i * 2] = entry.addr
                    arr[i * 2 + 1] = entry.value
                }
            }
    }
}
