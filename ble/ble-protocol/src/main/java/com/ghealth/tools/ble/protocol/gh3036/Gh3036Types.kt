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

enum class GhFuncId(val id: Int, val label: String) {
    ADT(0, "ADT"),
    HR(1, "HR"),
    SPO2(2, "SpO2"),
    HRV(3, "HRV"),
    NADT_GREEN(4, "NADT-GREEN"),
    NADT_IR(5, "NADT-IR"),
    TEST1(6, "TEST1"),
    TEST2(7, "TEST2"),
    EVK(8, "EVK"),
    ECG(9, "ECG"),
    GSR(10, "GSR"),
    BIA(11, "BIA"),
    HSM(12, "HSM"),
    FPBP(13, "FPBP"),
    PWA(14, "PWA"),
    PWTT(15, "PWTT"),
    BT(16, "BT"),
    RESP(17, "RESP"),
    AF(18, "AF"),
    LEAD(19, "LEAD"),
    UNKNOWN(-1, "UNKNOWN");

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
    var gyro: IntArray = IntArray(0),
    var flags: IntArray = IntArray(0),
    var algoData: IntArray = IntArray(0),
    var agcInfo: IntArray = IntArray(0),
    var agcInfoHigh: IntArray = IntArray(0),
    var slotCfg: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GhFuncFrame
        if (funcId != other.funcId) return false
        if (frameCnt != other.frameCnt) return false
        if (timestamp != other.timestamp) return false
        if (!rawdata.contentEquals(other.rawdata)) return false
        if (!phyValue.contentEquals(other.phyValue)) return false
        if (!gsData.contentEquals(other.gsData)) return false
        if (!gyro.contentEquals(other.gyro)) return false
        if (!flags.contentEquals(other.flags)) return false
        if (!algoData.contentEquals(other.algoData)) return false
        if (!agcInfo.contentEquals(other.agcInfo)) return false
        if (!agcInfoHigh.contentEquals(other.agcInfoHigh)) return false
        if (slotCfg != other.slotCfg) return false
        return true
    }

    override fun hashCode(): Int {
        var result = funcId.hashCode()
        result = 31 * result + frameCnt
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + rawdata.contentHashCode()
        result = 31 * result + phyValue.contentHashCode()
        result = 31 * result + gsData.contentHashCode()
        result = 31 * result + gyro.contentHashCode()
        result = 31 * result + flags.contentHashCode()
        result = 31 * result + algoData.contentHashCode()
        result = 31 * result + agcInfo.contentHashCode()
        result = 31 * result + agcInfoHigh.contentHashCode()
        result = 31 * result + slotCfg
        return result
    }
}
