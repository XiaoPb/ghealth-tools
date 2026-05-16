package com.ghealth.tools.ble.protocol.rpccore

private const val MAX_PARAM_NUMBER = 10
private const val MAX_SUPPORT_FORMAT_LENGTH = 50

private const val HEAD_DATA: Byte = 0
private const val HEAD_ARRAY: Byte = 1
private const val HEAD_ERROR: Byte = 2

enum class ProPackType(val value: Byte) {
    Pack(0),
    Unsigned(1),
    Signed(2),
    Double(3)
}

data class TypeHeader(
    var packType: Byte = 0,
    var isArray: Boolean = false,
    var width: Byte = 0,
    var end: Boolean = false,
    var split: Boolean = false
) {
    fun toByte(): Byte {
        var byte = (packType.toInt() and 0x03).toByte()
        if (isArray) {
            byte = (byte.toInt() or 0x04).toByte()
        }
        byte = (byte.toInt() or ((width.toInt() and 0x07) shl 3)).toByte()
        if (end) {
            byte = (byte.toInt() or 0x40).toByte()
        }
        if (split) {
            byte = (byte.toInt() or 0x80).toByte()
        }
        return byte
    }

    fun headType(): Byte {
        if (packType == ProPackType.Pack.value) {
            return HEAD_ERROR
        }
        return if (isArray) HEAD_ARRAY else HEAD_DATA
    }

    companion object {
        fun fromByte(byte: Byte): TypeHeader {
            return TypeHeader(
                packType = (byte.toInt() and 0x03).toByte(),
                isArray = (byte.toInt() and 0x04) != 0,
                width = ((byte.toInt() shr 3) and 0x07).toByte(),
                end = (byte.toInt() and 0x40) != 0,
                split = (byte.toInt() and 0x80) != 0
            )
        }
    }
}

data class FormatInfo(
    val headers: MutableList<TypeHeader> = mutableListOf(),
    var dataSize: Int = 0,
    var arrayNum: Int = 0
) {
    companion object {
        fun parse(format: String): Result<FormatInfo> {
            val info = FormatInfo()
            val formatBytes = format.toByteArray(Charsets.UTF_8)

            if (formatBytes.size > MAX_SUPPORT_FORMAT_LENGTH) {
                return Result.failure(ProtocolError.FormatError)
            }

            var i = 0
            while (i < formatBytes.size) {
                while (i < formatBytes.size && formatBytes[i] != '<'.code.toByte()) {
                    i++
                }
                if (i >= formatBytes.size) {
                    break
                }
                i++

                if (info.headers.size >= MAX_PARAM_NUMBER) {
                    return Result.failure(ProtocolError.ParamTooMuch)
                }

                val start = i
                while (i < formatBytes.size && formatBytes[i] != '>'.code.toByte()) {
                    i++
                }
                if (i >= formatBytes.size) {
                    return Result.failure(ProtocolError.FormatError)
                }

                val token = format.substring(start, i)
                val tokenBytes = token.toByteArray(Charsets.UTF_8)

                if (tokenBytes.size < 2) {
                    return Result.failure(ProtocolError.FormatError)
                }

                val packType: Byte = when (tokenBytes[0].toInt().toChar()) {
                    'u' -> ProPackType.Unsigned.value
                    'f' -> ProPackType.Double.value
                    'd', 'i' -> ProPackType.Signed.value
                    else -> return Result.failure(ProtocolError.FormatError)
                }

                val (isArray, widthStr) = if (tokenBytes[tokenBytes.size - 1] == '*'.code.toByte()) {
                    Pair(true, token.substring(1, token.length - 1))
                } else {
                    Pair(false, token.substring(1))
                }

                val widthBits: Int = widthStr.toIntOrNull()
                    ?: return Result.failure(ProtocolError.FormatError)
                val width = (kotlin.math.log2(widthBits.toDouble())).toInt().toByte()

                val header = TypeHeader(
                    packType = packType,
                    isArray = isArray,
                    width = width,
                    end = false,
                    split = false
                )

                if (!isArray) {
                    info.dataSize += (1 shl width.toInt()) / 8
                } else {
                    info.arrayNum++
                }

                info.headers.add(header)
                i++
            }

            if (info.headers.isNotEmpty()) {
                info.headers[info.headers.size - 1].end = true
            }

            return Result.success(info)
        }
    }
}

object Package {
    fun packU8(data: Byte): ByteArray = byteArrayOf(data)

    fun packU16(data: Short): ByteArray = byteArrayOf(
        (data.toInt() and 0xFF).toByte(),
        ((data.toInt() shr 8) and 0xFF).toByte()
    )

    fun packU32(data: Int): ByteArray = byteArrayOf(
        (data and 0xFF).toByte(),
        ((data shr 8) and 0xFF).toByte(),
        ((data shr 16) and 0xFF).toByte(),
        ((data shr 24) and 0xFF).toByte()
    )

    fun packU64(data: Long): ByteArray = byteArrayOf(
        (data and 0xFF).toByte(),
        ((data shr 8) and 0xFF).toByte(),
        ((data shr 16) and 0xFF).toByte(),
        ((data shr 24) and 0xFF).toByte(),
        ((data shr 32) and 0xFF).toByte(),
        ((data shr 40) and 0xFF).toByte(),
        ((data shr 48) and 0xFF).toByte(),
        ((data shr 56) and 0xFF).toByte()
    )

    fun packI8(data: Byte): ByteArray = byteArrayOf(data)

    fun packI16(data: Short): ByteArray = packU16(data)

    fun packI32(data: Int): ByteArray = packU32(data)

    fun packI64(data: Long): ByteArray = packU64(data)

    fun packF64(data: Double): ByteArray {
        val bits = java.lang.Double.doubleToLongBits(data)
        return packU64(bits)
    }

    fun packU8Array(data: ByteArray): ByteArray {
        val result = ByteArray(data.size + 2)
        result[0] = (data.size and 0xFF).toByte()
        result[1] = ((data.size shr 8) and 0xFF).toByte()
        data.copyInto(result, 2)
        return result
    }

    fun packU16Array(data: ShortArray): ByteArray {
        val result = ByteArray(data.size * 2 + 2)
        result[0] = (data.size and 0xFF).toByte()
        result[1] = ((data.size shr 8) and 0xFF).toByte()
        for (i in data.indices) {
            val offset = 2 + i * 2
            result[offset] = (data[i].toInt() and 0xFF).toByte()
            result[offset + 1] = ((data[i].toInt() shr 8) and 0xFF).toByte()
        }
        return result
    }

    fun packU32Array(data: IntArray): ByteArray {
        val result = ByteArray(data.size * 4 + 2)
        result[0] = (data.size and 0xFF).toByte()
        result[1] = ((data.size shr 8) and 0xFF).toByte()
        for (i in data.indices) {
            val offset = 2 + i * 4
            result[offset] = (data[i] and 0xFF).toByte()
            result[offset + 1] = ((data[i] shr 8) and 0xFF).toByte()
            result[offset + 2] = ((data[i] shr 16) and 0xFF).toByte()
            result[offset + 3] = ((data[i] shr 24) and 0xFF).toByte()
        }
        return result
    }

    fun packDataWithHeader(header: TypeHeader, data: ByteArray): ByteArray {
        val result = ByteArray(data.size + 1)
        result[0] = header.toByte()
        data.copyInto(result, 1)
        return result
    }

    fun packArrayWithHeader(header: TypeHeader, data: ByteArray, elementWidth: Int): ByteArray {
        val result = mutableListOf<Byte>()
        val totalElements = data.size / elementWidth
        var remaining = totalElements
        var offset = 0

        while (remaining > 0) {
            val chunkSize = minOf(remaining, 255)
            val isLast = remaining <= 255

            val chunkHeader = TypeHeader(
                packType = header.packType,
                isArray = true,
                width = header.width,
                end = header.end && isLast,
                split = !isLast
            )

            result.add(chunkHeader.toByte())
            result.add(chunkSize.toByte())

            val chunkBytes = chunkSize * elementWidth
            for (j in 0 until chunkBytes) {
                result.add(data[offset + j])
            }

            offset += chunkBytes
            remaining -= chunkSize
        }

        return result.toByteArray()
    }

    fun pack(format: String, values: ByteArray): Result<ByteArray> {
        val info = FormatInfo.parse(format).getOrThrow()
        val result = mutableListOf<Byte>()
        var valueOffset = 0

        for (header in info.headers) {
            val widthBits = (1 shl header.width.toInt())
            val widthBytes = widthBits / 8

            if (header.isArray) {
                if (valueOffset + 2 > values.size) {
                    return Result.failure(ProtocolError.UnpackageError)
                }
                val arrLen = ((values[valueOffset].toInt() and 0xFF) or
                        ((values[valueOffset + 1].toInt() and 0xFF) shl 8))
                valueOffset += 2

                val arrBytes = arrLen * widthBytes
                if (valueOffset + arrBytes > values.size) {
                    return Result.failure(ProtocolError.UnpackageError)
                }

                result.addAll(packArrayWithHeader(
                    header,
                    values.sliceArray(valueOffset until valueOffset + arrBytes),
                    widthBytes
                ).toList())
                valueOffset += arrBytes
            } else {
                if (valueOffset + widthBytes > values.size) {
                    return Result.failure(ProtocolError.UnpackageError)
                }
                result.add(header.toByte())
                for (j in 0 until widthBytes) {
                    result.add(values[valueOffset + j])
                }
                valueOffset += widthBytes
            }
        }

        return Result.success(result.toByteArray())
    }
}

object Unpackage {
    fun unpackU8(data: ByteArray): Result<Byte> {
        if (data.isEmpty()) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        return Result.success(data[0])
    }

    fun unpackU16(data: ByteArray): Result<Short> {
        if (data.size < 2) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        return Result.success(((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)).toShort())
    }

    fun unpackU32(data: ByteArray): Result<Int> {
        if (data.size < 4) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        return Result.success(
            (data[0].toInt() and 0xFF) or
                    ((data[1].toInt() and 0xFF) shl 8) or
                    ((data[2].toInt() and 0xFF) shl 16) or
                    ((data[3].toInt() and 0xFF) shl 24)
        )
    }

    fun unpackU64(data: ByteArray): Result<Long> {
        if (data.size < 8) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        return Result.success(
            (data[0].toLong() and 0xFF) or
                    ((data[1].toLong() and 0xFF) shl 8) or
                    ((data[2].toLong() and 0xFF) shl 16) or
                    ((data[3].toLong() and 0xFF) shl 24) or
                    ((data[4].toLong() and 0xFF) shl 32) or
                    ((data[5].toLong() and 0xFF) shl 40) or
                    ((data[6].toLong() and 0xFF) shl 48) or
                    ((data[7].toLong() and 0xFF) shl 56)
        )
    }

    fun unpackI8(data: ByteArray): Result<Byte> {
        if (data.isEmpty()) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        return Result.success(data[0])
    }

    fun unpackI16(data: ByteArray): Result<Short> {
        return unpackU16(data)
    }

    fun unpackI32(data: ByteArray): Result<Int> {
        return unpackU32(data)
    }

    fun unpackI64(data: ByteArray): Result<Long> {
        return unpackU64(data)
    }

    fun unpackF64(data: ByteArray): Result<Double> {
        val bits = unpackU64(data).getOrThrow()
        return Result.success(java.lang.Double.longBitsToDouble(bits))
    }

    fun unpackU8Array(data: ByteArray): Result<ByteArray> {
        if (data.size < 2) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        val len = ((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8))
        if (data.size < 2 + len) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        return Result.success(data.sliceArray(2 until 2 + len))
    }

    fun unpackU16Array(data: ByteArray): Result<ShortArray> {
        if (data.size < 2) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        val len = ((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8))
        if (data.size < 2 + len * 2) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        val result = ShortArray(len)
        for (i in 0 until len) {
            val offset = 2 + i * 2
            result[i] = ((data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)).toShort()
        }
        return Result.success(result)
    }

    fun unpackU32Array(data: ByteArray): Result<IntArray> {
        if (data.size < 2) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        val len = ((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8))
        if (data.size < 2 + len * 4) {
            return Result.failure(ProtocolError.UnpackageError)
        }
        val result = IntArray(len)
        for (i in 0 until len) {
            val offset = 2 + i * 4
            result[i] = (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    ((data[offset + 2].toInt() and 0xFF) shl 16) or
                    ((data[offset + 3].toInt() and 0xFF) shl 24)
        }
        return Result.success(result)
    }

    fun unpackWithFormat(data: ByteArray, format: String): Result<ByteArray> {
        val info = FormatInfo.parse(format).getOrThrow()
        val result = mutableListOf<Byte>()
        var offset = 0

        for (expectedHeader in info.headers) {
            if (offset >= data.size) {
                return Result.failure(ProtocolError.UnpackageError)
            }

            val actualHeader = TypeHeader.fromByte(data[offset])
            offset++

            if (actualHeader.packType != expectedHeader.packType ||
                actualHeader.isArray != expectedHeader.isArray ||
                actualHeader.width != expectedHeader.width
            ) {
                return Result.failure(ProtocolError.UnpackageError)
            }

            val widthBytes = (1 shl actualHeader.width.toInt())

            if (actualHeader.isArray) {
                var totalElements = 0
                val arrData = mutableListOf<Byte>()
                var currentHeader = actualHeader

                while (true) {
                    if (offset >= data.size) {
                        return Result.failure(ProtocolError.UnpackageError)
                    }
                    val chunkLen = data[offset].toInt() and 0xFF
                    offset++

                    val chunkBytes = chunkLen * widthBytes
                    if (offset + chunkBytes > data.size) {
                        return Result.failure(ProtocolError.UnpackageError)
                    }

                    for (j in 0 until chunkBytes) {
                        arrData.add(data[offset + j])
                    }
                    offset += chunkBytes
                    totalElements += chunkLen

                    if (!currentHeader.split) {
                        break
                    }

                    if (offset >= data.size) {
                        return Result.failure(ProtocolError.UnpackageError)
                    }
                    currentHeader = TypeHeader.fromByte(data[offset])
                    offset++
                }

                result.add((totalElements and 0xFF).toByte())
                result.add(((totalElements shr 8) and 0xFF).toByte())
                result.addAll(arrData)
            } else {
                if (offset + widthBytes > data.size) {
                    return Result.failure(ProtocolError.UnpackageError)
                }
                for (j in 0 until widthBytes) {
                    result.add(data[offset + j])
                }
                offset += widthBytes
            }
        }

        return Result.success(result.toByteArray())
    }
}
