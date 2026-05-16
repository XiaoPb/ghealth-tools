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

    private fun getArrayLen(data: ByteArray): Result<Pair<Int, Int>> {
        if (data.size < 2) {
            return Result.failure(UnpackError.InsufficientData)
        }
        val arrayLen = data[1].toInt() and 0xFF
        val start = 2
        return Result.success(Pair(arrayLen, start))
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

        val (arrayLen, start) = getArrayLen(data).getOrThrow()
        val end = start + arrayLen * elementSize

        if (end > data.size) {
            val result = UByteArray(data.size - start) { i -> (data[start + i].toInt() and 0xFF).toUByte() }
            return Result.success(UnpackValue.U8Array(result))
        }

        val result = UByteArray(arrayLen) { i -> (data[start + i].toInt() and 0xFF).toUByte() }
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

        val (arrayLen, start) = getArrayLen(data).getOrThrow()
        val result = ByteArray(arrayLen) { i -> data[start + i] }
        return Result.success(UnpackValue.I8Array(result))
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

        val (arrayLen, start) = getArrayLen(data).getOrThrow()
        val result = UShortArray(arrayLen) { i ->
            val offset = start + i * 2
            if (offset + 2 > data.size) return Result.failure(UnpackError.InsufficientData)
            ((data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)).toUShort()
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

        val (arrayLen, start) = getArrayLen(data).getOrThrow()
        val result = ShortArray(arrayLen) { i ->
            val offset = start + i * 2
            if (offset + 2 > data.size) return Result.failure(UnpackError.InsufficientData)
            ((data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)).toShort()
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

        val (arrayLen, start) = getArrayLen(data).getOrThrow()
        val result = UIntArray(arrayLen) { i ->
            val offset = start + i * 4
            if (offset + 4 > data.size) return Result.failure(UnpackError.InsufficientData)
            ((data[offset].toLong() and 0xFF) or
                    ((data[offset + 1].toLong() and 0xFF) shl 8) or
                    ((data[offset + 2].toLong() and 0xFF) shl 16) or
                    ((data[offset + 3].toLong() and 0xFF) shl 24)).toUInt()
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

        val (arrayLen, start) = getArrayLen(data).getOrThrow()
        val result = IntArray(arrayLen) { i ->
            val offset = start + i * 4
            if (offset + 4 > data.size) return Result.failure(UnpackError.InsufficientData)
            ((data[offset].toLong() and 0xFF) or
                    ((data[offset + 1].toLong() and 0xFF) shl 8) or
                    ((data[offset + 2].toLong() and 0xFF) shl 16) or
                    ((data[offset + 3].toLong() and 0xFF) shl 24)).toInt()
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

        val (arrayLen, start) = getArrayLen(data).getOrThrow()
        val result = ULongArray(arrayLen) { i ->
            val offset = start + i * 8
            if (offset + 8 > data.size) return Result.failure(UnpackError.InsufficientData)
            ((data[offset].toLong() and 0xFF) or
                    ((data[offset + 1].toLong() and 0xFF) shl 8) or
                    ((data[offset + 2].toLong() and 0xFF) shl 16) or
                    ((data[offset + 3].toLong() and 0xFF) shl 24) or
                    ((data[offset + 4].toLong() and 0xFF) shl 32) or
                    ((data[offset + 5].toLong() and 0xFF) shl 40) or
                    ((data[offset + 6].toLong() and 0xFF) shl 48) or
                    ((data[offset + 7].toLong() and 0xFF) shl 56)).toULong()
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

        val (arrayLen, start) = getArrayLen(data).getOrThrow()
        val result = LongArray(arrayLen) { i ->
            val offset = start + i * 8
            if (offset + 8 > data.size) return Result.failure(UnpackError.InsufficientData)
            ((data[offset].toLong() and 0xFF) or
                    ((data[offset + 1].toLong() and 0xFF) shl 8) or
                    ((data[offset + 2].toLong() and 0xFF) shl 16) or
                    ((data[offset + 3].toLong() and 0xFF) shl 24) or
                    ((data[offset + 4].toLong() and 0xFF) shl 32) or
                    ((data[offset + 5].toLong() and 0xFF) shl 40) or
                    ((data[offset + 6].toLong() and 0xFF) shl 48) or
                    ((data[offset + 7].toLong() and 0xFF) shl 56))
        }
        return Result.success(UnpackValue.I64Array(result))
    }
}

fun unpack(data: ByteArray, format: String): Result<UnpackValue> {
    return DataUnpacker().unpack(data, format)
}

fun unpackU8Array(data: ByteArray): UByteArray {
    return when (val result = unpack(data, "<u8*>")) {
        is Result.Success -> (result.value as? UnpackValue.U8Array)?.value ?: UByteArray(0)
        else -> UByteArray(0)
    }
}

fun unpackU16Array(data: ByteArray): UShortArray {
    return when (val result = unpack(data, "<u16*>")) {
        is Result.Success -> (result.value as? UnpackValue.U16Array)?.value ?: UShortArray(0)
        else -> UShortArray(0)
    }
}

fun unpackU32Array(data: ByteArray): UIntArray {
    return when (val result = unpack(data, "<u32*>")) {
        is Result.Success -> (result.value as? UnpackValue.U32Array)?.value ?: UIntArray(0)
        else -> UIntArray(0)
    }
}

fun unpackU64Array(data: ByteArray): ULongArray {
    return when (val result = unpack(data, "<u64*>")) {
        is Result.Success -> (result.value as? UnpackValue.U64Array)?.value ?: ULongArray(0)
        else -> ULongArray(0)
    }
}

fun unpackI8Array(data: ByteArray): ByteArray {
    return when (val result = unpack(data, "<i8*>")) {
        is Result.Success -> (result.value as? UnpackValue.I8Array)?.value ?: ByteArray(0)
        else -> ByteArray(0)
    }
}

fun unpackI16Array(data: ByteArray): ShortArray {
    return when (val result = unpack(data, "<i16*>")) {
        is Result.Success -> (result.value as? UnpackValue.I16Array)?.value ?: ShortArray(0)
        else -> ShortArray(0)
    }
}

fun unpackI32Array(data: ByteArray): IntArray {
    return when (val result = unpack(data, "<i32*>")) {
        is Result.Success -> (result.value as? UnpackValue.I32Array)?.value ?: IntArray(0)
        else -> IntArray(0)
    }
}

fun unpackI64Array(data: ByteArray): LongArray {
    return when (val result = unpack(data, "<i64*>")) {
        is Result.Success -> (result.value as? UnpackValue.I64Array)?.value ?: LongArray(0)
        else -> LongArray(0)
    }
}

fun unpackString(data: ByteArray): String {
    return when (val result = unpack(data, "<s>")) {
        is Result.Success -> (result.value as? UnpackValue.StringValue)?.value ?: ""
        else -> ""
    }
}
