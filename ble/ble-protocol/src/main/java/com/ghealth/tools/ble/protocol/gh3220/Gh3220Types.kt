package com.ghealth.tools.ble.protocol.gh3220

/**
 * GH3220 (GH3x2x) G-protocol type definitions.
 *
 * Based on STGh3x2xFrameInfo from gh_drv.h and STRawdataPackHeader from fifo_analyse.h.
 * Wire format uses varint+zigzag encoding with delta compression on rawdata.
 *
 * C reference: .claude/gh_protocol/c/gh3220/gh3220_data_package.h
 */

const val MAX_CHANNELS_3220 = 32
const val MAX_CHANNELS_RAW_3220 = 128
const val MAX_GS_DATA_3220 = 6       // 3 acc + optionally 3 gyro
const val MAX_ALGO_DATA_3220 = 32
const val MAX_FLAG_DATA_3220 = 8
const val MAX_AGC_INFO_3220 = 32

/**
 * Pack header for the GH3220 G-protocol wire format.
 *
 * Matches gh3220_pack_header_t in gh3220_data_package.h:
 *   bit 0: rawdata_en    - raw sensor data per channel
 *   bit 1: gs_data_en    - accelerometer/gyro data
 *   bit 2: flags_en      - frame flags
 *   bit 3: algo_res_en   - algorithm results
 *   bit 4: agc_info_en   - AGC info per channel
 *   bit 5: func_id_en    - function ID
 *   bit 6: frame_id_en   - frame counter
 */
data class Gh3220PackHeader(val bits: Int) {
    val rawdataEn: Boolean get() = (bits and (1 shl 0)) != 0
    val gsDataEn: Boolean get() = (bits and (1 shl 1)) != 0
    val flagsEn: Boolean get() = (bits and (1 shl 2)) != 0
    val algoResEn: Boolean get() = (bits and (1 shl 3)) != 0
    val agcInfoEn: Boolean get() = (bits and (1 shl 4)) != 0
    val funcIdEn: Boolean get() = (bits and (1 shl 5)) != 0
    val frameIdEn: Boolean get() = (bits and (1 shl 6)) != 0
}

/**
 * GH3220 function IDs as defined in gh_drv.h (GH3X2X_FUNC_OFFSET_*).
 *
 *  0=ADT, 1=HR, 2=HRV, 3=HSM, 4=FPBP, 5=PWA,
 *  6=SPO2, 7=ECG, 8=PWTT, 9=SOFT_ADT_GREEN,
 *  10=BT, 11=RESP, 12=AF, 13=TEST1, 14=TEST2,
 *  15=SOFT_ADT_IR, 16=RS0, 17=RS1, 18=RS2, 19=LEAD_DET
 */
enum class Gh3220FuncId(val id: Int, val label: String) {
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
    RS0(16, "RS0"),
    RS1(17, "RS1"),
    RS2(18, "RS2"),
    LEAD_DET(19, "LEAD_DET"),
    UNKNOWN(-1, "UNKNOWN");

    companion object {
        fun from(value: Int): Gh3220FuncId = entries.find { it.id == value } ?: UNKNOWN
    }
}
