package com.ghealth.tools.ble.protocol.gh3036

object RegisterCommandPayloadBuilder {
    fun buildU16ArrayPayload(values: IntArray): ByteArray {
        val result = ByteArray(values.size * 2 + 2)
        result[0] = (values.size and 0xFF).toByte()
        result[1] = ((values.size shr 8) and 0xFF).toByte()
        for (i in values.indices) {
            val offset = 2 + i * 2
            result[offset] = (values[i] and 0xFF).toByte()
            result[offset + 1] = ((values[i] shr 8) and 0xFF).toByte()
        }
        return result
    }
}
