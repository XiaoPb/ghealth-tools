package com.ghealth.tools.ble.protocol.gh3036

sealed class DecodeError : Exception() {
    object InsufficientData : DecodeError()
    object InvalidFormat : DecodeError()
    object InvalidChannelCount : DecodeError()
    object CrcMismatch : DecodeError()
}

data class GhAgcInfo(
    var gainCode: Byte = 0,
    var bgCancelRange: Byte = 0,
    var dcCancelRange: Byte = 0,
    var dcCancelCode: Byte = 0,
    var ledDrv0: Byte = 0,
    var ledDrv1: Byte = 0,
    var bgCancelCode: Byte = 0,
    var tiaGain: Byte = 0
) {
    companion object {
        fun fromBytes(data: ByteArray): Result<Pair<GhAgcInfo, Byte>> {
            if (data.size < 8) {
                return Result.failure(DecodeError.InsufficientData)
            }

            val word0 = ((data[0].toLong() and 0xFF) or
                    ((data[1].toLong() and 0xFF) shl 8) or
                    ((data[2].toLong() and 0xFF) shl 16) or
                    ((data[3].toLong() and 0xFF) shl 24)).toInt()

            val word1 = ((data[4].toLong() and 0xFF) or
                    ((data[5].toLong() and 0xFF) shl 8) or
                    ((data[6].toLong() and 0xFF) shl 16) or
                    ((data[7].toLong() and 0xFF) shl 24)).toInt()

            val ledDrvFs = ((word0 shr 16) and 0xFF).toByte()

            val info = GhAgcInfo(
                gainCode = (word0 and 0x0F).toByte(),
                bgCancelRange = ((word0 shr 4) and 0x03).toByte(),
                dcCancelRange = ((word0 shr 6) and 0x03).toByte(),
                dcCancelCode = ((word0 shr 8) and 0xFF).toByte(),
                ledDrv0 = ((word0 shr 24) and 0xFF).toByte(),
                ledDrv1 = (word1 and 0xFF).toByte(),
                bgCancelCode = 0,
                tiaGain = 0
            )

            return Result.success(Pair(info, ledDrvFs))
        }
    }

    fun toBytes(ledDrvFs: Byte): ByteArray {
        val word0 = (gainCode.toLong() and 0xFF) or
                ((bgCancelRange.toLong() and 0xFF) shl 4) or
                ((dcCancelRange.toLong() and 0xFF) shl 6) or
                ((dcCancelCode.toLong() and 0xFF) shl 8) or
                ((ledDrvFs.toLong() and 0xFF) shl 16) or
                ((ledDrv0.toLong() and 0xFF) shl 24)

        val word1 = (ledDrv1.toLong() and 0xFF)

        return byteArrayOf(
            (word0 and 0xFF).toByte(),
            ((word0 shr 8) and 0xFF).toByte(),
            ((word0 shr 16) and 0xFF).toByte(),
            ((word0 shr 24) and 0xFF).toByte(),
            (word1 and 0xFF).toByte(),
            ((word1 shr 8) and 0xFF).toByte(),
            ((word1 shr 16) and 0xFF).toByte(),
            ((word1 shr 24) and 0xFF).toByte()
        )
    }
}

data class GhFrameDataFlag(
    var ledAdjFlag: Boolean = false,
    var saFlag: Boolean = false,
    var paramChangeFlag: Boolean = false,
    var dreUpdate: Boolean = false,
    var skipOkFlag: Boolean = false
) {
    companion object {
        fun fromByte(byte: Byte): GhFrameDataFlag {
            return GhFrameDataFlag(
                ledAdjFlag = (byte.toInt() and 0x01) != 0,
                saFlag = ((byte.toInt() shr 1) and 0x01) != 0,
                paramChangeFlag = ((byte.toInt() shr 2) and 0x01) != 0,
                dreUpdate = ((byte.toInt() shr 3) and 0x01) != 0,
                skipOkFlag = ((byte.toInt() shr 4) and 0x01) != 0
            )
        }
    }

    fun toByte(): Byte {
        var result: Byte = 0
        if (ledAdjFlag) result = (result.toInt() or 0x01).toByte()
        if (saFlag) result = (result.toInt() or 0x02).toByte()
        if (paramChangeFlag) result = (result.toInt() or 0x04).toByte()
        if (dreUpdate) result = (result.toInt() or 0x08).toByte()
        if (skipOkFlag) result = (result.toInt() or 0x10).toByte()
        return result
    }
}

data class GhFrameData(
    var ipdPa: Int = 0,
    var rawdata: Int = 0,
    var flag: GhFrameDataFlag = GhFrameDataFlag(),
    var agcInfo: GhAgcInfo = GhAgcInfo()
) {
    companion object {
        fun fromBytes(data: ByteArray): Result<GhFrameData> {
            if (data.size < 17) {
                return Result.failure(DecodeError.InsufficientData)
            }

            val ipdPa = ((data[0].toLong() and 0xFF) or
                    ((data[1].toLong() and 0xFF) shl 8) or
                    ((data[2].toLong() and 0xFF) shl 16) or
                    ((data[3].toLong() and 0xFF) shl 24)).toInt()

            val rawdata = ((data[4].toLong() and 0xFF) or
                    ((data[5].toLong() and 0xFF) shl 8) or
                    ((data[6].toLong() and 0xFF) shl 16) or
                    ((data[7].toLong() and 0xFF) shl 24)).toInt()

            val flag = GhFrameDataFlag.fromByte(data[8])
            val (agcInfo, _) = GhAgcInfo.fromBytes(data.sliceArray(9 until 17)).getOrThrow()

            return Result.success(GhFrameData(
                ipdPa = ipdPa,
                rawdata = rawdata,
                flag = flag,
                agcInfo = agcInfo
            ))
        }
    }

    fun toBytes(ledDrvFs: Byte): ByteArray {
        val result = mutableListOf<Byte>()
        result.add((ipdPa and 0xFF).toByte())
        result.add(((ipdPa shr 8) and 0xFF).toByte())
        result.add(((ipdPa shr 16) and 0xFF).toByte())
        result.add(((ipdPa shr 24) and 0xFF).toByte())
        result.add((rawdata and 0xFF).toByte())
        result.add(((rawdata shr 8) and 0xFF).toByte())
        result.add(((rawdata shr 16) and 0xFF).toByte())
        result.add(((rawdata shr 24) and 0xFF).toByte())
        result.add(flag.toByte())
        result.addAll(agcInfo.toBytes(ledDrvFs).toList())
        return result.toByteArray()
    }
}

enum class GhFuncFixIdx(val value: Byte) {
    Adt(0),
    Hr(1),
    Spo2(2),
    Hrv(3),
    Gnadt(4),
    Irnadt(5),
    AlgoMax(6),
    Test2(7),
    PpgCfg0(8),
    PpgCfg1(9),
    PpgCfg2(10),
    PpgCfg3(11),
    PpgCfg4(12),
    PpgCfg5(13),
    PpgCfg6(14),
    PpgCfg7(15),
    CapCfg(16),
    Max(17);

    companion object {
        fun fromByte(value: Byte): GhFuncFixIdx {
            return entries.find { it.value == value } ?: Max
        }
    }
}

val GH_FUNC_FIX_IDX_TEST1 = GhFuncFixIdx.AlgoMax

data class GhGsensorData(
    var acc: ShortArray = shortArrayOf(0, 0, 0)
) {
    companion object {
        fun fromBytes(data: ByteArray): Result<GhGsensorData> {
            if (data.size < 6) {
                return Result.failure(DecodeError.InsufficientData)
            }

            val acc = shortArrayOf(
                ((data[0].toLong() and 0xFF) or ((data[1].toLong() and 0xFF) shl 8)).toShort(),
                ((data[2].toLong() and 0xFF) or ((data[3].toLong() and 0xFF) shl 8)).toShort(),
                ((data[4].toLong() and 0xFF) or ((data[5].toLong() and 0xFF) shl 8)).toShort()
            )

            return Result.success(GhGsensorData(acc = acc))
        }
    }

    fun toBytes(): ByteArray {
        return byteArrayOf(
            (acc[0].toInt() and 0xFF).toByte(),
            ((acc[0].toInt() shr 8) and 0xFF).toByte(),
            (acc[1].toInt() and 0xFF).toByte(),
            ((acc[1].toInt() shr 8) and 0xFF).toByte(),
            (acc[2].toInt() and 0xFF).toByte(),
            ((acc[2].toInt() shr 8) and 0xFF).toByte()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GhGsensorData
        return acc.contentEquals(other.acc)
    }

    override fun hashCode(): Int {
        return acc.contentHashCode()
    }
}

data class GhFuncFrame(
    var frameCnt: Int = 0,
    var timestamp: Long = 0,
    var gsensorData: GhGsensorData = GhGsensorData(),
    var id: GhFuncFixIdx = GhFuncFixIdx.Adt,
    var chNum: Byte = 0,
    var chMax: Byte = 0,
    var gsensorEn: Byte = 0,
    var fifoEndFlag: Byte = 0,
    var ledDrvFs: ByteArray = ByteArray(2),
    var data: List<GhFrameData> = emptyList()
) {
    companion object {
        fun fromBytes(data: ByteArray): Result<GhFuncFrame> {
            if (data.size < 32) {
                return Result.failure(DecodeError.InsufficientData)
            }

            val frameCnt = ((data[0].toLong() and 0xFF) or
                    ((data[1].toLong() and 0xFF) shl 8) or
                    ((data[2].toLong() and 0xFF) shl 16) or
                    ((data[3].toLong() and 0xFF) shl 24)).toInt()

            val timestamp = ((data[4].toLong() and 0xFF) or
                    ((data[5].toLong() and 0xFF) shl 8) or
                    ((data[6].toLong() and 0xFF) shl 16) or
                    ((data[7].toLong() and 0xFF) shl 24) or
                    ((data[8].toLong() and 0xFF) shl 32) or
                    ((data[9].toLong() and 0xFF) shl 40) or
                    ((data[10].toLong() and 0xFF) shl 48) or
                    ((data[11].toLong() and 0xFF) shl 56))

            val gsensorData = GhGsensorData.fromBytes(data.sliceArray(12 until 18)).getOrThrow()

            val id = GhFuncFixIdx.fromByte(data[18])
            val chNum = data[19]
            val chMax = data[20]
            val gsensorEn = data[21]
            val fifoEndFlag = data[22]
            val ledDrvFs = byteArrayOf(data[23], data[24])

            val headerSize = 25
            val frameDataSize = 17
            val frames = mutableListOf<GhFrameData>()

            var offset = headerSize
            while (offset + frameDataSize <= data.size) {
                val frameData = GhFrameData.fromBytes(data.sliceArray(offset until offset + frameDataSize))
                if (frameData.isSuccess) {
                    frames.add(frameData.getOrThrow())
                }
                offset += frameDataSize
            }

            return Result.success(GhFuncFrame(
                frameCnt = frameCnt,
                timestamp = timestamp,
                gsensorData = gsensorData,
                id = id,
                chNum = chNum,
                chMax = chMax,
                gsensorEn = gsensorEn,
                fifoEndFlag = fifoEndFlag,
                ledDrvFs = ledDrvFs,
                data = frames
            ))
        }
    }

    fun toBytes(): ByteArray {
        val result = mutableListOf<Byte>()

        result.add((frameCnt and 0xFF).toByte())
        result.add(((frameCnt shr 8) and 0xFF).toByte())
        result.add(((frameCnt shr 16) and 0xFF).toByte())
        result.add(((frameCnt shr 24) and 0xFF).toByte())

        for (i in 0 until 8) {
            result.add(((timestamp shr (i * 8)) and 0xFF).toByte())
        }

        result.addAll(gsensorData.toBytes().toList())
        result.add(id.value)
        result.add(chNum)
        result.add(chMax)
        result.add(gsensorEn)
        result.add(fifoEndFlag)
        result.addAll(ledDrvFs.toList())

        for (frame in data) {
            result.addAll(frame.toBytes(ledDrvFs[0]).toList())
        }

        return result.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GhFuncFrame
        return frameCnt == other.frameCnt &&
                timestamp == other.timestamp &&
                gsensorData == other.gsensorData &&
                id == other.id &&
                chNum == other.chNum &&
                chMax == other.chMax &&
                gsensorEn == other.gsensorEn &&
                fifoEndFlag == other.fifoEndFlag &&
                ledDrvFs.contentEquals(other.ledDrvFs) &&
                data == other.data
    }

    override fun hashCode(): Int {
        var result1 = frameCnt
        result1 = 31 * result1 + timestamp.hashCode()
        result1 = 31 * result1 + gsensorData.hashCode()
        result1 = 31 * result1 + id.hashCode()
        result1 = 31 * result1 + chNum
        result1 = 31 * result1 + chMax
        result1 = 31 * result1 + gsensorEn
        result1 = 31 * result1 + fifoEndFlag
        result1 = 31 * result1 + ledDrvFs.contentHashCode()
        result1 = 31 * result1 + data.hashCode()
        return result1
    }
}
