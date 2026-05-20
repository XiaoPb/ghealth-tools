package com.ghealth.tools.ble.protocol.gh3300

const val MAX_CHANNELS_3300 = 32
const val MAX_CHANNELS_RAW_3300 = 128
const val MAX_GS_DATA_3300 = 6
const val MAX_ALGO_DATA_3300 = 16
const val MAX_FLAG_DATA_3300 = 8

data class Gh3300PackHeader(val bits: Int) {
    val rawdataEn: Boolean get() = (bits and (1 shl 0)) != 0
    val gsDataEn: Boolean get() = (bits and (1 shl 1)) != 0
    val flagsEn: Boolean get() = (bits and (1 shl 2)) != 0
    val algDataEn: Boolean get() = (bits and (1 shl 3)) != 0
    val agcInfoEn: Boolean get() = (bits and (1 shl 4)) != 0
    val frameIdEn: Boolean get() = (bits and (1 shl 5)) != 0
    val funcIdEn: Boolean get() = (bits and (1 shl 6)) != 0
}

enum class Gh3300FuncId(val id: Int, val label: String) {
    ADT(0, "ADT"),
    HR(1, "HR"),
    HRV(2, "HRV"),
    HSM(3, "HSM"),
    FPBP(4, "FPBP"),
    PWA(5, "PWA"),
    SPO2(6, "SpO2"),
    ECG(7, "ECG"),
    PWTT(8, "PWTT"),
    SOFT_ADT_GREEN(9, "SOFT_ADT_GREEN"),
    BT(10, "BT"),
    RESP(11, "RESP"),
    AF(12, "AF"),
    TEST1(13, "TEST1"),
    TEST2(14, "TEST2"),
    SOFT_ADT_IR(15, "SOFT_ADT_IR"),
    BIA(16, "BIA"),
    GSR(17, "GSR"),
    LEAD(18, "LEAD"),
    UNKNOWN(-1, "UNKNOWN");

    companion object {
        fun from(value: Int): Gh3300FuncId = entries.find { it.id == value } ?: UNKNOWN
    }
}
