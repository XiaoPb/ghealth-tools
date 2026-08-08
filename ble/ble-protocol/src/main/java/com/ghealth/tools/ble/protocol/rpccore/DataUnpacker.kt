package com.ghealth.tools.ble.protocol.rpccore

sealed class UnpackError : Exception() {
    object InsufficientData : UnpackError()
    object InvalidHeader : UnpackError()
    object InvalidFormat : UnpackError()
    object UnsupportedType : UnpackError()
}

sealed class UnpackValue {
    data class U8(val value: kotlin.UByte) : UnpackValue()
    data class I8(val value: Byte) : UnpackValue()
    data class U16(val value: kotlin.UShort) : UnpackValue()
    data class I16(val value: Short) : UnpackValue()
    data class U32(val value: kotlin.UInt) : UnpackValue()
    data class I32(val value: Int) : UnpackValue()
    data class U64(val value: kotlin.ULong) : UnpackValue()
    data class I64(val value: Long) : UnpackValue()
    data class U8Array(val value: kotlin.UByteArray) : UnpackValue()
    data class I8Array(val value: ByteArray) : UnpackValue()
    data class U16Array(val value: kotlin.UShortArray) : UnpackValue()
    data class I16Array(val value: ShortArray) : UnpackValue()
    data class U32Array(val value: kotlin.UIntArray) : UnpackValue()
    data class I32Array(val value: IntArray) : UnpackValue()
    data class U64Array(val value: kotlin.ULongArray) : UnpackValue()
    data class I64Array(val value: LongArray) : UnpackValue()
    data class StringValue(val value: String) : UnpackValue()
}

class DataUnpacker {
    private fun getElementSize(header: Byte): Int {
        val width = (header.toInt() shr 3) and 0x07
        return (1 shl width) / 8
    }

    private fun isArray(header: Byte): Boolean {
        return (header.toInt() and 0x04) != 0
    }

    private fun parseFormat(format: String): Pair<String, Boolean>? {
        val trimmed = format.trim()
        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) {
            return null
        }
        val inner = trimmed.substring(1, trimmed.length - 1)
        val isArray = inner.endsWith("*")
        val typeName = if (isArray) {
            inner.substring(0, inner.length - 1)
        } else {
            inner
        }
        return Pair(typeName.lowercase(), isArray)
    }

    fun unpack(data: ByteArray, format: String): Result<UnpackValue> {
        if (data.isEmpty()) {
            return Result.failure(UnpackError.InsufficientData)
        }

        val (typeName, isFormatArray) = parseFormat(format) ?: return Result.failure(UnpackError.InvalidFormat)

        val header = data[0]
        val isDataArray = isArray(header)
        val elementSize = getElementSize(header)
        val isArray = isDataArray || isFormatArray

        return when (typeName) {
            "u8" -> if (isArray) unpackU8ArrayInternal(data, elementSize) else unpackU8Internal(data, elementSize)
            "i8", "d8" -> if (isArray) unpackI8ArrayInternal(data, elementSize) else unpackI8Internal(data, elementSize)
            "u16" -> if (isArray) unpackU16ArrayInternal(data, elementSize) else unpackU16Internal(data, elementSize)
            "i16", "d16" -> if (isArray) unpackI16ArrayInternal(data, elementSize) else unpackI16Internal(data, elementSize)
            "u32" -> if (isArray) unpackU32ArrayInternal(data, elementSize) else unpackU32Internal(data, elementSize)
            "i32", "d32" -> if (isArray) unpackI32ArrayInternal(data, elementSize) else unpackI32Internal(data, elementSize)
            "u64" -> if (isArray) unpackU64ArrayInternal(data, elementSize) else unpackU64Internal(data, elementSize)
            "i64", "d64" -> if (isArray) unpackI64ArrayInternal(data, elementSize) else unpackI64Internal(data, elementSize)
            "s", "string" -> {
                val result = unpackU8ArrayInternal(data, elementSize)
                result.mapCatching { value ->
                    if (value is UnpackValue.U8Array) {
                        val str = value.value.toByteArray().decodeToString().trim('\u0000')
                        UnpackValue.StringValue(str)
                    } else {
                        throw UnpackError.InvalidFormat
                    }
                }
            }
            else -> Result.failure(UnpackError.UnsupportedType)
        }
    }

    /**
     * 对齐 C 端 gh_package.c 的 unpackArray：数组元素超过 255 个时，发送方会拆成多个
     * [header(split=1)][len][data] 分块，最后一块 split=0。这里循环累加所有分块并拼接，
     * 后续分块头与首块头比较时忽略 split 位（对应 C compareWithoutFlag）。
     */
    private fun unpackArrayData(data: ByteArray, elementSize: Int): Result<ByteArray> {
        if (data.size < 2) {
            return Result.failure(UnpackError.InsufficientData)
        }
        var pos = 0
        var firstHeader = 0
        var first = true
        val output = java.io.ByteArrayOutputStream()
        while (true) {
            if (pos >= data.size) {
                return Result.failure(UnpackError.InsufficientData)
            }
            val header = data[pos].toInt() and 0xFF
            if (first) {
                firstHeader = header
                first = false
            } else if ((header or 0x80) != (firstHeader or 0x80)) {
                return Result.failure(UnpackError.InvalidHeader)
            }
            if (!isArray(header.toByte())) {
                return Result.failure(UnpackError.InvalidHeader)
            }
            pos++
            if (pos >= data.size) {
                return Result.failure(UnpackError.InsufficientData)
            }
            val length = data[pos].toInt() and 0xFF
            pos++
            val byteCount = length * elementSize
            if (byteCount > data.size - pos) {
                return Result.failure(UnpackError.InsufficientData)
            }
            output.write(data, pos, byteCount)
            pos += byteCount
            if ((header and 0x80) == 0) {
                break
            }
        }
        return Result.success(output.toByteArray())
    }

    private fun unpackU8Internal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        if (data.size < 1 + elementSize) {
            return Result.failure(UnpackError.InsufficientData)
        }
        return Result.success(UnpackValue.U8((data[1].toInt() and 0xFF).toUByte()))
    }

    private fun unpackU8ArrayInternal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        val header = data[0]
        if (!isArray(header)) {
            if (data.size < 1 + elementSize) {
                return Result.failure(UnpackError.InsufficientData)
            }
            return Result.success(UnpackValue.U8Array(ubyteArrayOf((data[1].toInt() and 0xFF).toUByte())))
        }

        val bytes = unpackArrayData(data, elementSize).getOrElse { return Result.failure(it) }
        val result = UByteArray(bytes.size) { i -> (bytes[i].toInt() and 0xFF).toUByte() }
        return Result.success(UnpackValue.U8Array(result))
    }

    private fun unpackI8Internal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        if (data.size < 1 + elementSize) {
            return Result.failure(UnpackError.InsufficientData)
        }
        return Result.success(UnpackValue.I8(data[1]))
    }

    private fun unpackI8ArrayInternal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        val header = data[0]
        if (!isArray(header)) {
            if (data.size < 1 + elementSize) {
                return Result.failure(UnpackError.InsufficientData)
            }
            return Result.success(UnpackValue.I8Array(byteArrayOf(data[1])))
        }

        val bytes = unpackArrayData(data, elementSize).getOrElse { return Result.failure(it) }
        return Result.success(UnpackValue.I8Array(bytes))
    }

    private fun unpackU16Internal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        if (data.size < 1 + elementSize) {
            return Result.failure(UnpackError.InsufficientData)
        }
        val value = ((data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)).toUShort()
        return Result.success(UnpackValue.U16(value))
    }

    private fun unpackU16ArrayInternal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        val header = data[0]
        if (!isArray(header)) {
            if (data.size < 1 + elementSize) {
                return Result.failure(UnpackError.InsufficientData)
            }
            val value = ((data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)).toUShort()
            return Result.success(UnpackValue.U16Array(ushortArrayOf(value)))
        }

        val bytes = unpackArrayData(data, elementSize).getOrElse { return Result.failure(it) }
        val result = UShortArray(bytes.size / 2) { i ->
            ((bytes[i * 2].toInt() and 0xFF) or ((bytes[i * 2 + 1].toInt() and 0xFF) shl 8)).toUShort()
        }
        return Result.success(UnpackValue.U16Array(result))
    }

    private fun unpackI16Internal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        if (data.size < 1 + elementSize) {
            return Result.failure(UnpackError.InsufficientData)
        }
        val value = ((data[1].toInt() and 0xFF) or (data[2].toInt() shl 8)).toShort()
        return Result.success(UnpackValue.I16(value))
    }

    private fun unpackI16ArrayInternal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        val header = data[0]
        if (!isArray(header)) {
            if (data.size < 1 + elementSize) {
                return Result.failure(UnpackError.InsufficientData)
            }
            val value = ((data[1].toInt() and 0xFF) or (data[2].toInt() shl 8)).toShort()
            return Result.success(UnpackValue.I16Array(shortArrayOf(value)))
        }

        val bytes = unpackArrayData(data, elementSize).getOrElse { return Result.failure(it) }
        val result = ShortArray(bytes.size / 2) { i ->
            ((bytes[i * 2].toInt() and 0xFF) or ((bytes[i * 2 + 1].toInt() and 0xFF) shl 8)).toShort()
        }
        return Result.success(UnpackValue.I16Array(result))
    }

    private fun unpackU32Internal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        if (data.size < 1 + elementSize) {
            return Result.failure(UnpackError.InsufficientData)
        }
        val value = ((data[1].toLong() and 0xFF) or
                ((data[2].toLong() and 0xFF) shl 8) or
                ((data[3].toLong() and 0xFF) shl 16) or
                ((data[4].toLong() and 0xFF) shl 24)).toUInt()
        return Result.success(UnpackValue.U32(value))
    }

    private fun unpackU32ArrayInternal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        val header = data[0]
        if (!isArray(header)) {
            if (data.size < 1 + elementSize) {
                return Result.failure(UnpackError.InsufficientData)
            }
            val value = ((data[1].toLong() and 0xFF) or
                    ((data[2].toLong() and 0xFF) shl 8) or
                    ((data[3].toLong() and 0xFF) shl 16) or
                    ((data[4].toLong() and 0xFF) shl 24)).toUInt()
            return Result.success(UnpackValue.U32Array(uintArrayOf(value)))
        }

        val bytes = unpackArrayData(data, elementSize).getOrElse { return Result.failure(it) }
        val result = UIntArray(bytes.size / 4) { i ->
            val offset = i * 4
            ((bytes[offset].toLong() and 0xFF) or
                    ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toLong() and 0xFF) shl 24)).toUInt()
        }
        return Result.success(UnpackValue.U32Array(result))
    }

    private fun unpackI32Internal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        if (data.size < 1 + elementSize) {
            return Result.failure(UnpackError.InsufficientData)
        }
        val value = ((data[1].toLong() and 0xFF) or
                ((data[2].toLong() and 0xFF) shl 8) or
                ((data[3].toLong() and 0xFF) shl 16) or
                ((data[4].toLong() and 0xFF) shl 24)).toInt()
        return Result.success(UnpackValue.I32(value))
    }

    private fun unpackI32ArrayInternal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        val header = data[0]
        if (!isArray(header)) {
            if (data.size < 1 + elementSize) {
                return Result.failure(UnpackError.InsufficientData)
            }
            val value = ((data[1].toLong() and 0xFF) or
                    ((data[2].toLong() and 0xFF) shl 8) or
                    ((data[3].toLong() and 0xFF) shl 16) or
                    ((data[4].toLong() and 0xFF) shl 24)).toInt()
            return Result.success(UnpackValue.I32Array(intArrayOf(value)))
        }

        val bytes = unpackArrayData(data, elementSize).getOrElse { return Result.failure(it) }
        val result = IntArray(bytes.size / 4) { i ->
            val offset = i * 4
            ((bytes[offset].toLong() and 0xFF) or
                    ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toLong() and 0xFF) shl 24)).toInt()
        }
        return Result.success(UnpackValue.I32Array(result))
    }

    private fun unpackU64Internal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        if (data.size < 1 + elementSize) {
            return Result.failure(UnpackError.InsufficientData)
        }
        val value = ((data[1].toLong() and 0xFF) or
                ((data[2].toLong() and 0xFF) shl 8) or
                ((data[3].toLong() and 0xFF) shl 16) or
                ((data[4].toLong() and 0xFF) shl 24) or
                ((data[5].toLong() and 0xFF) shl 32) or
                ((data[6].toLong() and 0xFF) shl 40) or
                ((data[7].toLong() and 0xFF) shl 48) or
                ((data[8].toLong() and 0xFF) shl 56)).toULong()
        return Result.success(UnpackValue.U64(value))
    }

    private fun unpackU64ArrayInternal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        val header = data[0]
        if (!isArray(header)) {
            if (data.size < 1 + elementSize) {
                return Result.failure(UnpackError.InsufficientData)
            }
            val value = ((data[1].toLong() and 0xFF) or
                    ((data[2].toLong() and 0xFF) shl 8) or
                    ((data[3].toLong() and 0xFF) shl 16) or
                    ((data[4].toLong() and 0xFF) shl 24) or
                    ((data[5].toLong() and 0xFF) shl 32) or
                    ((data[6].toLong() and 0xFF) shl 40) or
                    ((data[7].toLong() and 0xFF) shl 48) or
                    ((data[8].toLong() and 0xFF) shl 56)).toULong()
            return Result.success(UnpackValue.U64Array(ulongArrayOf(value)))
        }

        val bytes = unpackArrayData(data, elementSize).getOrElse { return Result.failure(it) }
        val result = ULongArray(bytes.size / 8) { i ->
            val offset = i * 8
            ((bytes[offset].toLong() and 0xFF) or
                    ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toLong() and 0xFF) shl 24) or
                    ((bytes[offset + 4].toLong() and 0xFF) shl 32) or
                    ((bytes[offset + 5].toLong() and 0xFF) shl 40) or
                    ((bytes[offset + 6].toLong() and 0xFF) shl 48) or
                    ((bytes[offset + 7].toLong() and 0xFF) shl 56)).toULong()
        }
        return Result.success(UnpackValue.U64Array(result))
    }

    private fun unpackI64Internal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        if (data.size < 1 + elementSize) {
            return Result.failure(UnpackError.InsufficientData)
        }
        val value = ((data[1].toLong() and 0xFF) or
                ((data[2].toLong() and 0xFF) shl 8) or
                ((data[3].toLong() and 0xFF) shl 16) or
                ((data[4].toLong() and 0xFF) shl 24) or
                ((data[5].toLong() and 0xFF) shl 32) or
                ((data[6].toLong() and 0xFF) shl 40) or
                ((data[7].toLong() and 0xFF) shl 48) or
                ((data[8].toLong() and 0xFF) shl 56))
        return Result.success(UnpackValue.I64(value))
    }

    private fun unpackI64ArrayInternal(data: ByteArray, elementSize: Int): Result<UnpackValue> {
        val header = data[0]
        if (!isArray(header)) {
            if (data.size < 1 + elementSize) {
                return Result.failure(UnpackError.InsufficientData)
            }
            val value = ((data[1].toLong() and 0xFF) or
                    ((data[2].toLong() and 0xFF) shl 8) or
                    ((data[3].toLong() and 0xFF) shl 16) or
                    ((data[4].toLong() and 0xFF) shl 24) or
                    ((data[5].toLong() and 0xFF) shl 32) or
                    ((data[6].toLong() and 0xFF) shl 40) or
                    ((data[7].toLong() and 0xFF) shl 48) or
                    ((data[8].toLong() and 0xFF) shl 56))
            return Result.success(UnpackValue.I64Array(longArrayOf(value)))
        }

        val bytes = unpackArrayData(data, elementSize).getOrElse { return Result.failure(it) }
        val result = LongArray(bytes.size / 8) { i ->
            val offset = i * 8
            ((bytes[offset].toLong() and 0xFF) or
                    ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                    ((bytes[offset + 3].toLong() and 0xFF) shl 24) or
                    ((bytes[offset + 4].toLong() and 0xFF) shl 32) or
                    ((bytes[offset + 5].toLong() and 0xFF) shl 40) or
                    ((bytes[offset + 6].toLong() and 0xFF) shl 48) or
                    ((bytes[offset + 7].toLong() and 0xFF) shl 56))
        }
        return Result.success(UnpackValue.I64Array(result))
    }
}

fun unpack(data: ByteArray, format: String): Result<UnpackValue> {
    return DataUnpacker().unpack(data, format)
}

fun unpackU8Array(data: ByteArray): UByteArray {
    val result = unpack(data, "<u8*>").getOrNull()
    return (result as? UnpackValue.U8Array)?.value ?: UByteArray(0)
}

fun unpackU16Array(data: ByteArray): UShortArray {
    val result = unpack(data, "<u16*>").getOrNull()
    return (result as? UnpackValue.U16Array)?.value ?: UShortArray(0)
}

fun unpackU32Array(data: ByteArray): UIntArray {
    val result = unpack(data, "<u32*>").getOrNull()
    return (result as? UnpackValue.U32Array)?.value ?: UIntArray(0)
}

fun unpackU64Array(data: ByteArray): ULongArray {
    val result = unpack(data, "<u64*>").getOrNull()
    return (result as? UnpackValue.U64Array)?.value ?: ULongArray(0)
}

fun unpackI8Array(data: ByteArray): ByteArray {
    val result = unpack(data, "<i8*>").getOrNull()
    return (result as? UnpackValue.I8Array)?.value ?: ByteArray(0)
}

fun unpackI16Array(data: ByteArray): ShortArray {
    val result = unpack(data, "<i16*>").getOrNull()
    return (result as? UnpackValue.I16Array)?.value ?: ShortArray(0)
}

fun unpackI32Array(data: ByteArray): IntArray {
    val result = unpack(data, "<i32*>").getOrNull()
    return (result as? UnpackValue.I32Array)?.value ?: IntArray(0)
}

fun unpackI64Array(data: ByteArray): LongArray {
    val result = unpack(data, "<i64*>").getOrNull()
    return (result as? UnpackValue.I64Array)?.value ?: LongArray(0)
}

fun unpackString(data: ByteArray): String {
    val result = unpack(data, "<s>").getOrNull()
    return (result as? UnpackValue.StringValue)?.value ?: ""
}
