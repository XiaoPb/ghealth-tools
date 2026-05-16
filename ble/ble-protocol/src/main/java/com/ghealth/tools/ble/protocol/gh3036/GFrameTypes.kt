package com.ghealth.tools.ble.protocol.gh3036

const val MAX_CHANNELS = 32
const val MAX_GS_DATA = 6
const val MAX_ALGO_DATA = 32

data class PackHeader(val bits: Int) {
    val rawdataEn: Boolean get() = (bits and (1 shl 0)) != 0
    val phyValueEn: Boolean get() = (bits and (1 shl 1)) != 0
    val gsDataEn: Boolean get() = (bits and (1 shl 2)) != 0
    val flagsEn: Boolean get() = (bits and (1 shl 3)) != 0
    val algDataEn: Boolean get() = (bits and (1 shl 4)) != 0
    val agcInfoEn: Boolean get() = (bits and (1 shl 5)) != 0
    val timestampEn: Boolean get() = (bits and (1 shl 6)) != 0
    val frameIdEn: Boolean get() = (bits and (1 shl 7)) != 0
    val funcIdEn: Boolean get() = (bits and (1 shl 8)) != 0
    val slotCfgEn: Boolean get() = (bits and (1 shl 9)) != 0
}

enum class GhFuncId(val id: Int) {
    ADT(0), HR(1), SPO2(2), HRV(3),
    NADT_GREEN(4), NADT_IR(5),
    TEST1(6), TEST2(7), EVK(8),
    ECG(9), GSR(10), BIA(11),
    UNKNOWN(-1);

    companion object {
        fun from(value: Int): GhFuncId = entries.find { it.id == value } ?: UNKNOWN
    }
}

data class GhFuncFrame(
    var funcId: GhFuncId = GhFuncId.UNKNOWN,
    var frameCnt: Int = 0,
    var timestamp: Long = 0L,
    var rawdata: IntArray = IntArray(0),
    var phyValue: IntArray = IntArray(0),
    var gsData: IntArray = IntArray(0),
    var flags: IntArray = IntArray(0),
    var algoData: IntArray = IntArray(0),
    var agcInfo: IntArray = IntArray(0),
    var agcInfoHigh: IntArray = IntArray(0),
    var slotCfg: Int = 0
)
